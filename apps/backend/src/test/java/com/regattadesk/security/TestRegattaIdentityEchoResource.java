package com.regattadesk.security;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Test-only endpoints for validating IdentityHeaderSanitizer trust-boundary behavior.
 */
@Path("/api/v1/regattas/{regatta_id}")
public class TestRegattaIdentityEchoResource {

    @Inject
    SecurityContext securityContext;

    @GET
    @Path("/operator/echo-identity")
    @Produces(MediaType.APPLICATION_JSON)
    public EchoResponse operatorEchoIdentity(
        @PathParam("regatta_id") String regattaId,
        @HeaderParam("Remote-User") String remoteUser,
        @HeaderParam("Remote-Groups") String remoteGroups,
        @HeaderParam("Remote-Name") String remoteName,
        @HeaderParam("Remote-Email") String remoteEmail
    ) {
        return buildEchoResponse(regattaId, remoteUser, remoteGroups, remoteName, remoteEmail);
    }

    @GET
    @Path("/events/echo-identity")
    @Produces(MediaType.APPLICATION_JSON)
    public EchoResponse eventsEchoIdentity(
        @PathParam("regatta_id") String regattaId,
        @HeaderParam("Remote-User") String remoteUser,
        @HeaderParam("Remote-Groups") String remoteGroups,
        @HeaderParam("Remote-Name") String remoteName,
        @HeaderParam("Remote-Email") String remoteEmail
    ) {
        return buildEchoResponse(regattaId, remoteUser, remoteGroups, remoteName, remoteEmail);
    }

    @GET
    @Path("/entries/echo-identity")
    @Produces(MediaType.APPLICATION_JSON)
    public EchoResponse entriesEchoIdentity(
        @PathParam("regatta_id") String regattaId,
        @HeaderParam("Remote-User") String remoteUser,
        @HeaderParam("Remote-Groups") String remoteGroups,
        @HeaderParam("Remote-Name") String remoteName,
        @HeaderParam("Remote-Email") String remoteEmail
    ) {
        return buildEchoResponse(regattaId, remoteUser, remoteGroups, remoteName, remoteEmail);
    }

    @GET
    @Path("/operator_stuff/echo-identity")
    @Produces(MediaType.APPLICATION_JSON)
    public EchoResponse operatorStuffEchoIdentity(
        @PathParam("regatta_id") String regattaId,
        @HeaderParam("Remote-User") String remoteUser,
        @HeaderParam("Remote-Groups") String remoteGroups,
        @HeaderParam("Remote-Name") String remoteName,
        @HeaderParam("Remote-Email") String remoteEmail
    ) {
        return buildEchoResponse(regattaId, remoteUser, remoteGroups, remoteName, remoteEmail);
    }

    @GET
    @Path("/tokens/echo-identity")
    @Produces(MediaType.APPLICATION_JSON)
    public EchoResponse tokensEchoIdentity(
        @PathParam("regatta_id") String regattaId,
        @HeaderParam("Remote-User") String remoteUser,
        @HeaderParam("Remote-Groups") String remoteGroups,
        @HeaderParam("Remote-Name") String remoteName,
        @HeaderParam("Remote-Email") String remoteEmail
    ) {
        return buildEchoResponse(regattaId, remoteUser, remoteGroups, remoteName, remoteEmail);
    }

    private EchoResponse buildEchoResponse(
        String regattaId,
        String remoteUser,
        String remoteGroups,
        String remoteName,
        String remoteEmail
    ) {
        return new EchoResponse(
            regattaId,
            securityContext.isAuthenticated(),
            securityContext.isAuthenticated() ? securityContext.getPrincipal().getUsername() : null,
            remoteUser,
            remoteGroups,
            remoteName,
            remoteEmail
        );
    }

    public record EchoResponse(
        String regattaId,
        boolean authenticated,
        String username,
        String remoteUser,
        String remoteGroups,
        String remoteName,
        String remoteEmail
    ) {}
}
