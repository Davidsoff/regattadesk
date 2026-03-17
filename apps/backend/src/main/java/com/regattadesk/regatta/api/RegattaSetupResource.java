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
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

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
    @Operation(summary = "Create Event Group")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = EventGroupResponse.class))),
        @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response createEventGroup(@PathParam("regatta_id") UUID regattaId, @Valid EventGroupCreateRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.createEventGroup(regattaId, request)).build();
    }

    @GET
    @Path("/event-groups")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "List Event Groups")
    @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = EventGroupListResponse.class)))
    public Response listEventGroups(@PathParam("regatta_id") UUID regattaId, @QueryParam("search") String search) {
        return Response.ok(EventGroupListResponse.from(service.listEventGroups(regattaId, search))).build();
    }

    @PATCH
    @Path("/event-groups/{event_group_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Update Event Group")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = EventGroupResponse.class))),
        @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateEventGroup(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("event_group_id") UUID eventGroupId,
        @Valid EventGroupCreateRequest request
    ) {
        return Response.ok(service.updateEventGroup(regattaId, eventGroupId, request)).build();
    }

    @DELETE
    @Path("/event-groups/{event_group_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Delete Event Group")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "No Content"),
        @APIResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response deleteEventGroup(@PathParam("regatta_id") UUID regattaId, @PathParam("event_group_id") UUID eventGroupId) {
        service.deleteEventGroup(regattaId, eventGroupId);
        return Response.noContent().build();
    }

    @POST
    @Path("/events")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Create Event")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = EventResponse.class))),
        @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response createEvent(@PathParam("regatta_id") UUID regattaId, @Valid EventCreateRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.createEvent(regattaId, request)).build();
    }

    @GET
    @Path("/events")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "List Events")
    @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = EventListResponse.class)))
    public Response listEvents(@PathParam("regatta_id") UUID regattaId, @QueryParam("search") String search) {
        return Response.ok(EventListResponse.from(service.listEvents(regattaId, search))).build();
    }

    @PATCH
    @Path("/events/{event_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Update Event")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = EventResponse.class))),
        @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateEvent(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("event_id") UUID eventId,
        @Valid EventCreateRequest request
    ) {
        return Response.ok(service.updateEvent(regattaId, eventId, request)).build();
    }

    @DELETE
    @Path("/events/{event_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Delete Event")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "No Content"),
        @APIResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response deleteEvent(@PathParam("regatta_id") UUID regattaId, @PathParam("event_id") UUID eventId) {
        service.deleteEvent(regattaId, eventId);
        return Response.noContent().build();
    }

    @POST
    @Path("/crews")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Create Crew")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CrewResponse.class))),
        @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response createCrew(@PathParam("regatta_id") UUID regattaId, @Valid CrewCreateRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.createCrew(regattaId, request)).build();
    }

    @GET
    @Path("/crews")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "List Crews")
    @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CrewListResponse.class)))
    public Response listCrews(@PathParam("regatta_id") UUID regattaId, @QueryParam("search") String search) {
        return Response.ok(CrewListResponse.from(service.listCrews(regattaId, search))).build();
    }

    @PATCH
    @Path("/crews/{crew_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Update Crew")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CrewResponse.class))),
        @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "409", description = "Conflict", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateCrew(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("crew_id") UUID crewId,
        @Valid CrewCreateRequest request
    ) {
        return Response.ok(service.updateCrew(regattaId, crewId, request)).build();
    }

    @DELETE
    @Path("/crews/{crew_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Delete Crew")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "No Content"),
        @APIResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "409", description = "Conflict", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response deleteCrew(@PathParam("regatta_id") UUID regattaId, @PathParam("crew_id") UUID crewId) {
        service.deleteCrew(regattaId, crewId);
        return Response.noContent().build();
    }

    @POST
    @Path("/entries")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Create Entry")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = EntryResponse.class))),
        @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response createEntry(@PathParam("regatta_id") UUID regattaId, @Valid EntryCreateRequest request) {
        return Response.status(Response.Status.CREATED).entity(service.createEntry(regattaId, request)).build();
    }

    @GET
    @Path("/entries")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "List Entries")
    @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = EntryListResponse.class)))
    public Response listEntries(
        @PathParam("regatta_id") UUID regattaId,
        @QueryParam("status") String status,
        @QueryParam("search") String search
    ) {
        return Response.ok(EntryListResponse.from(service.listEntries(regattaId, status, search))).build();
    }

    @PATCH
    @Path("/entries/{entry_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Update Entry")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = EntryResponse.class))),
        @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateEntry(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("entry_id") UUID entryId,
        @Valid EntryCreateRequest request
    ) {
        return Response.ok(service.updateEntry(regattaId, entryId, request)).build();
    }

    @DELETE
    @Path("/entries/{entry_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Delete Entry")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "No Content"),
        @APIResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response deleteEntry(@PathParam("regatta_id") UUID regattaId, @PathParam("entry_id") UUID entryId) {
        service.deleteEntry(regattaId, entryId);
        return Response.noContent().build();
    }

    @POST
    @Path("/entries/{entry_id}/withdraw")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Withdraw Entry")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = WithdrawResponse.class))),
        @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "409", description = "Conflict", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response withdrawEntry(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("entry_id") UUID entryId,
        @Parameter(hidden = true)
        @HeaderParam("Remote-User") String actor,
        @RequestBody(required = true)
        @Valid WithdrawEntryRequest request
    ) {
        return Response.ok(service.withdrawEntry(regattaId, entryId, request, actor)).build();
    }

    @POST
    @Path("/entries/{entry_id}/reinstate")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    @Operation(summary = "Reinstate Entry")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = WithdrawResponse.class))),
        @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "409", description = "Conflict", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response reinstateEntry(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("entry_id") UUID entryId,
        @Parameter(hidden = true)
        @HeaderParam("Remote-User") String actor,
        @RequestBody(required = true)
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
    public static class SetupNotFoundExceptionMapper implements jakarta.ws.rs.ext.ExceptionMapper<RegattaSetupService.SetupNotFoundException> {
        @Override
        public Response toResponse(RegattaSetupService.SetupNotFoundException exception) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(exception.getMessage()))
                .build();
        }
    }

    @jakarta.ws.rs.ext.Provider
    public static class SetupBadRequestExceptionMapper implements jakarta.ws.rs.ext.ExceptionMapper<RegattaSetupService.SetupBadRequestException> {
        @Override
        public Response toResponse(RegattaSetupService.SetupBadRequestException exception) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(exception.getMessage()))
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
    @NotBlank
    @Pattern(regexp = "^(withdrawn_before_draw|withdrawn_after_draw)$")
    @Schema(enumeration = {"withdrawn_before_draw", "withdrawn_after_draw"})
    String status,
    @NotBlank String reason,
    @NotBlank
    @Pattern(regexp = "^(entered|withdrawn_before_draw|withdrawn_after_draw|dns|dnf|excluded|dsq)$")
    @Schema(enumeration = {"entered", "withdrawn_before_draw", "withdrawn_after_draw", "dns", "dnf", "excluded", "dsq"})
    String expected_status
) {
}

record ReinstateEntryRequest(
    @NotBlank
    @Pattern(regexp = "^(withdrawn_before_draw|withdrawn_after_draw)$")
    @Schema(enumeration = {"withdrawn_before_draw", "withdrawn_after_draw"})
    String expected_status
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
    @Schema(enumeration = {"entered", "withdrawn_before_draw", "withdrawn_after_draw", "dns", "dnf", "excluded", "dsq"}) String status,
    @Schema(enumeration = {"unpaid", "paid"}) String payment_status,
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
    @Schema(enumeration = {"entered", "withdrawn_before_draw", "withdrawn_after_draw"}) String status,
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

record EventGroupListResponse(
    List<EventGroupResponse> data,
    Pagination pagination
) {
    static EventGroupListResponse from(ListResponse<EventGroupResponse> response) {
        return new EventGroupListResponse(response.data(), response.pagination());
    }
}

record EventListResponse(
    List<EventResponse> data,
    Pagination pagination
) {
    static EventListResponse from(ListResponse<EventResponse> response) {
        return new EventListResponse(response.data(), response.pagination());
    }
}

record CrewListResponse(
    List<CrewResponse> data,
    Pagination pagination
) {
    static CrewListResponse from(ListResponse<CrewResponse> response) {
        return new CrewListResponse(response.data(), response.pagination());
    }
}

record EntryListResponse(
    List<EntryResponse> data,
    Pagination pagination
) {
    static EntryListResponse from(ListResponse<EntryResponse> response) {
        return new EntryListResponse(response.data(), response.pagination());
    }
}

record Pagination(
    boolean has_more,
    String next_cursor
) {
}
