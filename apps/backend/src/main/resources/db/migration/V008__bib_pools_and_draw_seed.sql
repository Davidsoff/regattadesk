-- Add bib pools table and draw_seed tracking to regattas
-- Reference: pdd/design/database-schema.md
-- BC04-003: Block scheduling, bib pool allocation, and draw publication

-- Add draw_seed column to regattas table for draw reproducibility
ALTER TABLE regattas ADD COLUMN draw_seed BIGINT;

CREATE INDEX idx_regattas_draw_seed ON regattas(draw_seed);

COMMENT ON COLUMN regattas.draw_seed IS 'Random seed used for draw generation to ensure reproducibility';

-- Bib pools table for allocation configuration
CREATE TABLE bib_pools (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL REFERENCES regattas(id) ON DELETE CASCADE,
    block_id UUID REFERENCES blocks(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    allocation_mode VARCHAR(20) NOT NULL DEFAULT 'range',
    start_bib INTEGER,
    end_bib INTEGER,
    bib_numbers INTEGER[],
    priority INTEGER NOT NULL DEFAULT 0,
    is_overflow BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
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
            AND cardinality(bib_numbers) > 0)
    )
);

CREATE INDEX idx_bib_pools_regatta ON bib_pools(regatta_id);
CREATE INDEX idx_bib_pools_block ON bib_pools(block_id);
CREATE INDEX idx_bib_pools_priority ON bib_pools(priority);
CREATE INDEX idx_bib_pools_overflow ON bib_pools(is_overflow);
CREATE INDEX idx_bib_pools_numbers ON bib_pools USING GIN (bib_numbers);

COMMENT ON TABLE bib_pools IS 'Bib number allocation pools for blocks and overflow';
COMMENT ON COLUMN bib_pools.allocation_mode IS 'range: uses start_bib/end_bib, explicit_list: uses bib_numbers array';
COMMENT ON COLUMN bib_pools.is_overflow IS 'True for regatta-level overflow pool (no block_id)';
COMMENT ON COLUMN bib_pools.priority IS 'Lower priority values are allocated first';

-- Triggers for updated_at
CREATE TRIGGER update_bib_pools_updated_at
    BEFORE UPDATE ON bib_pools
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
