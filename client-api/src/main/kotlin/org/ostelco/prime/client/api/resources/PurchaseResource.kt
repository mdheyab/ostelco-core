package org.ostelco.prime.client.api.resources

import io.dropwizard.auth.Auth
import org.ostelco.prime.client.api.auth.AccessTokenPrincipal
import org.ostelco.prime.client.api.store.SubscriberDAO
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

/**
 * Purchase API.
 *
 */
@Path("/purchases")
class PurchaseResource(private val dao: SubscriberDAO) {

    @GET
    @Produces("application/json")
    fun getPurchases(@Auth token: AccessTokenPrincipal?): Response {
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .build()
        }

        val result = dao.getPurchaseHistory(token.name)

        return if (result.isRight) {
            Response.status(Response.Status.OK)
                    .entity(asJson(result.right().get()))
                    .build()
        } else {
            Response.status(Response.Status.NOT_FOUND)
                    .entity(asJson(result.left().get()))
                    .build()
        }
    }
}