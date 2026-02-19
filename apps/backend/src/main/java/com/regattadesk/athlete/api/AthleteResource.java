package com.regattadesk.athlete.api;

import com.regattadesk.athlete.AthleteService;
import com.regattadesk.security.RequireRole;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.regattadesk.security.Role.*;

@Path("/api/v1/athletes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AthleteResource {

    @Inject
    AthleteService athleteService;

    @GET
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK})
    public Response listAthletes(
            @QueryParam("search") String search,
            @QueryParam("limit") @DefaultValue("30") int limit,
            @QueryParam("cursor") String cursor
    ) {
        try {
            var athletes = athleteService.listAthletes(search, limit, cursor);
            var athleteResponses = athletes.stream()
                    .map(AthleteResponse::from)
                    .collect(Collectors.toList());

            var response = new AthleteListResponse(
                    athleteResponses,
                    new AthleteListResponse.PaginationInfo(null, limit, null)
            );

            return Response.ok(response).build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to list athletes: " + e.getMessage()))
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
                        .entity(new ErrorResponse("Athlete not found"))
                        .build();
            }

            return Response.ok(AthleteResponse.from(athlete.get())).build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to get athlete: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response createAthlete(@Valid AthleteCreateRequest request) {
        try {
            UUID athleteId = athleteService.createAthlete(
                    request.firstName(),
                    request.middleName(),
                    request.lastName(),
                    request.dateOfBirth(),
                    request.gender(),
                    request.clubId()
            );

            // Fetch the created athlete to return it
            var athlete = athleteService.getAthlete(athleteId);
            if (athlete.isEmpty()) {
                return Response.serverError()
                        .entity(new ErrorResponse("Failed to fetch created athlete"))
                        .build();
            }

            return Response.created(URI.create("/api/v1/athletes/" + athleteId))
                    .entity(AthleteResponse.from(athlete.get()))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to create athlete: " + e.getMessage()))
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
            // Get current athlete to fill in missing fields
            var current = athleteService.getAthlete(athleteId);
            if (current.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Athlete not found"))
                        .build();
            }

            var currentDto = current.get();

            athleteService.updateAthlete(
                    athleteId,
                    request.firstName() != null ? request.firstName() : currentDto.firstName(),
                    request.middleName() != null ? request.middleName() : currentDto.middleName(),
                    request.lastName() != null ? request.lastName() : currentDto.lastName(),
                    request.gender() != null ? request.gender() : currentDto.gender(),
                    request.clubId() != null ? request.clubId() : currentDto.clubId()
            );

            // Fetch updated athlete
            var updated = athleteService.getAthlete(athleteId);
            return Response.ok(AthleteResponse.from(updated.get())).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to update athlete: " + e.getMessage()))
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
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(new ErrorResponse("Failed to delete athlete: " + e.getMessage()))
                    .build();
        }
    }

    public record ErrorResponse(String message) {
    }
}
