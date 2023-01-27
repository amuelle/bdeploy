package io.bdeploy.jersey;

import java.util.Map;
import java.util.TreeMap;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.container.ContainerRequestContext;

/**
 * Provides a wrapper around the {@link ContainerRequestContext} where per-request data is stored.
 * <p>
 * In case a new Thread is started *within* a request, the request association is lost if not handled explicitly, thus we
 * need to fall back to a simple thread local solution.
 */
@Service
public class JerseyRequestContext {

    private static final Logger log = LoggerFactory.getLogger(JerseyRequestContext.class);

    @Inject
    private Provider<ContainerRequestContext> reqCtx;

    private final ThreadLocal<Map<String, Object>> fallback = ThreadLocal.withInitial(() -> new TreeMap<>());

    public Object getProperty(String name) {
        try {
            return this.reqCtx.get().getProperty(name);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to get property {}", name, e);
            }
            return fallback.get().get(name);
        }
    }

    public void setProperty(String name, Object object) {
        try {
            this.reqCtx.get().setProperty(name, object);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to set property {}", name, e);
            }
            fallback.get().put(name, object);
        }
    }

    public void removeProperty(String name) {
        try {
            reqCtx.get().removeProperty(name);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to remove property {}", name, e);
            }
            fallback.get().remove(name);
        }
    }
}