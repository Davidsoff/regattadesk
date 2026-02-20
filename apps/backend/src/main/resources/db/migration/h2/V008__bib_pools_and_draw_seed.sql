-- Add bib pools table and draw_seed tracking to regattas (H2 compatible)
-- Reference: pdd/design/database-schema.md
-- BC04-003: Block scheduling, bib pool allocation, and draw publication

-- Add draw_seed column to regattas table for draw reproducibility
ALTER TABLE regattas ADD COLUMN IF NOT EXISTS draw_seed BIGINT;

CREATE INDEX IF NOT EXISTS idx_regattas_draw_seed ON regattas(draw_seed);

-- Bib pools table for allocation configuration
CREATE TABLE IF NOT EXISTS bib_pools (
    id UUID PRIMARY KEY,
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    block_id UUID REFERENCES blocks(id) ON DELETE CASCADE,
    name CHARACTER VARYING(255) NOT NULL,
    allocation_mode CHARACTER VARYING(20) NOT NULL DEFAULT 'range',
    start_bib INTEGER,
    end_bib INTEGER,
    bib_numbers INTEGER ARRAY,
    priority INTEGER NOT NULL DEFAULT 0,
    is_overflow BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CHECK (allocation_mode IN ('range', 'explicit_list')),
    CHECK (
        (allocation_mode = 'range'
            AND start_bib IS NOT NULL
            AND end_bib IS NOT NULL
            AND start_bib <= end_bib
            AND bib_numbers IS NULL)
        OR
        (allocation_mode = 'explicit_list'
            AND start_bib IS NULL
            AND end_bib IS NULL
            AND bib_numbers IS NOT NULL
            AND CARDINALITY(bib_numbers) > 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_bib_pools_regatta ON bib_pools(regatta_id);
CREATE INDEX IF NOT EXISTS idx_bib_pools_block ON bib_pools(block_id);
CREATE INDEX IF NOT EXISTS idx_bib_pools_priority ON bib_pools(priority);
CREATE INDEX IF NOT EXISTS idx_bib_pools_overflow ON bib_pools(is_overflow);
