-- V002: Operator Tokens Schema (H2 Test Database)
-- 
-- H2-compatible version of the operator tokens migration

-- ========================================
-- OPERATOR_TOKENS TABLE
-- ========================================
CREATE TABLE operator_tokens (
    id UUID PRIMARY KEY DEFAULT random_uuid(),
    regatta_id UUID NOT NULL,
    block_id UUID,
    station VARCHAR(100) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    pin VARCHAR(10),
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT current_timestamp(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT current_timestamp(),
    CONSTRAINT chk_valid_until_after_from CHECK (valid_until > valid_from)
);

CREATE INDEX idx_operator_tokens_regatta ON operator_tokens(regatta_id);
CREATE INDEX idx_operator_tokens_block ON operator_tokens(block_id);
CREATE INDEX idx_operator_tokens_token ON operator_tokens(token);
CREATE INDEX idx_operator_tokens_validity ON operator_tokens(valid_from, valid_until);
CREATE INDEX idx_operator_tokens_active ON operator_tokens(is_active);
CREATE INDEX idx_operator_tokens_station ON operator_tokens(regatta_id, station);

-- H2 Trigger for updated_at
CREATE TRIGGER update_operator_tokens_updated_at
    BEFORE UPDATE ON operator_tokens
    FOR EACH ROW CALL "com.regattadesk.eventstore.UpdateTimestampTrigger";
