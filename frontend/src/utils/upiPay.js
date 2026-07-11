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

// The https:// redirect link shared over WhatsApp — same params, decoded by
// PayRedirectPage back into the upi:// deep link above. A plain upi:// link
// pasted into a WhatsApp message isn't reliably tappable (WhatsApp only
// auto-linkifies http(s) URLs), so this wraps it in a real https link.
export function buildPayRedirectLink({ vpa, payeeName, amount, note }) {
    const params = new URLSearchParams({
        pa: vpa,
        pn: payeeName,
        am: Number(amount).toFixed(2),
        cu: 'INR',
        tn: note,
    })
    return `${window.location.origin}/pay?${params.toString()}`
}
