package org.ostelco.topup.api.resources;

import org.ostelco.topup.api.core.Profile;
import org.ostelco.topup.api.core.Error;
import org.ostelco.topup.api.db.SubscriberDAO;

import io.vavr.control.Option;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.NonNull;

/**
 * Sign up API.
 *
 */
@AllArgsConstructor
@Path("/register")
public class SignUpResource extends ResourceHelpers {

    @NonNull
    private final SubscriberDAO dao;

    @POST
    @Consumes({"application/json"})
    @Produces({"application/json"})
    public Response signUp(@NotNull final Profile profile) {

        Option<Error> error = dao.signUp(profile);

        return error.isEmpty()
            ? Response.status(Response.Status.CREATED)
                 .build()
            : Response.status(Response.Status.FORBIDDEN)
                 .entity(getErrorAsJson(error.get()))
                 .build();
    }
}