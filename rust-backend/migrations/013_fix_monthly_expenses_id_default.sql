-- Production's monthly_expenses.id was missing its gen_random_uuid() default
-- (the table predates that default being standardized, and CREATE TABLE IF
-- NOT EXISTS in schema.sql never retroactively applied it). save_expense's
-- INSERT never lists `id` explicitly, so every save failed with "null value
-- in column id violates not-null constraint" — reads worked fine since GET
-- is a plain SELECT, only writes were broken.
ALTER TABLE monthly_expenses ALTER COLUMN id SET DEFAULT gen_random_uuid();
