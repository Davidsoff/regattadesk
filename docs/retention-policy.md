# Event Store Retention Policy

Version: v0.1  
Last Updated: 2026-02-13  
Owner: RegattaDesk Platform Team

## Overview

This document defines the retention policy for the RegattaDesk event store, which serves as the immutable audit trail for all domain events.

## Policy Statement

**For v0.1: Events are retained indefinitely with no automatic pruning or archival.**

## Rationale

The event store serves multiple critical functions:
1. **Full Audit Trail**: Complete history of all regatta management actions for compliance and dispute resolution
2. **Event Replay**: Ability to reconstruct aggregate state at any point in time
3. **Projection Rebuilding**: Source of truth for rebuilding read models and projections
4. **Investigation Support**: Historical evidence for jury investigations and adjudication

Given these requirements and the relatively low volume expected in v0.1 deployments (single-regatta or small organization scale), indefinite retention provides the safest operational posture.

## Technical Implementation

### Schema Constraints
The event store schema enforces immutability through:
- **No Updates**: Database trigger prevents UPDATE operations on `event_store` table
- **No Deletes**: Database trigger prevents DELETE operations on `event_store` table
- **Append-Only**: Events can only be inserted, never modified or removed

See migration `V001__initial_event_store_schema.sql` for implementation details.

### Storage Considerations

#### Estimated Growth
Assuming moderate usage:
- Average event size: ~2 KB (including JSONB payload and metadata)
- Events per regatta per year: ~10,000 (entries, withdrawals, timing, investigations, results)
- Annual storage growth per regatta: ~20 MB/year

For a single regatta organization running 5 regattas per year:
- Annual growth: ~100 MB/year
- 10-year projection: ~1 GB

Modern PostgreSQL deployments can easily handle this scale without special considerations.

#### Monitoring
Operations should monitor:
- `event_store` table size (via `pg_total_relation_size('event_store')`)
- Row count growth rate
- Query performance metrics for aggregate stream reads

Alert thresholds (recommended):
- Table size exceeds 10 GB: Review retention policy
- Read queries exceed 500ms p99: Review indexing strategy

## Operational Procedures

### Backup Requirements
- Full database backups must include `event_store` and `aggregates` tables
- Backup retention must match or exceed event retention (indefinite in v0.1)
- Point-in-time recovery (PITR) capability recommended for disaster recovery

### Testing Restoration
- Regularly test event store restoration from backups
- Verify projection rebuilding from restored events
- Document restoration time objectives (RTO) and recovery point objectives (RPO)

### Emergency Procedures
In the rare case of storage constraints or performance degradation:

1. **Do NOT delete events** - this breaks audit trail and regulatory compliance
2. **Do NOT modify events** - this corrupts aggregate integrity
3. **Acceptable actions**:
   - Migrate older events to cold storage (offline PostgreSQL instance)
   - Archive events by date range to separate tables with same schema
   - Optimize indexes and query patterns
   - Scale storage capacity

### Manual Archival (If Required)
If manual archival becomes necessary (not planned for v0.1):

```sql
-- Example archival query (DO NOT RUN without approval)
-- This moves events older than 5 years to archive table

-- 1. Create archive table with identical schema
CREATE TABLE event_store_archive (LIKE event_store INCLUDING ALL);

-- 2. Copy old events (read-only operation)
INSERT INTO event_store_archive 
SELECT * FROM event_store 
WHERE created_at < now() - INTERVAL '5 years';

-- 3. Verification step (manual review required)
-- Verify row counts match and no data loss

-- 4. Remove from active table (requires approval)
-- DELETE FROM event_store 
-- WHERE created_at < now() - INTERVAL '5 years';
```

**IMPORTANT**: Archival operations require:
- Written approval from platform owner
- Backup verification
- Impact assessment on active projections
- Documented restoration procedure

## Future Considerations (Post-v0.1)

Potential enhancements for future versions:

1. **Tiered Storage**: Move events older than N years to cold storage with slower access times
2. **Selective Archival**: Archive events for closed/archived regattas only
3. **Snapshot Strategy**: Implement aggregate snapshots to reduce replay cost for long-lived aggregates
4. **Compression**: Enable PostgreSQL TOAST compression for JSONB payloads
5. **Partitioning**: Partition `event_store` by date range for improved query performance

## Compliance and Audit

### Regulatory Requirements
Organizations using RegattaDesk must assess their own regulatory requirements for audit log retention:
- Financial records: Typically 7-10 years
- Competition records: Varies by sport federation (often 5+ years)
- GDPR considerations: Right to erasure vs. legitimate interest in audit trail

### Audit Log Access
The event store provides full audit trail access via:
- `event_store` table queries (requires database access)
- Future API endpoints for authorized audit log retrieval (post-v0.1)
- Backup and archive records

### Immutability Verification
To verify event store integrity:

```sql
-- Check for unexpected gaps in sequence numbers
SELECT 
    aggregate_id,
    COUNT(*) as event_count,
    MAX(sequence_number) as max_sequence,
    MIN(sequence_number) as min_sequence
FROM event_store
GROUP BY aggregate_id
HAVING COUNT(*) != (MAX(sequence_number) - MIN(sequence_number) + 1);

-- Check for any events with future timestamps (clock skew detection)
SELECT COUNT(*) 
FROM event_store 
WHERE created_at > now() + INTERVAL '1 minute';
```

## References

- Database Schema: `pdd/design/database-schema.md`
- Migration Script: `apps/backend/src/main/resources/db/migration/V001__initial_event_store_schema.sql`
- Implementation Plan: `pdd/implementation/plan.md` (Step 3)
- BC03 Specification: `pdd/implementation/bc03-core-regatta-management.md`

## Revision History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| v0.1 | 2026-02-13 | Initial policy defining indefinite retention | RegattaDesk Platform Team |
