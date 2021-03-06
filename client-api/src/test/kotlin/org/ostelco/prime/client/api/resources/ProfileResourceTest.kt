package org.ostelco.prime.client.api.resources

import arrow.core.Either
import com.nhaarman.mockito_kotlin.argumentCaptor
import io.dropwizard.auth.AuthDynamicFeature
import io.dropwizard.auth.AuthValueFactoryProvider
import io.dropwizard.auth.oauth.OAuthCredentialAuthFilter
import io.dropwizard.testing.junit.ResourceTestRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.ostelco.prime.auth.AccessTokenPrincipal
import org.ostelco.prime.auth.OAuthAuthenticator
import org.ostelco.prime.client.api.store.SubscriberDAO
import org.ostelco.prime.client.api.util.AccessToken
import org.ostelco.prime.jsonmapper.objectMapper
import org.ostelco.prime.model.Subscriber
import java.util.*
import javax.ws.rs.client.Entity
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * Profile API tests.
 *
 */
class ProfileResourceTest {

    private val email = "boaty@internet.org"
    private val name = "Boaty McBoatface"
    private val address = "Storvej 10"
    private val postCode = "132 23"
    private val city = "Oslo"

    private val profile = Subscriber(email)

    @Before
    fun setUp() {
        `when`(AUTHENTICATOR.authenticate(ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(AccessTokenPrincipal(email)))
    }

    @Test
    fun getProfile() {
        val arg = argumentCaptor<String>()

        `when`(DAO.getProfile(arg.capture())).thenReturn(Either.right(profile))

        val resp = RULE.target("/profile")
                .request()
                .header("Authorization", "Bearer ${AccessToken.withEmail(email)}")
                .get(Response::class.java)

        assertThat(resp.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(resp.mediaType.toString()).isEqualTo(MediaType.APPLICATION_JSON)

        assertThat(resp.readEntity(Subscriber::class.java)).isEqualTo(profile)
        assertThat(arg.firstValue).isEqualTo(email)
    }

    @Test
    fun createProfile() {
        val arg1 = argumentCaptor<String>()
        val arg2 = argumentCaptor<Subscriber>()
        val arg3 = argumentCaptor<String>()


        `when`(DAO.createProfile(arg1.capture(), arg2.capture(), arg3.capture()))
                .thenReturn(Either.right(profile))

        val resp = RULE.target("/profile")
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${AccessToken.withEmail(email)}")
                .post(Entity.json("""{
                        "name": "$name",
                        "address": "$address",
                        "postCode": "$postCode",
                        "city": "$city",
                        "email": "$email"
                }""".trimIndent()))

        assertThat(resp.status).isEqualTo(Response.Status.CREATED.statusCode)
        assertThat(resp.mediaType.toString()).isEqualTo(MediaType.APPLICATION_JSON)
        assertThat(arg1.firstValue).isEqualTo(email)
        assertThat(arg2.firstValue.email).isEqualTo(email)
        assertThat(arg2.firstValue.name).isEqualTo(name)
        assertThat(arg2.firstValue.address).isEqualTo(address)
        assertThat(arg2.firstValue.postCode).isEqualTo(postCode)
        assertThat(arg2.firstValue.city).isEqualTo(city)
        assertThat(arg3.firstValue).isNull()
    }

    @Test
    fun createProfileWithReferral() {
        val arg1 = argumentCaptor<String>()
        val arg2 = argumentCaptor<Subscriber>()
        val arg3 = argumentCaptor<String>()

        val referredBy = "foo@bar.com"

        `when`(DAO.createProfile(arg1.capture(), arg2.capture(), arg3.capture()))
                .thenReturn(Either.right(profile))

        val resp = RULE.target("/profile")
                .queryParam("referred_by", referredBy)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${AccessToken.withEmail(email)}")
                .post(Entity.json("""{
                    "name": "$name",
                    "address": "$address",
                    "postCode": "$postCode",
                    "city": "$city",
                    "email": "$email"
                }""".trimIndent()))

        assertThat(resp.status).isEqualTo(Response.Status.CREATED.statusCode)
        assertThat(resp.mediaType.toString()).isEqualTo(MediaType.APPLICATION_JSON)
        assertThat(arg1.firstValue).isEqualTo(email)
        assertThat(arg2.firstValue.email).isEqualTo(email)
        assertThat(arg2.firstValue.name).isEqualTo(name)
        assertThat(arg2.firstValue.address).isEqualTo(address)
        assertThat(arg2.firstValue.postCode).isEqualTo(postCode)
        assertThat(arg2.firstValue.city).isEqualTo(city)
        assertThat(arg3.firstValue).isEqualTo(referredBy)
    }

    @Test
    fun updateProfile() {
        val arg1 = argumentCaptor<String>()
        val arg2 = argumentCaptor<Subscriber>()

        val newAddress = "Storvej 10"
        val newPostCode = "132 23"

        `when`(DAO.updateProfile(arg1.capture(), arg2.capture()))
                .thenReturn(Either.right(profile))

        val resp = RULE.target("/profile")
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${AccessToken.withEmail(email)}")
                .put(Entity.json("""{
                    "name": "$name",
                    "address": "$newAddress",
                    "postCode": "$newPostCode",
                    "city": "$city",
                    "email": "$email"
                }""".trimIndent()))

        assertThat(resp.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(resp.mediaType.toString()).isEqualTo(MediaType.APPLICATION_JSON)
        assertThat(arg1.firstValue).isEqualTo(email)
        assertThat(arg2.firstValue.email).isEqualTo(email)
        assertThat(arg2.firstValue.name).isEqualTo(name)
        assertThat(arg2.firstValue.address).isEqualTo(newAddress)
        assertThat(arg2.firstValue.postCode).isEqualTo(newPostCode)
        assertThat(arg2.firstValue.city).isEqualTo(city)
    }

    @Test
    fun updateWithIncompleteProfile() {
        val resp = RULE.target("/profile")
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${AccessToken.withEmail(email)}")
                .put(Entity.json("""{ "name": "$name" }"""))

        assertThat(resp.status).isEqualTo(Response.Status.BAD_REQUEST.statusCode)
    }

    companion object {

        val DAO = mock(SubscriberDAO::class.java)
        val AUTHENTICATOR = mock(OAuthAuthenticator::class.java)

        @JvmField
        @ClassRule
        val RULE = ResourceTestRule.builder()
                .setMapper(objectMapper)
                .addResource(AuthDynamicFeature(
                        OAuthCredentialAuthFilter.Builder<AccessTokenPrincipal>()
                                .setAuthenticator(AUTHENTICATOR)
                                .setPrefix("Bearer")
                                .buildAuthFilter()))
                .addResource(AuthValueFactoryProvider.Binder(AccessTokenPrincipal::class.java))
                .addResource(ProfileResource(DAO))
                .build()
    }
}
