-- UPI VPA (Virtual Payment Address) used to generate the self-declared UPI QR
-- code / payment-request link on the admin Create Membership screen. Nullable
-- — both new features are guarded client-side until an admin sets this.
ALTER TABLE app_settings
    ADD COLUMN IF NOT EXISTS upi_id VARCHAR(255);
