package com.regattadesk.regatta.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.entry.EntryNotFoundException;
import com.regattadesk.security.RequireRole;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.regattadesk.security.Role.INFO_DESK;
import static com.regattadesk.security.Role.REGATTA_ADMIN;
import static com.regattadesk.security.Role.SUPER_ADMIN;

@Path("/api/v1/regattas/{regatta_id}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RegattaSetupResource {

    @Inject
    RegattaSetupService service;

    @POST
    @Path("/event-groups")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response createEventGroup(@PathParam("regatta_id") UUID regattaId, @Valid EventGroupCreateRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.createEventGroup(regattaId, request)).build();
    }

    @GET
    @Path("/event-groups")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response listEventGroups(@PathParam("regatta_id") UUID regattaId, @QueryParam("search") String search) {
        return Response.ok(service.listEventGroups(regattaId, search)).build();
    }

    @POST
    @Path("/events")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response createEvent(@PathParam("regatta_id") UUID regattaId, @Valid EventCreateRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.createEvent(regattaId, request)).build();
    }

    @GET
    @Path("/events")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response listEvents(@PathParam("regatta_id") UUID regattaId) {
        return Response.ok(service.listEvents(regattaId)).build();
    }

    @POST
    @Path("/crews")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response createCrew(@PathParam("regatta_id") UUID regattaId, @Valid CrewCreateRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.createCrew(regattaId, request)).build();
    }

    @GET
    @Path("/crews")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response listCrews(@PathParam("regatta_id") UUID regattaId) {
        return Response.ok(service.listCrews(regattaId)).build();
    }

    @POST
    @Path("/entries")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response createEntry(@PathParam("regatta_id") UUID regattaId, @Valid EntryCreateRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.createEntry(regattaId, request)).build();
    }

    @GET
    @Path("/entries")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response listEntries(@PathParam("regatta_id") UUID regattaId, @QueryParam("status") String status) {
        return Response.ok(service.listEntries(regattaId, status)).build();
    }

    @POST
    @Path("/entries/{entry_id}/withdraw")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response withdrawEntry(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("entry_id") UUID entryId,
        @HeaderParam("Remote-User") String actor,
        @Valid WithdrawEntryRequest request
    ) {
        return Response.ok(service.withdrawEntry(regattaId, entryId, request, actor)).build();
    }

    @POST
    @Path("/entries/{entry_id}/reinstate")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response reinstateEntry(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("entry_id") UUID entryId,
        @HeaderParam("Remote-User") String actor,
        @Valid ReinstateEntryRequest request
    ) {
        return Response.ok(service.reinstateEntry(regattaId, entryId, request, actor)).build();
    }

    @jakarta.ws.rs.ext.Provider
    public static class SetupConflictExceptionMapper implements jakarta.ws.rs.ext.ExceptionMapper<RegattaSetupService.ConflictException> {
        @Override
        public Response toResponse(RegattaSetupService.ConflictException conflict) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse("CONFLICT", conflict.getMessage(), conflict.details()))
                .build();
        }
    }

    @jakarta.ws.rs.ext.Provider
    public static class SetupEntryNotFoundExceptionMapper implements jakarta.ws.rs.ext.ExceptionMapper<EntryNotFoundException> {
        @Override
        public Response toResponse(EntryNotFoundException exception) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(exception.getMessage()))
                .build();
        }
    }

    @jakarta.ws.rs.ext.Provider
    public static class SetupForbiddenExceptionMapper implements jakarta.ws.rs.ext.ExceptionMapper<ForbiddenException> {
        @Override
        public Response toResponse(ForbiddenException exception) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("FORBIDDEN", exception.getMessage()))
                .build();
        }
    }
}

record EventGroupCreateRequest(
    @NotBlank @Size(max = 255) String name,
    String description,
    int display_order
) {
}

record EventCreateRequest(
    @NotNull UUID event_group_id,
    @NotNull UUID category_id,
    @NotNull UUID boat_type_id,
    @NotBlank @Size(max = 255) String name,
    int display_order
) {
}

record CrewCreateRequest(
    @NotBlank @Size(max = 255) String display_name,
    UUID club_id,
    boolean is_composite,
    @NotNull @Size(min = 1) List<@Valid CrewMemberRequest> members
) {
}

record CrewMemberRequest(
    @NotNull UUID athlete_id,
    @NotNull Integer seat_position
) {
}

record EntryCreateRequest(
    @NotNull UUID event_id,
    @NotNull UUID block_id,
    @NotNull UUID crew_id,
    UUID billing_club_id
) {
}

record WithdrawEntryRequest(
    @NotBlank @Pattern(regexp = "^(withdrawn_before_draw|withdrawn_after_draw)$") String status,
    @NotBlank String reason,
    @NotBlank @Pattern(regexp = "^(entered|withdrawn_before_draw|withdrawn_after_draw|dns|dnf|excluded|dsq)$") String expected_status
) {
}

record ReinstateEntryRequest(
    @NotBlank @Pattern(regexp = "^(withdrawn_before_draw|withdrawn_after_draw)$") String expected_status
) {
}

record EventGroupResponse(
    UUID id,
    UUID regatta_id,
    String name,
    String description,
    int display_order
) {
}

record EventResponse(
    UUID id,
    UUID regatta_id,
    UUID event_group_id,
    UUID category_id,
    UUID boat_type_id,
    String name,
    int display_order
) {
}

record CrewMemberResponse(
    UUID athlete_id,
    int seat_position
) {
}

record CrewResponse(
    UUID id,
    String display_name,
    UUID club_id,
    boolean is_composite,
    List<CrewMemberResponse> members
) {
}

record EntryResponse(
    UUID id,
    UUID regatta_id,
    UUID event_id,
    UUID block_id,
    UUID crew_id,
    UUID billing_club_id,
    String status,
    String payment_status,
    Instant paid_at,
    String paid_by,
    String payment_reference,
    Long marker_start_time_ms,
    Long marker_finish_time_ms,
    String completion_status
) {
    static EntryResponse from(com.regattadesk.entry.EntryDto dto) {
        return new EntryResponse(
            dto.id(),
            dto.regattaId(),
            dto.eventId(),
            dto.blockId(),
            dto.crewId(),
            dto.billingClubId(),
            dto.status(),
            dto.paymentStatus(),
            dto.paidAt(),
            dto.paidBy(),
            dto.paymentReference(),
            dto.markerStartTimeMs(),
            dto.markerFinishTimeMs(),
            dto.completionStatus()
        );
    }
}

record WithdrawResponse(
    UUID id,
    String status,
    AuditResponse audit
) {
}

record AuditResponse(
    String actor,
    Instant at,
    String reason
) {
}

record ListResponse<T>(
    List<T> data,
    Pagination pagination
) {
}

record Pagination(
    boolean has_more,
    String next_cursor
) {
}
