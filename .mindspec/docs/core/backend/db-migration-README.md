# Event Store Implementation

This directory contains the event-sourcing infrastructure for RegattaDesk v0.1.

## Overview

The event store provides an immutable, append-only log of all domain events with full audit trail capability. It serves as the foundation for:
- Complete audit history of all regatta management actions
- Event sourcing for aggregate reconstruction
- Projection rebuilding from source events
- Investigation support with historical evidence

## Database Schema

The event store consists of two primary tables:

### `aggregates`
Tracks aggregate root entities with versioning for optimistic concurrency.

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Unique identifier for the aggregate root |
| `aggregate_type` | VARCHAR(100) | Type discriminator (e.g., Regatta, Entry, Investigation) |
| `version` | BIGINT | Current version number, incremented with each event |
| `created_at` | TIMESTAMPTZ | Timestamp of aggregate creation |
| `updated_at` | TIMESTAMPTZ | Timestamp of last version increment |

### `event_store`
Append-only log of all domain events.

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Unique event identifier |
| `aggregate_id` | UUID | Reference to the aggregate root |
| `event_type` | VARCHAR(100) | Event type discriminator |
| `sequence_number` | BIGINT | Monotonic sequence within aggregate stream |
| `payload` | JSONB | Event data as flexible JSON |
| `metadata` | JSONB | Additional metadata (user_id, client_ip, etc.) |
| `correlation_id` | UUID | Correlation identifier for tracing related events |
| `causation_id` | UUID | ID of the event that caused this event |
| `created_at` | TIMESTAMPTZ | Immutable timestamp when event was appended |

## Immutability Constraints

The event store enforces immutability through PostgreSQL triggers:

1. **No Updates**: The `enforce_event_store_immutability` trigger prevents any UPDATE operations on `event_store` rows
2. **No Deletes**: The `enforce_event_store_no_deletes` trigger prevents any DELETE operations

These constraints ensure the audit trail remains intact and tamper-proof.

## Migrations

Database migrations are managed with Flyway and located in:
- **Production**: `src/main/resources/db/migration/V001__initial_event_store_schema.sql`
- **Tests**: `src/main/resources/db/migration/h2/V001__initial_event_store_schema.sql` (H2-compatible version)

### Running Migrations

Migrations run automatically on application startup via Quarkus Flyway integration:

```bash
# Development mode (with auto-migration)
mvn quarkus:dev

# Production build
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

Configuration in `application.properties`:
```properties
quarkus.flyway.migrate-at-start=true
quarkus.flyway.baseline-on-migrate=true
```

### Migration History

| Version | Date | Description |
|---------|------|-------------|
| V001 | 2026-02-13 | Initial event store schema with aggregates and event_store tables, immutability triggers, and indexes |

## Testing

Integration tests verify:
- Schema creation and structure
- Immutability constraints (no updates/deletes)
- Unique sequence numbering per aggregate
- Index existence and usage
- Performance of indexed stream reads

Run tests:
```bash
# All tests
mvn test

# Event store tests only
mvn test -Dtest=EventStoreSchemaTest
mvn test -Dtest=EventStorePerformanceTest
```

## Performance

The schema includes indexes optimized for common query patterns:

- `idx_event_store_aggregate`: Fast stream reads by aggregate_id
- `idx_event_store_aggregate_sequence`: Combined index for ordered stream reads
- `idx_event_store_type`: Fast queries by event type
- `idx_event_store_created`: Temporal range queries
- `idx_event_store_correlation`: Correlation tracking queries

Performance targets (validated in tests):
- Reading 100 events from a stream: < 100ms
- Event type queries: < 50ms
- Temporal range queries: < 50ms

## Retention Policy

Events are retained **indefinitely** in v0.1 for full audit trail.

See [docs/retention-policy.md](/docs/retention-policy.md) for:
- Detailed retention policy
- Storage growth estimates
- Backup requirements
- Emergency procedures
- Future archival considerations

## Usage Examples

### Appending Events

```java
// Create aggregate
UUID aggregateId = UUID.randomUUID();
stmt.executeUpdate(
    "INSERT INTO aggregates (id, aggregate_type, version, created_at, updated_at) " +
    "VALUES (?, 'Regatta', 1, now(), now())",
    aggregateId
);

// Append event
UUID eventId = UUID.randomUUID();
stmt.executeUpdate(
    "INSERT INTO event_store (id, aggregate_id, event_type, sequence_number, payload, created_at) " +
    "VALUES (?, ?, 'RegattaCreated', 1, ?::jsonb, now())",
    eventId, aggregateId, "{\"name\":\"Spring Head Race 2026\"}"
);
```

### Reading Event Streams

```java
// Read all events for an aggregate in order
PreparedStatement ps = conn.prepareStatement(
    "SELECT event_type, sequence_number, payload " +
    "FROM event_store " +
    "WHERE aggregate_id = ? " +
    "ORDER BY sequence_number ASC"
);
ps.setObject(1, aggregateId);
ResultSet rs = ps.executeQuery();
```

### Correlation Tracking

```java
// Find all events in a correlation chain
PreparedStatement ps = conn.prepareStatement(
    "SELECT aggregate_id, event_type, payload " +
    "FROM event_store " +
    "WHERE correlation_id = ? " +
    "ORDER BY created_at ASC"
);
ps.setObject(1, correlationId);
```

## Next Steps

After BC03-001 completion:
- BC03-002: Implement core aggregates (Regatta, Entry, etc.)
- BC03-003: Implement projection consumers
- BC03-004: Implement command handlers
- BC03-005: Implement domain CRUD workflows

## References

- [BC03 Implementation Plan](/pdd/implementation/bc03-core-regatta-management.md)
- [Database Schema Design](/pdd/design/database-schema.md)
- [Retention Policy](/docs/retention-policy.md)
- [GitHub Issue BC03-001](https://github.com/Davidsoff/regattadesk/issues/BC03-001)
