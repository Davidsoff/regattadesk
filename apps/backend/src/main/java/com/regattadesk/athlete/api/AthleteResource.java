package com.regattadesk.athlete.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.athlete.AthleteService;
import com.regattadesk.security.RequireRole;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.regattadesk.security.Role.HEAD_OF_JURY;
import static com.regattadesk.security.Role.INFO_DESK;
import static com.regattadesk.security.Role.REGATTA_ADMIN;
import static com.regattadesk.security.Role.SUPER_ADMIN;

@Path("/api/v1/athletes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AthleteResource {

    private static final int MAX_LIST_LIMIT = 100;

    @Inject
    AthleteService athleteService;

    @GET
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK})
    public Response listAthletes(
            @QueryParam("federation_code") String federationCode,
            @QueryParam("federation_external_id") String federationExternalId,
            @QueryParam("search") String search,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("cursor") String cursor
    ) {
        try {
            boolean hasFederationCode = federationCode != null && !federationCode.isBlank();
            boolean hasFederationExternalId = federationExternalId != null && !federationExternalId.isBlank();
            if (hasFederationCode != hasFederationExternalId) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.badRequest(
                        "Both federation_code and federation_external_id must be provided together or both omitted"
                    ))
                    .build();
            }

            int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
            var athletes = athleteService.listAthletes(
                search,
                federationCode,
                federationExternalId,
                effectiveLimit,
                cursor
            );

            var athleteResponses = athletes.stream()
                .map(AthleteResponse::from)
                .collect(Collectors.toList());

            var response = new AthleteListResponse(
                athleteResponses,
                new AthleteListResponse.PaginationInfo(false, null)
            );

            return Response.ok(response).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to list athletes"))
                .build();
        }
    }

    @GET
    @Path("/{athlete_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK})
    public Response getAthlete(@PathParam("athlete_id") UUID athleteId) {
        try {
            var athlete = athleteService.getAthlete(athleteId);
            if (athlete.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Athlete not found"))
                    .build();
            }

            return Response.ok(AthleteResponse.from(athlete.get())).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to get athlete"))
                .build();
        }
    }

    @POST
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response createAthlete(@Valid AthleteCreateRequest request) {
        try {
            var created = athleteService.createAthlete(
                request.firstName(),
                request.middleName(),
                request.lastName(),
                request.dateOfBirth(),
                request.gender(),
                request.clubId()
            );

            return Response.created(URI.create("/api/v1/athletes/" + created.id()))
                .entity(AthleteResponse.from(created))
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to create athlete"))
                .build();
        }
    }

    @PATCH
    @Path("/{athlete_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response updateAthlete(
            @PathParam("athlete_id") UUID athleteId,
            AthleteUpdateRequest request
    ) {
        try {
            var current = athleteService.getAthleteState(athleteId);
            if (current.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound("Athlete not found"))
                    .build();
            }

            var currentDto = current.get();
            var updated = athleteService.updateAthlete(
                athleteId,
                request.firstName() != null ? request.firstName() : currentDto.firstName(),
                request.middleName() != null ? request.middleName() : currentDto.middleName(),
                request.lastName() != null ? request.lastName() : currentDto.lastName(),
                request.dateOfBirth() != null ? request.dateOfBirth() : currentDto.dateOfBirth(),
                request.gender() != null ? request.gender() : currentDto.gender(),
                request.clubId() != null ? request.clubId() : currentDto.clubId()
            );

            return Response.ok(AthleteResponse.from(updated)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to update athlete"))
                .build();
        }
    }

    @DELETE
    @Path("/{athlete_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response deleteAthlete(@PathParam("athlete_id") UUID athleteId) {
        try {
            athleteService.deleteAthlete(athleteId);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(ErrorResponse.internalError("Failed to delete athlete"))
                .build();
        }
    }
}
