package org.ostelco.prime.client.api.resources

import io.dropwizard.auth.Auth
import org.ostelco.prime.auth.AccessTokenPrincipal
import org.ostelco.prime.client.api.store.SubscriberDAO
import org.ostelco.prime.getLogger
import org.ostelco.prime.jsonmapper.asJson
import javax.validation.constraints.NotNull
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * Payment API.
 *
 */
@Path("/paymentSources")
class PaymentResource(private val dao: SubscriberDAO) {

    private val logger by getLogger()

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    fun createSource(@Auth token: AccessTokenPrincipal?,
                     @NotNull
                     @QueryParam("sourceId")
                     sourceId: String): Response {
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .build()
        }

        return dao.createSource(token.name, sourceId)
                .fold(
                        { apiError -> Response.status(apiError.status).entity(asJson(apiError)) },
                        { sourceInfo -> Response.status(Response.Status.CREATED).entity(sourceInfo)}
                ).build()
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun listSources(@Auth token: AccessTokenPrincipal?): Response {
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .build()
        }
        return dao.listSources(token.name)
                .fold(
                        { apiError -> Response.status(apiError.status).entity(asJson(apiError)) },
                        { sourceList -> Response.status(Response.Status.OK).entity(sourceList)}
                ).build()
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    fun setDefaultSource(@Auth token: AccessTokenPrincipal?,
                         @NotNull
                         @QueryParam("sourceId")
                         sourceId: String): Response {
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .build()
        }

        return dao.setDefaultSource(token.name, sourceId)
                .fold(
                        { apiError -> Response.status(apiError.status).entity(asJson(apiError)) },
                        { sourceInfo -> Response.status(Response.Status.OK).entity(sourceInfo)}
                ).build()
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    fun removeSource(@Auth token: AccessTokenPrincipal?,
                     @NotNull
                     @QueryParam("sourceId")
                     sourceId: String): Response {
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .build()
        }

        return dao.removeSource(token.name, sourceId)
                .fold(
                        { apiError -> Response.status(apiError.status).entity(asJson(apiError)) },
                        { sourceInfo -> Response.status(Response.Status.OK).entity(sourceInfo)}
                ).build()
    }
}
