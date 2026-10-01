// Mirrors rust-backend's services::membership::plan_end_date — keep in sync.
// A plan's durationDays that's a multiple of 30 means that many calendar
// months (30 → 1, 90 → 3); anything else is a plain day count.

export function planMonths(durationDays) {
    return durationDays > 0 && durationDays % 30 === 0 ? durationDays / 30 : null
}

// Last day of a term starting on `start` ('yyyy-MM-dd'): the day before the
// same date `months` later, or the target month's last day when that date
// doesn't exist (Jan 31 → Feb 28).
export function planEndDate(start, durationDays) {
    const [y, m, d] = start.split('-').map(Number)
    const months = planMonths(durationDays)
    let end
    if (months) {
        const lastDay = new Date(Date.UTC(y, m - 1 + months + 1, 0)).getUTCDate()
        end = d > lastDay
            ? new Date(Date.UTC(y, m - 1 + months, lastDay))
            : new Date(Date.UTC(y, m - 1 + months, d - 1))
    } else {
        end = new Date(Date.UTC(y, m - 1, d + durationDays - 1))
    }
    return end.toISOString().slice(0, 10)
}
