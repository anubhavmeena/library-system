// Builds a UPI payment deep link. Amounts are formatted to 2 decimals per
// the UPI spec's `am` param; `pn`/`tn` are URI-encoded since they can
// contain spaces. `scheme` defaults to the generic `upi://pay` (any UPI
// app), but each major app also registers its own scheme — see
// UPI_APP_SCHEMES below — which is worth targeting explicitly: a link
// tapped inside WhatsApp's in-app browser that fires `upi://pay` via a
// script-triggered redirect is often silently absorbed by WhatsApp's own
// UPI payments feature instead of handing off to the user's actual UPI
// app. Real per-app buttons (genuine user-gesture link taps, not a JS
// redirect) route reliably instead.
export function buildUpiDeepLink({ vpa, payeeName, amount, note, scheme = 'upi://pay' }) {
    const params = new URLSearchParams({
        pa: vpa,
        pn: payeeName,
        am: Number(amount).toFixed(2),
        cu: 'INR',
        tn: note,
    })
    return `${scheme}?${params.toString()}`
}

export const UPI_APPS = [
    { key: 'phonepe', label: 'PhonePe',    scheme: 'phonepe://pay' },
    { key: 'gpay',    label: 'Google Pay', scheme: 'tez://upi/pay' },
    { key: 'paytm',   label: 'Paytm',      scheme: 'paytmmp://pay' },
    { key: 'other',   label: 'Other UPI App', scheme: 'upi://pay' },
]
