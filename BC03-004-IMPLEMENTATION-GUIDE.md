# BC03-004 Implementation Guide

## Summary

This document provides implementation guidance for completing BC03-004 (Regatta Setup CRUD APIs). The Athletes entity has been fully implemented as a reference pattern that can be extended to other entities (Crews, Events, EventGroups, Entries).

## What's Been Implemented

### 1. Database Migrations (✅ Complete)

**Files Created:**
- `V005__athletes_read_model.sql` - Athletes, Clubs, Crews tables
- `V006__regatta_entities_read_model.sql` - Categories, BoatTypes, EventGroups, Events, Blocks, Entries tables
- H2-compatible versions for both migrations

All read model tables are now in place for regatta setup entities.

### 2. Athletes Domain (✅ Complete - Reference Implementation)

**Event Sourcing Pattern:**
```
AthleteAggregate
  ├── AthleteCreatedEvent
  ├── AthleteUpdatedEvent
  └── AthleteDeletedEvent

AthleteProjectionHandler
  └── Transforms events → athletes read model table

AthleteService
  └── Orchestrates commands, emits events

AthleteResource (REST API)
  ├── GET /api/v1/athletes (list with search)
  ├── GET /api/v1/athletes/{id}
  ├── POST /api/v1/athletes
  ├── PATCH /api/v1/athletes/{id}
  └── DELETE /api/v1/athletes/{id}
```

## How to Extend to Other Entities

To implement CRUD for Crews, Events, EventGroups, or Entries, follow this pattern:

### Step 1: Create Domain Events

Create three events for each entity (following `AthleteCreatedEvent` pattern):

```java
package com.regattadesk.{entity};

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.regattadesk.eventstore.DomainEvent;
import java.util.UUID;

public class {Entity}CreatedEvent implements DomainEvent {
    private final UUID {entity}Id;
    private final /* entity fields */;

    @JsonCreator
    public {Entity}CreatedEvent(
        @JsonProperty("{entity}Id") UUID {entity}Id,
        /* other fields */
    ) {
        this.{entity}Id = {entity}Id;
        /* assign fields */
    }

    @Override
    public String getEventType() {
        return "{Entity}Created";
    }

    @Override
    public UUID getAggregateId() {
        return {entity}Id;
    }

    // Getters for all fields
}
```

Do the same for `{Entity}UpdatedEvent` and `{Entity}DeletedEvent`.

### Step 2: Create Aggregate

Follow `AthleteAggregate` pattern:

```java
package com.regattadesk.{entity};

import com.regattadesk.aggregate.AggregateRoot;
import com.regattadesk.eventstore.DomainEvent;
import java.util.UUID;

public class {Entity}Aggregate extends AggregateRoot<{Entity}Aggregate> {
    private /* entity state fields */;

    public {Entity}Aggregate(UUID id) {
        super(id);
    }

    public static {Entity}Aggregate create(UUID id, /* params */) {
        /* validation */
        var aggregate = new {Entity}Aggregate(id);
        aggregate.raiseEvent(new {Entity}CreatedEvent(id, /* params */));
        return aggregate;
    }

    public void update(/* params */) {
        /* validation */
        raiseEvent(new {Entity}UpdatedEvent(getId(), /* params */));
    }

    public void delete() {
        raiseEvent(new {Entity}DeletedEvent(getId()));
    }

    @Override
    protected void applyEventToState(DomainEvent event) {
        if (event instanceof {Entity}CreatedEvent e) {
            /* set state from event */
        } else if (event instanceof {Entity}UpdatedEvent e) {
            /* update state from event */
        } else if (event instanceof {Entity}DeletedEvent e) {
            /* mark deleted */
        }
    }

    @Override
    public String getAggregateType() {
        return "{Entity}";
    }

    // Getters
}
```

### Step 3: Create Projection Handler

Follow `AthleteProjectionHandler` pattern:

```java
package com.regattadesk.{entity};

import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.projection.ProjectionHandler;
import jakarta.enterprise.context.ApplicationScoped;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

@ApplicationScoped
public class {Entity}ProjectionHandler implements ProjectionHandler {

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public String getProjectionName() {
        return "{entity}_projection";
    }

    @Override
    public boolean canHandle(EventEnvelope event) {
        String eventType = event.getEventType();
        return "{Entity}Created".equals(eventType) ||
               "{Entity}Updated".equals(eventType) ||
               "{Entity}Deleted".equals(eventType);
    }

    @Override
    public void handle(EventEnvelope envelope) {
        var eventType = envelope.getEventType();
        try {
            switch (eventType) {
                case "{Entity}Created" -> handle{Entity}Created(envelope);
                case "{Entity}Updated" -> handle{Entity}Updated(envelope);
                case "{Entity}Deleted" -> handle{Entity}Deleted(envelope);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle event: " + eventType, e);
        }
    }

    // Private methods to handle each event type with SQL INSERT/UPDATE/DELETE
}
```

### Step 4: Create Service

Follow `AthleteService` pattern:

```java
package com.regattadesk.{entity};

import com.regattadesk.eventstore.EventStore;
import com.regattadesk.eventstore.EventMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class {Entity}Service {

    @Inject
    EventStore eventStore;

    @Inject
    DataSource dataSource;

    @Transactional
    public UUID create{Entity}(/* params */) {
        UUID id = UUID.randomUUID();
        var aggregate = {Entity}Aggregate.create(id, /* params */);
        var metadata = EventMetadata.builder()
                .correlationId(UUID.randomUUID())
                .build();

        eventStore.append(
                id,
                "{Entity}",
                aggregate.getVersion(),
                aggregate.getUncommittedEvents(),
                metadata
        );

        return id;
    }

    @Transactional
    public void update{Entity}(UUID id, /* params */) {
        /* Load aggregate from event stream, call update, append events */
    }

    @Transactional
    public void delete{Entity}(UUID id) {
        /* Load aggregate from event stream, call delete, append events */
    }

    public Optional<{Entity}Dto> get{Entity}(UUID id) throws Exception {
        /* Query read model table */
    }

    public List<{Entity}Dto> list{Entity}s(/* params */) throws Exception {
        /* Query read model table with filters */
    }
}
```

### Step 5: Create REST Resource

Follow `AthleteResource` pattern with DTOs:

```java
package com.regattadesk.{entity}.api;

import com.regattadesk.{entity}.{Entity}Service;
import com.regattadesk.security.RequireRole;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

import static com.regattadesk.security.Role.*;

@Path("/api/v1/{entities}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class {Entity}Resource {

    @Inject
    {Entity}Service service;

    @GET
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK})
    public Response list() { /* ... */ }

    @GET
    @Path("/{id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, HEAD_OF_JURY, INFO_DESK})
    public Response get(@PathParam("id") UUID id) { /* ... */ }

    @POST
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response create(/* request DTO */) { /* ... */ }

    @PATCH
    @Path("/{id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN, INFO_DESK})
    public Response update(@PathParam("id") UUID id, /* request DTO */) { /* ... */ }

    @DELETE
    @Path("/{id}")
    @RequireRole({SUPER_ADMIN, REGATTA_ADMIN})
    public Response delete(@PathParam("id") UUID id) { /* ... */ }
}
```

## Testing Strategy

### Unit Tests
For each aggregate, test:
- Creation validation
- Update validation
- State reconstruction from events
- Delete behavior

Example: `AthleteAggregateTest` (to be created)

### Integration Tests
For each projection handler, test:
- Event processing
- Database updates
- Idempotency

Example: `AthleteProjectionHandlerTest` (to be created)

### API Tests
For each REST resource, test:
- All CRUD operations
- Error handling
- Role-based access control

Example: `AthleteResourceTest` (to be created)

## Remaining Work for BC03-004

### Priority 1: Core Entities
1. **Crews** - High priority (referenced by Entries)
2. **Events** - High priority (referenced by Entries)
3. **EventGroups** - Medium priority (organizes Events)
4. **Entries** - High priority (core workflow)

### Priority 2: Crew Mutations
- CrewMemberAddedEvent
- CrewMemberRemovedEvent
- CrewMemberMovedEvent
- Update AthleteAggregate/CrewAggregate to support these

### Priority 3: Withdrawal Workflow
- EntryWithdrawnEvent (before/after draw)
- EntryStatusChangedEvent (DNS, DNF, etc.)
- Update EntryAggregate to support status transitions

### Priority 4: Testing
- Unit tests for all aggregates
- Integration tests for all projection handlers
- API contract tests for all REST resources
- End-to-end workflow tests

### Priority 5: Final Verification
- Code review
- CodeQL security scan
- Performance testing
- Documentation review

## Key Patterns to Follow

1. **Always use EventMetadata** when appending events
2. **Always validate** in aggregate factory/command methods
3. **Always handle PostgreSQL vs H2** in projection handlers
4. **Always use UUIDs** for entity IDs
5. **Always make events immutable** (use `final` fields)
6. **Always implement `getAggregateType()`** in aggregates
7. **Always use `@RequireRole`** for authorization
8. **Always use `@Transactional`** on service methods that append events

## Reference Files

**Complete Implementation:**
- `/apps/backend/src/main/java/com/regattadesk/athlete/*` - All athlete files

**Existing Patterns:**
- `/apps/backend/src/main/java/com/regattadesk/regatta/RegattaAggregate.java`
- `/apps/backend/src/main/java/com/regattadesk/regatta/RegattaProjectionHandler.java`

**Database Schema:**
- `/pdd/design/database-schema.md` - Full schema specification
- `/apps/backend/src/main/resources/db/migration/V005__*.sql` - Athletes migration
- `/apps/backend/src/main/resources/db/migration/V006__*.sql` - Other entities migration

**API Specification:**
- `/pdd/design/openapi-v0.1.yaml` - Complete API contract

## Status Summary

✅ **Complete:**
- Database schema and migrations
- Athletes domain with full CRUD
- Event sourcing infrastructure
- Projection infrastructure
- REST API pattern
- All tests passing (192/192)

⏳ **Remaining:**
- Crews, Events, EventGroups, Entries implementations
- Crew mutation workflows
- Entry withdrawal workflows
- Comprehensive test coverage
- Code review and security scan

The foundation is solid and the pattern is proven. The remaining work is straightforward replication of the Athletes pattern for other entities.
