package io.bdeploy.ui.api;

import java.io.InputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.ui.dto.ConfigFileDto;
import io.bdeploy.ui.dto.InstanceUsageDto;
import io.bdeploy.ui.dto.ProductDto;

@Path("/product")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ProductResource {

    /**
     * List all products, optionally filtering for a certain name.
     *
     * @param name the name to filter for or null to list all products in the instance group.
     * @return a sorted list of products. Names are sorted lexically, versions are sorted descending (newest version first).
     */
    @GET
    @Path("/list")
    public List<ProductDto> list(@QueryParam("name") String name);

    @GET
    @Path("/count")
    public Long count();

    @DELETE
    @Path("/{name : .+}/{tag}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void delete(@PathParam("name") String name, @PathParam("tag") String tag);

    @Path("/{name : .+}/{tag}/application")
    public ApplicationResource getApplicationResource(@PathParam("name") String name, @PathParam("tag") String tag);

    @GET
    @Path("/{name : .+}/diskUsage")
    public String getProductDiskUsage(@PathParam("name") String name);

    @GET
    @Path("/{name : .+}/{tag}/useCount")
    public Long getProductUseCount(@PathParam("name") String name, @PathParam("tag") String tag);

    @GET
    @Path("/{name : .+}/{tag}/usedIn")
    public List<InstanceUsageDto> getProductUsedIn(@PathParam("name") String name, @PathParam("tag") String tag);

    @GET
    @Path("/{name : .+}/{tag}/zip")
    public String createProductZipFile(@PathParam("name") String name, @PathParam("tag") String tag);

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredPermission(permission = Permission.WRITE)
    public List<Manifest.Key> upload(@FormDataParam("file") InputStream inputStream);

    @GET
    @Path("/copy")
    @RequiredPermission(permission = Permission.WRITE)
    public void copyProduct(@QueryParam("repo") String softwareRepository, @QueryParam("name") String productName,
            @QueryParam("tag") String productTag);

    @GET
    @Path("/{name : .+}/{tag}/listConfig")
    public List<ConfigFileDto> listConfigFiles(@PathParam("name") String name, @PathParam("tag") String tag);

    @GET
    @Path("/{name : .+}/{tag}/config/{file: .+}")
    public String loadConfigFile(@PathParam("name") String name, @PathParam("tag") String tag, @PathParam("file") String file);

}
