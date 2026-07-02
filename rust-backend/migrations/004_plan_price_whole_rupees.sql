-- Plan prices are always whole rupees; drop the decimal places.
ALTER TABLE membership_plans ALTER COLUMN price TYPE NUMERIC(10,0);
