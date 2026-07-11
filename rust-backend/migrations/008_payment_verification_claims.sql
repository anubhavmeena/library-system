-- Student-submitted UPI payment claims (screenshot + self-reported amount) for
-- the Pending Fee / Grace Dues reminder pay links. Reviewed manually by an
-- admin since a raw personal-VPA UPI payment has no gateway callback to
-- verify against — see rust-backend/CLAUDE.md and the payment-mode-split
-- feature for context on CASH/UPI-QR/ONLINE-PG.
CREATE TABLE payment_verification_claims (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL,
    claim_type     VARCHAR(20)  NOT NULL CHECK (claim_type IN ('DUES', 'PENDING_FEE')),
    membership_id  UUID,
    amount_claimed NUMERIC(10,2) NOT NULL,
    screenshot_url VARCHAR(500) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    reviewed_at    TIMESTAMP,
    reviewed_by    UUID
);

CREATE INDEX idx_payment_claims_status ON payment_verification_claims(status);
CREATE INDEX idx_payment_claims_user_type_status ON payment_verification_claims(user_id, claim_type, status);
