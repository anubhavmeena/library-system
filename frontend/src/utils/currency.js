// API amounts arrive as decimal strings (rust_decimal serialization, e.g.
// "400.00") — Number() + toLocaleString drops insignificant trailing zeros
// while still showing a genuine fraction correctly (e.g. "150.50" -> "150.5").
export function formatNumber(value) {
    return Number(value ?? 0).toLocaleString('en-IN')
}

export function formatCurrency(value) {
    return `₹${formatNumber(value)}`
}
