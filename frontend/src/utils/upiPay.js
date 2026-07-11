// Builds the upi://pay deep link. Amounts are formatted to 2 decimals per
// the UPI spec's `am` param; `pn`/`tn` are URI-encoded since they can
// contain spaces.
export function buildUpiDeepLink({ vpa, payeeName, amount, note }) {
    const params = new URLSearchParams({
        pa: vpa,
        pn: payeeName,
        am: Number(amount).toFixed(2),
        cu: 'INR',
        tn: note,
    })
    return `upi://pay?${params.toString()}`
}

