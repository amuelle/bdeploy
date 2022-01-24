package io.bdeploy.minion.remote.jersey;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.endpoints.CommonEndpointHelper;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.NodeProcessResource;
import io.bdeploy.interfaces.remote.NodeProxyResource;
import io.bdeploy.interfaces.remote.ProxiedRequestWrapper;
import io.bdeploy.interfaces.remote.ProxiedResponseWrapper;
import io.bdeploy.interfaces.variables.ApplicationParameterValueResolver;
import io.bdeploy.interfaces.variables.ApplicationVariableResolver;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathResolver;
import io.bdeploy.minion.MinionRoot;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

public class NodeProxyResourceImpl implements NodeProxyResource {

    private static final Logger log = LoggerFactory.getLogger(NodeProxyResourceImpl.class);

    @Inject
    private MinionRoot root;

    @Context
    private ResourceContext rc;

    @Override
    public ProxiedResponseWrapper forward(ProxiedRequestWrapper wrapper) {
        NodeProcessResource spr = rc.initResource(new NodeProcessResourceImpl());
        InstanceNodeStatusDto ins = spr.getStatus(wrapper.instanceId);
        ProcessStatusDto ps = ins.getStatus(wrapper.applicationId);

        if (!ps.processState.isRunning()) {
            throw new WebApplicationException(
                    "Process with ID " + wrapper.applicationId + " is not running and ready for instance " + wrapper.instanceId,
                    Status.PRECONDITION_FAILED);
        }

        InstanceNodeManifest inm = findInstanceNodeManifest(wrapper.instanceId, ps.instanceTag);
        if (inm == null) {
            throw new WebApplicationException("Cannot find instance " + wrapper.instanceId, Status.NOT_FOUND);
        }

        DeploymentPathProvider dpp = new DeploymentPathProvider(root.getDeploymentDir().resolve(inm.getUUID()),
                inm.getKey().getTag());

        ApplicationConfiguration app = inm.getConfiguration().applications.stream()
                .filter(a -> a.uid.equals(wrapper.applicationId)).findFirst().orElseThrow();

        CompositeResolver list = new CompositeResolver();
        list.add(new DeploymentPathResolver(dpp));
        list.add(new ApplicationVariableResolver(app));
        list.add(new ApplicationParameterValueResolver(app.uid, inm.getConfiguration()));

        HttpEndpoint processedEndpoint = CommonEndpointHelper.processEndpoint(list, wrapper.endpoint);

        try {
            byte[] body = wrapper.base64body == null ? null : Base64.decodeBase64(wrapper.base64body);
            WebTarget target = CommonEndpointHelper.initClient(processedEndpoint);

            for (Map.Entry<String, List<String>> entry : wrapper.queryParameters.entrySet()) {
                target = target.queryParam(entry.getKey(), entry.getValue().toArray());
            }

            Invocation.Builder request = target.request();

            // Always replace the "host" header with "localhost". the request is *always* made on the local
            // machine. Avoid forwarding the original host (e.g. the hostname of the original BDeploy server).
            // Otherwise a potential SNI check will fail on the target due to hostname mismatch with certificates.
            if (wrapper.headers.containsKey("host")) {
                wrapper.headers.put("host", Collections.singletonList("localhost"));
            }

            for (Map.Entry<String, List<String>> entry : wrapper.headers.entrySet()) {
                for (String value : entry.getValue()) {
                    request.header(entry.getKey(), value);
                }
            }

            if (body != null) {
                return wrap(request.build(wrapper.method, Entity.entity(body, wrapper.bodyType)).invoke());
            } else {
                return wrap(request.build(wrapper.method).invoke());
            }
        } catch (Exception e) {
            throw new WebApplicationException("Failed to call endpoint " + wrapper.endpoint.id + " on target application "
                    + wrapper.applicationId + " for instance " + wrapper.instanceId, e);
        }
    }

    private ProxiedResponseWrapper wrap(Response resp) {
        ProxiedResponseWrapper wrapper = new ProxiedResponseWrapper();

        wrapper.headers = resp.getStringHeaders();
        wrapper.responseCode = resp.getStatus();
        wrapper.responseReason = resp.getStatusInfo().getReasonPhrase();

        if (resp.hasEntity()) {
            try (InputStream is = resp.readEntity(InputStream.class)) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    StreamHelper.copy(is, baos);
                    wrapper.base64body = Base64.encodeBase64String(baos.toByteArray());
                }
            } catch (IOException e) {
                log.warn("Cannot wrap response", e);
            }
        }

        return wrapper;
    }

    private InstanceNodeManifest findInstanceNodeManifest(String instanceId, String tag) {
        SortedSet<Key> manifests = InstanceNodeManifest.scan(root.getHive());
        for (Key key : manifests) {
            if (!key.getTag().equals(tag)) {
                continue;
            }
            InstanceNodeManifest mf = InstanceNodeManifest.of(root.getHive(), key);
            if (!mf.getUUID().equals(instanceId)) {
                continue;
            }
            return mf;
        }
        return null;
    }

}
