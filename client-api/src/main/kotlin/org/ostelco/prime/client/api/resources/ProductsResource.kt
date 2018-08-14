package org.ostelco.prime.client.api.resources

import arrow.core.Either
import arrow.core.flatMap
import io.dropwizard.auth.Auth
import org.ostelco.prime.client.api.auth.AccessTokenPrincipal
import org.ostelco.prime.client.api.store.SubscriberDAO
import org.ostelco.prime.core.ApiError
import org.ostelco.prime.module.getResource
import org.ostelco.prime.paymentprocessor.PaymentProcessor
import org.ostelco.prime.paymentprocessor.core.ProductInfo
import org.ostelco.prime.paymentprocessor.core.ProfileInfo
import javax.validation.constraints.NotNull
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.BAD_GATEWAY
import javax.ws.rs.core.Response.Status.CREATED

/**
 * Products API.
 *
 */
@Path("/products")
class ProductsResource(private val dao: SubscriberDAO) {

    private val paymentProcessor by lazy { getResource<PaymentProcessor>() }

    @GET
    @Produces("application/json")
    fun getProducts(@Auth token: AccessTokenPrincipal?): Response {
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .build()
        }

        return dao.getProducts(token.name).fold(
                { Response.status(Response.Status.NOT_FOUND).entity(asJson(it)) },
                { Response.status(Response.Status.OK).entity(asJson(it)) })
                .build()
    }

    @Deprecated("use purchaseProduct")
    @POST
    @Path("{sku}")
    @Produces("application/json")
    fun purchaseProductOld(@Auth token: AccessTokenPrincipal?,
                           @NotNull
                           @PathParam("sku")
                           sku: String,
                           @QueryParam("sourceId")
                           sourceId: String?,
                           @QueryParam("saveCard")
                           saveCard: Boolean = false): Response = purchaseProduct(token, sku, sourceId, saveCard)

    @POST
    @Path("{sku}/purchase")
    @Produces("application/json")
    fun purchaseProduct(@Auth token: AccessTokenPrincipal?,
                        @NotNull
                        @PathParam("sku")
                        sku: String,
                        @QueryParam("sourceId")
                        sourceId: String?,
                        @QueryParam("saveCard")
                        saveCard: Boolean = false): Response {    /* 'false' is default. */
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .build()
        }
        return dao.purchaseProduct(token.name, sku, sourceId, saveCard)
                .fold(
                        { (status, apiError )-> Response.status(status).entity(asJson(apiError)) },
                        { productInfo -> Response.status(CREATED).entity(productInfo)}
                ).build()

    }
}
