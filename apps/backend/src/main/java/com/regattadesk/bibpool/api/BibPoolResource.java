package com.regattadesk.bibpool.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.bibpool.BibPoolService;
import com.regattadesk.bibpool.BibPoolValidationException;
import com.regattadesk.security.RequireRole;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.regattadesk.security.Role.REGATTA_ADMIN;
import static com.regattadesk.security.Role.SUPER_ADMIN;

@Path("/api/v1/regattas/{regatta_id}/bib_pools")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BibPoolResource {

    @Inject
    BibPoolService bibPoolService;

    @GET
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response listBibPools(@PathParam("regatta_id") UUID regattaId) {
        return Response.ok(BibPoolListResponse.from(bibPoolService.listBibPools(regattaId))).build();
    }

    @POST
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response createBibPool(@PathParam("regatta_id") UUID regattaId, BibPoolMutationRequest request) {
        try {
            validateRequest(request);
            var pool = bibPoolService.createBibPool(
                regattaId,
                request.block_id(),
                request.name(),
                request.allocation_mode(),
                request.start_bib(),
                request.end_bib(),
                request.bib_numbers(),
                request.is_overflow()
            );
            return Response.status(Response.Status.CREATED).entity(BibPoolResponse.from(pool)).build();
        } catch (BibPoolValidationException e) {
            return validationConflict(e);
        } catch (IllegalArgumentException e) {
            return mapIllegalArgument(e);
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(ErrorResponse.conflict(e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{pool_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response updateBibPool(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("pool_id") UUID poolId,
        BibPoolMutationRequest request
    ) {
        try {
            validateRequest(request);
            var pool = bibPoolService.updateBibPool(
                regattaId,
                poolId,
                request.name(),
                request.allocation_mode(),
                request.start_bib(),
                request.end_bib(),
                request.bib_numbers()
            );
            return Response.ok(BibPoolResponse.from(pool)).build();
        } catch (BibPoolValidationException e) {
            return validationConflict(e);
        } catch (IllegalArgumentException e) {
            return mapIllegalArgument(e);
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(ErrorResponse.conflict(e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{pool_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response deleteBibPool(@PathParam("regatta_id") UUID regattaId, @PathParam("pool_id") UUID poolId) {
        try {
            bibPoolService.deleteBibPool(regattaId, poolId);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return mapIllegalArgument(e);
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(ErrorResponse.conflict(e.getMessage())).build();
        }
    }

    @POST
    @Path("/reorder")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response reorderBibPools(@PathParam("regatta_id") UUID regattaId, BibPoolReorderRequest request) {
        try {
            if (request == null || request.items() == null || request.items().isEmpty()) {
                throw new IllegalArgumentException("Reorder items are required");
            }
            List<BibPoolService.ReorderItem> items = request.items().stream()
                .map(item -> new BibPoolService.ReorderItem(item.bib_pool_id(), item.priority()))
                .toList();
            return Response.ok(BibPoolListResponse.from(bibPoolService.reorderBibPools(regattaId, items))).build();
        } catch (IllegalArgumentException e) {
            return mapIllegalArgument(e);
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(ErrorResponse.conflict(e.getMessage())).build();
        }
    }

    private static void validateRequest(BibPoolMutationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if ("explicit_list".equals(request.allocation_mode())) {
            if (request.bib_numbers() == null || request.bib_numbers().isEmpty()) {
                throw new IllegalArgumentException("bib_numbers are required for explicit_list allocation");
            }
            return;
        }
        if (request.start_bib() == null || request.end_bib() == null) {
            throw new IllegalArgumentException("start_bib and end_bib are required for range allocation");
        }
    }

    private static Response validationConflict(BibPoolValidationException e) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ErrorResponse(
                "BIB_POOL_VALIDATION_ERROR",
                e.getMessage(),
                Map.of(
                    "overlapping_bibs", e.getOverlappingBibs(),
                    "conflicting_pool_id", e.getConflictingPoolId(),
                    "conflicting_pool_name", e.getConflictingPoolName()
                )
            ))
            .build();
    }

    private static Response mapIllegalArgument(IllegalArgumentException e) {
        if ("Regatta not found".equals(e.getMessage()) || "Bib pool not found".equals(e.getMessage())) {
            return Response.status(Response.Status.NOT_FOUND).entity(ErrorResponse.notFound(e.getMessage())).build();
        }
        return Response.status(Response.Status.BAD_REQUEST).entity(ErrorResponse.badRequest(e.getMessage())).build();
    }

    public record BibPoolMutationRequest(
        UUID block_id,
        String name,
        String allocation_mode,
        Integer start_bib,
        Integer end_bib,
        List<Integer> bib_numbers,
        Boolean is_overflow
    ) {
    }

    public record BibPoolReorderRequest(List<BibPoolReorderItem> items) {
    }

    public record BibPoolReorderItem(UUID bib_pool_id, int priority) {
    }

    public record BibPoolResponse(
        UUID id,
        UUID regatta_id,
        UUID block_id,
        String name,
        String allocation_mode,
        Integer start_bib,
        Integer end_bib,
        List<Integer> bib_numbers,
        int priority,
        boolean is_overflow
    ) {
        static BibPoolResponse from(BibPoolService.BibPoolView view) {
            return new BibPoolResponse(
                view.id(),
                view.regattaId(),
                view.blockId(),
                view.name(),
                view.allocationMode(),
                view.startBib(),
                view.endBib(),
                view.bibNumbers(),
                view.priority(),
                view.isOverflow()
            );
        }
    }

    public record BibPoolListResponse(List<BibPoolResponse> data) {
        static BibPoolListResponse from(List<BibPoolService.BibPoolView> pools) {
            return new BibPoolListResponse(pools.stream().map(BibPoolResponse::from).toList());
        }
    }
}
