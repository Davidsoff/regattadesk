-- V002: Operator Tokens Schema
-- 
-- This migration implements the operator token system for BC06:
-- - operator_tokens table for QR token lifecycle management
-- - Station-scoped authentication for operator workflows
-- - Token validity and revocation support
-- - Audit event integration
--
-- Token Lifecycle:
-- - Tokens are issued by staff with regatta and station scope
-- - Valid tokens grant operator access within validity window
-- - Tokens can be revoked via is_active flag
-- - Expired tokens (valid_until < now) are rejected
-- - Token validation checks: is_active=true AND now() BETWEEN valid_from AND valid_until

-- ========================================
-- OPERATOR_TOKENS TABLE
-- ========================================
-- Tracks QR tokens for operator station authentication
CREATE TABLE operator_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regatta_id UUID NOT NULL,
    block_id UUID,
    station VARCHAR(100) NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    pin CHAR(6),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_valid_until_after_from CHECK (valid_until > valid_from)
);

CREATE INDEX idx_operator_tokens_regatta ON operator_tokens(regatta_id);
CREATE INDEX idx_operator_tokens_block ON operator_tokens(block_id);
CREATE INDEX idx_operator_tokens_validity ON operator_tokens(valid_from, valid_until);
CREATE INDEX idx_operator_tokens_active ON operator_tokens(is_active);
CREATE INDEX idx_operator_tokens_station ON operator_tokens(regatta_id, station);

-- Trigger to automatically update updated_at timestamp
CREATE TRIGGER update_operator_tokens_updated_at
    BEFORE UPDATE ON operator_tokens
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE operator_tokens IS 'QR tokens for operator station authentication with validity windows and revocation support';
COMMENT ON COLUMN operator_tokens.token IS 'Unique token string encoded in QR code';
COMMENT ON COLUMN operator_tokens.pin IS 'Optional PIN for station handoff verification';
COMMENT ON COLUMN operator_tokens.valid_from IS 'Token validity start time (inclusive)';
COMMENT ON COLUMN operator_tokens.valid_until IS 'Token validity end time (exclusive)';
COMMENT ON COLUMN operator_tokens.is_active IS 'Active flag for revocation (false = revoked)';
