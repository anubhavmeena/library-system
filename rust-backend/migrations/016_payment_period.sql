-- Each payment records the term it paid for. Previously the admin payment
-- history showed the linked membership's start_date → end_date, but dues
-- clearances extend that same membership row month by month, so every
-- payment ended up showing the whole span since joining.
--
-- NULL for payments that don't buy a term (clearing a pending balance).
ALTER TABLE payments ADD COLUMN IF NOT EXISTS period_start DATE;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS period_end   DATE;

-- Best-effort backfill: per membership, the latest payment covers the final
-- calendar month up to end_date, the one before it the month before that,
-- and so on; the oldest payment starts at the membership's start_date. A
-- membership with a single payment gets its full start_date → end_date.
WITH ranked AS (
    SELECT p.id,
           m.start_date,
           m.end_date + 1 AS anchor,
           ROW_NUMBER() OVER (PARTITION BY p.membership_id ORDER BY p.created_at DESC) - 1 AS k,
           COUNT(*)     OVER (PARTITION BY p.membership_id) AS n
    FROM payments p
    JOIN memberships m ON m.id = p.membership_id
    WHERE p.status = 'SUCCESS'
      AND p.period_start IS NULL
      AND (p.amount > 0 OR COALESCE(p.pending_amount, 0) > 0)
),
periods AS (
    SELECT id,
           CASE WHEN k = n - 1 THEN start_date
                ELSE (anchor - make_interval(months => (k + 1)::int))::date END AS ps,
           (anchor - make_interval(months => k::int))::date - 1 AS pe
    FROM ranked
)
UPDATE payments p
SET period_start = pr.ps, period_end = pr.pe
FROM periods pr
WHERE p.id = pr.id AND pr.ps <= pr.pe;
