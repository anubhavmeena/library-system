-- The UPI payee name shown/sent in payment-request deep links was hardcoded
-- as "Target Zone Library" in code, but GPay/PhonePe validate that name
-- against the actual bank-registered name for the configured VPA (upi_id)
-- and block the payment as a security risk on mismatch — a real issue when
-- upi_id is a personal (not merchant-registered) handle. Make it admin
-- configurable alongside upi_id so it can be set to match whatever name is
-- actually registered to the account.
ALTER TABLE app_settings
    ADD COLUMN IF NOT EXISTS upi_payee_name VARCHAR(255);
