// API amounts arrive as decimal strings (rust_decimal serialization, e.g.
// "400.00") — Number() + toLocaleString drops insignificant trailing zeros
// while still showing a genuine fraction correctly (e.g. "150.50" -> "150.5").
export function formatNumber(value) {
    return Number(value ?? 0).toLocaleString('en-IN')
}

export function formatCurrency(value) {
    return `₹${formatNumber(value)}`
}

// 2.5% surcharge for clearing dues/pending amount via the payment gateway,
// rounded to the nearest whole rupee — mirrors
// rust-backend/src/services/membership.rs's with_convenience_fee(), used
// server-side as the actual amount charged. This is display-only (so the
// student sees the true amount before checkout even opens); the backend is
// the source of truth for what's actually charged.
export function withConvenienceFee(value) {
    return Math.round(Number(value ?? 0) * 1.025)
}
