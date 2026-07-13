-- Coupon/discount codes for the primary booking flow (see rust-backend/CLAUDE.md
-- for scope: percentage-only, no expiry/usage limits — a coupon is live until
-- its own is_active flag or the global app_settings.coupons_enabled toggle is
-- flipped off). Discount applies to plan price only; convenience fee is added
-- after the discount, in create_order.
CREATE TABLE coupons (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(30)  UNIQUE NOT NULL,
    discount_percent SMALLINT     NOT NULL CHECK (discount_percent BETWEEN 1 AND 100),
    is_active        BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

ALTER TABLE app_settings
    ADD COLUMN IF NOT EXISTS coupons_enabled BOOLEAN NOT NULL DEFAULT true;

-- Staging columns set at create_order time (mirrors gateway_order_id/checkout_amount),
-- read back and copied onto the payments row once verify_payment confirms SUCCESS.
ALTER TABLE memberships
    ADD COLUMN IF NOT EXISTS coupon_code     VARCHAR(30),
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(10,2);

-- Permanent audit trail of what discount (if any) applied to a successful payment.
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS coupon_code     VARCHAR(30),
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(10,2);
