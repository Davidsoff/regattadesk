-- Regatta entities read model for BC03-004
-- Reference: pdd/design/database-schema.md

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

CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    event_group_id UUID REFERENCES event_groups(id) ON DELETE SET NULL,
    category_id UUID NOT NULL REFERENCES categories(id),
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
