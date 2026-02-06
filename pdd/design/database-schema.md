Version: v2
Last Updated: 2026-02-06
Author: RegattaDesk Team

# RegattaDesk v0.1 Database Schema

This document contains the complete database schema design for RegattaDesk v0.1.

## Core Tables

### athletes table
```sql
CREATE TABLE athletes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(20) NOT NULL CHECK (gender IN ('M', 'F', 'X')),
    club_id UUID REFERENCES clubs(id) ON DELETE SET NULL,
    federation_id VARCHAR(20) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(federation_id)
);

CREATE INDEX idx_athletes_club ON athletes(club_id);
CREATE INDEX idx_athletes_federation_id ON athletes(federation_id);
CREATE INDEX idx_athletes_first_name ON athletes(first_name);
CREATE INDEX idx_athletes_last_name ON athletes(last_name);
CREATE INDEX idx_athletes_name_search ON athletes(first_name, last_name);
```

### crews table
```sql
CREATE TABLE crews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name VARCHAR(255) NOT NULL,
    is_composite BOOLEAN NOT NULL DEFAULT FALSE,
    club_id UUID REFERENCES clubs(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_crews_display_name ON crews(display_name);
CREATE INDEX idx_crews_club ON crews(club_id);
CREATE INDEX idx_crews_composite ON crews(is_composite);

-- Many-to-many relationship for crew athletes
CREATE TABLE crew_athletes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    crew_id UUID NOT NULL REFERENCES crews(id) ON DELETE CASCADE,
    athlete_id UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
    seat_position INTEGER NOT NULL CHECK (seat_position >= 1),
    UNIQUE(crew_id, athlete_id),
    UNIQUE(crew_id, seat_position)
);

CREATE INDEX idx_crew_athletes_crew ON crew_athletes(crew_id);
CREATE INDEX idx_crew_athletes_athlete ON crew_athletes(athlete_id);
```

### clubs table
```sql
CREATE TABLE clubs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    short_name VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_clubs_name ON clubs(name);
CREATE INDEX idx_clubs_short_name ON clubs(short_name);
```

### club_billing table
```sql
CREATE TABLE club_billing (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id UUID NOT NULL UNIQUE REFERENCES clubs(id) ON DELETE CASCADE,
    contact_name VARCHAR(255),
    contact_email VARCHAR(255),
    address TEXT,
    postal_code VARCHAR(20),
    city VARCHAR(100),
    country VARCHAR(100) DEFAULT 'NL',
    vat_number VARCHAR(50),
    reference VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_club_billing_club ON club_billing(club_id);
```

### categories table
```sql
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    min_age INTEGER,
    max_age INTEGER,
    gender VARCHAR(20) CHECK (gender IN ('M', 'F', 'X', 'ANY')),
    skill_level VARCHAR(50),
    is_global BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_categories_global ON categories(is_global);
CREATE INDEX idx_categories_gender ON categories(gender);
```

### boat_types table
```sql
CREATE TABLE boat_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    rowers INTEGER NOT NULL,
    coxswain BOOLEAN NOT NULL DEFAULT FALSE,
    sculling BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_boat_types_code ON boat_types(code);
```

### rulesets table
```sql
CREATE TABLE rulesets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    description TEXT,
    age_calculation_type VARCHAR(50) NOT NULL DEFAULT 'actual_at_start',
    is_global BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rulesets_global ON rulesets(is_global);
CREATE INDEX idx_rulesets_version ON rulesets(version);
```

### regattas table
```sql
CREATE TABLE regattas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    time_zone VARCHAR(50) NOT NULL DEFAULT 'Europe/Amsterdam',
    status VARCHAR(50) NOT NULL DEFAULT 'draft',
    ruleset_id UUID REFERENCES rulesets(id),
    entry_fee DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    default_penalty_seconds INTEGER NOT NULL DEFAULT 30,
    allow_custom_penalty_seconds BOOLEAN NOT NULL DEFAULT FALSE,
    draw_revision INTEGER NOT NULL DEFAULT 0,
    results_revision INTEGER NOT NULL DEFAULT 0,
    regatta_end_at TIMESTAMPTZ,
    retention_days INTEGER NOT NULL DEFAULT 14,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status IN ('draft', 'published', 'archived', 'deleted'))
);

CREATE INDEX idx_regattas_status ON regattas(status);
CREATE INDEX idx_regattas_ruleset ON regattas(ruleset_id);
CREATE INDEX idx_regattas_revisions ON regattas(draw_revision, results_revision);
```

### event_groups table
```sql
CREATE TABLE event_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_event_groups_regatta ON event_groups(regatta_id);
CREATE INDEX idx_event_groups_order ON event_groups(display_order);
```

### events table
```sql
CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    event_group_id UUID REFERENCES event_groups(id) ON DELETE SET NULL,
    category_id UUID REFERENCES categories(id),
    boat_type_id UUID NOT NULL REFERENCES boat_types(id),
    name VARCHAR(255) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_regatta ON events(regatta_id);
CREATE INDEX idx_events_group ON events(event_group_id);
CREATE INDEX idx_events_category ON events(category_id);
CREATE INDEX idx_events_boat_type ON events(boat_type_id);
CREATE INDEX idx_events_order ON events(display_order);
```

### blocks table
```sql
CREATE TABLE blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    event_interval_seconds INTEGER NOT NULL DEFAULT 300,
    crew_interval_seconds INTEGER NOT NULL DEFAULT 60,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_blocks_regatta ON blocks(regatta_id);
CREATE INDEX idx_blocks_start_time ON blocks(start_time);
CREATE INDEX idx_blocks_order ON blocks(display_order);
```

### bib_pools table
```sql
CREATE TABLE bib_pools (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    block_id UUID REFERENCES blocks(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    start_bib INTEGER NOT NULL,
    end_bib INTEGER NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    is_overflow BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (start_bib <= end_bib)
);

CREATE INDEX idx_bib_pools_regatta ON bib_pools(regatta_id);
CREATE INDEX idx_bib_pools_block ON bib_pools(block_id);
CREATE INDEX idx_bib_pools_priority ON bib_pools(priority);
CREATE INDEX idx_bib_pools_overflow ON bib_pools(is_overflow);
```

### entries table
```sql
CREATE TABLE entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    block_id UUID NOT NULL REFERENCES blocks(id) ON DELETE CASCADE,
    crew_id UUID NOT NULL REFERENCES crews(id) ON DELETE RESTRICT,
    bib INTEGER,
    billing_club_id UUID REFERENCES clubs(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'entered',
    payment_status VARCHAR(50) NOT NULL DEFAULT 'unpaid',
    paid_at TIMESTAMPTZ,
    paid_by VARCHAR(255),
    payment_reference VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(regatta_id, bib),
    CHECK (status IN ('entered', 'withdrawn_before_draw', 'withdrawn_after_draw', 'dns', 'dnf', 'excluded', 'dsq')),
    CHECK (payment_status IN ('unpaid', 'paid'))
);

CREATE INDEX idx_entries_regatta ON entries(regatta_id);
CREATE INDEX idx_entries_event ON entries(event_id);
CREATE INDEX idx_entries_block ON entries(block_id);
CREATE INDEX idx_entries_crew ON entries(crew_id);
CREATE INDEX idx_entries_status ON entries(status);
CREATE INDEX idx_entries_payment_status ON entries(payment_status);
CREATE INDEX idx_entries_billing_club ON entries(billing_club_id);
```

### investigations table
```sql
CREATE TABLE investigations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    entry_id UUID NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    outcome VARCHAR(50),
    penalty_seconds INTEGER DEFAULT 0,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (outcome IN ('no_action', 'penalty', 'excluded', 'dsq'))
);

CREATE INDEX idx_investigations_regatta ON investigations(regatta_id);
CREATE INDEX idx_investigations_entry ON investigations(entry_id);
CREATE INDEX idx_investigations_outcome ON investigations(outcome);
CREATE INDEX idx_investigations_closed ON investigations(closed_at);
```

### invoices table
```sql
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    invoice_number VARCHAR(100) NOT NULL UNIQUE,
    total_amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    status VARCHAR(50) NOT NULL DEFAULT 'draft',
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status IN ('draft', 'sent', 'paid', 'cancelled'))
);

CREATE INDEX idx_invoices_regatta ON invoices(regatta_id);
CREATE INDEX idx_invoices_club ON invoices(club_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_number ON invoices(invoice_number);
```

### invoice_entries table
```sql
CREATE TABLE invoice_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    entry_id UUID NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
    amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoice_entries_invoice ON invoice_entries(invoice_id);
CREATE INDEX idx_invoice_entries_entry ON invoice_entries(entry_id);
```

### operator_tokens table
```sql
CREATE TABLE operator_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    block_id UUID REFERENCES blocks(id) ON DELETE CASCADE,
    station VARCHAR(100) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    pin VARCHAR(10),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_operator_tokens_regatta ON operator_tokens(regatta_id);
CREATE INDEX idx_operator_tokens_block ON operator_tokens(block_id);
CREATE INDEX idx_operator_tokens_token ON operator_tokens(token);
CREATE INDEX idx_operator_tokens_validity ON operator_tokens(valid_from, valid_until);
CREATE INDEX idx_operator_tokens_active ON operator_tokens(is_active);
```

### capture_sessions table
```sql
CREATE TABLE capture_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    block_id UUID NOT NULL REFERENCES blocks(id) ON DELETE CASCADE,
    station VARCHAR(100) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    session_type VARCHAR(50) NOT NULL,
    server_time_at_start TIMESTAMPTZ NOT NULL,
    device_monotonic_offset BIGINT,
    fps INTEGER NOT NULL,
    is_synced BOOLEAN NOT NULL DEFAULT TRUE,
    drift_exceeded_threshold BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (session_type IN ('start', 'finish'))
);

CREATE INDEX idx_capture_sessions_regatta ON capture_sessions(regatta_id);
CREATE INDEX idx_capture_sessions_block ON capture_sessions(block_id);
CREATE INDEX idx_capture_sessions_device ON capture_sessions(device_id);
CREATE INDEX idx_capture_sessions_type ON capture_sessions(session_type);
```

### timing_markers table
```sql
CREATE TABLE timing_markers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    capture_session_id UUID NOT NULL REFERENCES capture_sessions(id) ON DELETE CASCADE,
    entry_id UUID REFERENCES entries(id) ON DELETE SET NULL,
    frame_offset BIGINT NOT NULL,
    timestamp_ms BIGINT NOT NULL,
    is_linked BOOLEAN NOT NULL DEFAULT FALSE,
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    tile_x INTEGER,
    tile_y INTEGER,
    tile_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_timing_markers_session ON timing_markers(capture_session_id);
CREATE INDEX idx_timing_markers_entry ON timing_markers(entry_id);
CREATE INDEX idx_timing_markers_linked ON timing_markers(is_linked);
CREATE INDEX idx_timing_markers_approved ON timing_markers(is_approved);
CREATE INDEX idx_timing_markers_frame ON timing_markers(frame_offset);
```

## Event Sourcing Tables

### Aggregates Table
```sql
CREATE TABLE aggregates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_aggregates_type ON aggregates(aggregate_type);
CREATE INDEX idx_aggregates_version ON aggregates(version);
```

### Event Store Schema
```sql
CREATE TABLE event_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL REFERENCES aggregates(id),
    event_type VARCHAR(100) NOT NULL,
    sequence_number BIGINT NOT NULL,
    payload JSONB NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    correlation_id UUID,
    causation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(aggregate_id, sequence_number)
);

CREATE INDEX idx_event_store_aggregate ON event_store(aggregate_id);
CREATE INDEX idx_event_store_type ON event_store(event_type);
CREATE INDEX idx_event_store_created ON event_store(created_at);
CREATE INDEX idx_event_store_correlation ON event_store(correlation_id);
```

## Projection Tables

### public_regatta_results (keyed by regatta_id, draw_revision, results_revision)
```sql
CREATE TABLE public_regatta_results (
    regatta_id UUID NOT NULL,
    draw_revision INTEGER NOT NULL DEFAULT 0,
    results_revision INTEGER NOT NULL DEFAULT 0,
    entry_id UUID NOT NULL,
    event_id UUID NOT NULL,
    bib INTEGER,
    crew_name VARCHAR(255) NOT NULL,
    club_name VARCHAR(255),
    elapsed_time_ms INTEGER,
    penalties_ms INTEGER DEFAULT 0,
    rank INTEGER,
    status VARCHAR(50) NOT NULL DEFAULT 'entered',
    is_provisional BOOLEAN DEFAULT TRUE,
    is_edited BOOLEAN DEFAULT FALSE,
    is_official BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (regatta_id, entry_id)
);

CREATE INDEX idx_results_regatta_rev ON public_regatta_results(regatta_id, draw_revision, results_revision);
CREATE INDEX idx_results_event ON public_regatta_results(event_id);
CREATE INDEX idx_results_status ON public_regatta_results(status);
CREATE INDEX idx_results_rank ON public_regatta_results(rank);
```

### public_regatta_draw (keyed by regatta_id, draw_revision)
```sql
CREATE TABLE public_regatta_draw (
    regatta_id UUID NOT NULL,
    draw_revision INTEGER NOT NULL DEFAULT 0,
    entry_id UUID NOT NULL,
    event_id UUID NOT NULL,
    bib INTEGER,
    scheduled_start_time TIMESTAMPTZ,
    lane INTEGER,
    crew_name VARCHAR(255) NOT NULL,
    club_name VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'entered',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (regatta_id, entry_id)
);

CREATE INDEX idx_draw_regatta_rev ON public_regatta_draw(regatta_id, draw_revision);
CREATE INDEX idx_draw_event ON public_regatta_draw(event_id);
CREATE INDEX idx_draw_scheduled ON public_regatta_draw(scheduled_start_time);
```

## Utility Functions

### updated_at trigger function
```sql
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';
```

### Apply updated_at triggers to all relevant tables
```sql
CREATE TRIGGER update_athletes_updated_at BEFORE UPDATE ON athletes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_crews_updated_at BEFORE UPDATE ON crews
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_clubs_updated_at BEFORE UPDATE ON clubs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_club_billing_updated_at BEFORE UPDATE ON club_billing
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_categories_updated_at BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_boat_types_updated_at BEFORE UPDATE ON boat_types
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_rulesets_updated_at BEFORE UPDATE ON rulesets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_regattas_updated_at BEFORE UPDATE ON regattas
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_event_groups_updated_at BEFORE UPDATE ON event_groups
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_events_updated_at BEFORE UPDATE ON events
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_blocks_updated_at BEFORE UPDATE ON blocks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bib_pools_updated_at BEFORE UPDATE ON bib_pools
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_entries_updated_at BEFORE UPDATE ON entries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_investigations_updated_at BEFORE UPDATE ON investigations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_invoices_updated_at BEFORE UPDATE ON invoices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_operator_tokens_updated_at BEFORE UPDATE ON operator_tokens
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_capture_sessions_updated_at BEFORE UPDATE ON capture_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_timing_markers_updated_at BEFORE UPDATE ON timing_markers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_aggregates_updated_at BEFORE UPDATE ON aggregates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

## Schema Review Summary

### Improvements Made

1. **Added Missing Core Tables:**
   - `categories` - demographic grouping (age/gender/skill level)
   - `boat_types` - shell/rigging type definitions
   - `rulesets` - versioned rulesets for regattas
   - `regattas` - main regatta entity
   - `event_groups` - optional grouping of events
   - `events` - category × boat type pairing
   - `blocks` - operational scheduling unit
   - `bib_pools` - bib pool configuration
   - `entries` - regatta-scoped participation
   - `investigations` - jury investigations
   - `invoices` - payment invoices
   - `invoice_entries` - invoice line items
   - `operator_tokens` - QR tokens for operators
   - `capture_sessions` - timing capture sessions
   - `timing_markers` - line-scan markers

2. **Enhanced Existing Tables:**
   - `athletes`: Added `ON DELETE SET NULL` for `club_id`, added composite index for name search
   - `crews`: Added `club_id` field and indexes, added `CHECK` constraint for seat positions
   - `club_billing`: Added `UNIQUE` constraint on `club_id` (1:1 relationship)
   - `aggregates`: Added `version`, `created_at`, `updated_at` fields
   - `event_store`: Added `correlation_id`, `causation_id` for event tracing
   - `public_regatta_results`: Added `updated_at` and `rank` index
   - `public_regatta_draw`: Added `updated_at` field

3. **Added Utility Functions:**
   - `update_updated_at_column()` trigger function
   - Applied triggers to all tables with `updated_at` fields

4. **Added Comprehensive Indexes:**
   - Foreign key indexes for all relationships
   - Composite indexes for common query patterns
   - Status and type indexes for filtering

5. **Added Constraints:**
   - `CHECK` constraints for enum-like fields
   - `UNIQUE` constraints where appropriate
   - Proper `ON DELETE` behavior for foreign keys

### Design Decisions

1. **UUID Primary Keys**: Used for all tables for distributed system compatibility
2. **TIMESTAMPTZ**: All timestamps stored in UTC with timezone awareness
3. **JSONB**: Used for event store payload and metadata for flexibility
4. **ON DELETE Behavior**: 
   - `CASCADE` for child entities that shouldn't exist without parent
   - `SET NULL` for optional references
   - `RESTRICT` for critical relationships
5. **Revision Tracking**: `draw_revision` and `results_revision` in regattas for cache invalidation
6. **Status Enums**: Used `CHECK` constraints instead of ENUM for easier schema evolution
