package com.regattadesk.block.api;

import com.regattadesk.api.dto.ErrorResponse;
import com.regattadesk.block.BlockService;
import com.regattadesk.security.RequireRole;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.regattadesk.security.Role.REGATTA_ADMIN;
import static com.regattadesk.security.Role.SUPER_ADMIN;

@Path("/api/v1/regattas/{regatta_id}/blocks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BlockResource {

    @Inject
    BlockService blockService;

    @GET
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response listBlocks(@PathParam("regatta_id") UUID regattaId) {
        return Response.ok(BlockListResponse.from(blockService.listBlocks(regattaId))).build();
    }

    @POST
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response createBlock(@PathParam("regatta_id") UUID regattaId, BlockMutationRequest request) {
        try {
            validateRequest(request);
            var block = blockService.createBlock(
                regattaId,
                request.name(),
                Instant.parse(request.start_time()),
                request.event_interval_seconds(),
                request.crew_interval_seconds()
            );
            return Response.status(Response.Status.CREATED).entity(BlockResponse.from(block)).build();
        } catch (IllegalArgumentException e) {
            return mapIllegalArgument(e);
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(ErrorResponse.conflict(e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{block_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response updateBlock(
        @PathParam("regatta_id") UUID regattaId,
        @PathParam("block_id") UUID blockId,
        BlockMutationRequest request
    ) {
        try {
            validateRequest(request);
            var block = blockService.updateBlock(
                regattaId,
                blockId,
                request.name(),
                Instant.parse(request.start_time()),
                request.event_interval_seconds(),
                request.crew_interval_seconds()
            );
            return Response.ok(BlockResponse.from(block)).build();
        } catch (IllegalArgumentException e) {
            return mapIllegalArgument(e);
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(ErrorResponse.conflict(e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{block_id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response deleteBlock(@PathParam("regatta_id") UUID regattaId, @PathParam("block_id") UUID blockId) {
        try {
            blockService.deleteBlock(regattaId, blockId);
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
    public Response reorderBlocks(@PathParam("regatta_id") UUID regattaId, BlockReorderRequest request) {
        try {
            if (request == null || request.items() == null || request.items().isEmpty()) {
                throw new IllegalArgumentException("Reorder items are required");
            }
            List<BlockService.ReorderItem> items = request.items().stream()
                .map(item -> new BlockService.ReorderItem(item.block_id(), item.display_order()))
                .toList();
            return Response.ok(BlockListResponse.from(blockService.reorderBlocks(regattaId, items))).build();
        } catch (IllegalArgumentException e) {
            return mapIllegalArgument(e);
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(ErrorResponse.conflict(e.getMessage())).build();
        }
    }

    private static void validateRequest(BlockMutationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        Instant.parse(request.start_time());
    }

    private static Response mapIllegalArgument(IllegalArgumentException e) {
        if ("Regatta not found".equals(e.getMessage()) || "Block not found".equals(e.getMessage())) {
            return Response.status(Response.Status.NOT_FOUND).entity(ErrorResponse.notFound(e.getMessage())).build();
        }
        return Response.status(Response.Status.BAD_REQUEST).entity(ErrorResponse.badRequest(e.getMessage())).build();
    }

    public record BlockMutationRequest(
        String name,
        String start_time,
        int event_interval_seconds,
        int crew_interval_seconds
    ) {
    }

    public record BlockReorderRequest(List<BlockReorderItem> items) {
    }

    public record BlockReorderItem(UUID block_id, int display_order) {
    }

    public record BlockResponse(
        UUID id,
        UUID regatta_id,
        String name,
        Instant start_time,
        int event_interval_seconds,
        int crew_interval_seconds,
        int display_order
    ) {
        static BlockResponse from(BlockService.BlockView view) {
            return new BlockResponse(
                view.id(),
                view.regattaId(),
                view.name(),
                view.startTime(),
                view.eventIntervalSeconds(),
                view.crewIntervalSeconds(),
                view.displayOrder()
            );
        }
    }

    public record BlockListResponse(List<BlockResponse> data) {
        static BlockListResponse from(List<BlockService.BlockView> blocks) {
            return new BlockListResponse(blocks.stream().map(BlockResponse::from).toList());
        }
    }
}
