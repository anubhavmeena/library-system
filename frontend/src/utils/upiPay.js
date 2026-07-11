// Builds the generic upi://pay deep link (any installed UPI app can handle
// it — used for the QR code, where there's no "which app" ambiguity since
// the scanning app itself resolves the intent).
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

// Android "intent://" URL explicitly targeting one app's package via the
// standard `upi` scheme/action — this is the officially-supported way to
// target a specific UPI app from a web page (matches Razorpay/Cashfree/
// Paytm Business's own integration docs). Each app's own proprietary
// scheme (tez://, paytmmp://, etc.) is undocumented and changes across app
// versions — tested live and found only PhonePe's phonepe:// scheme still
// worked, Google Pay and Paytm's did not. Falls back to the plain
// upi://pay link (OS-level chooser) when no package is given.
export function buildUpiIntentLink({ vpa, payeeName, amount, note, androidPackage }) {
    const params = new URLSearchParams({
        pa: vpa,
        pn: payeeName,
        am: Number(amount).toFixed(2),
        cu: 'INR',
        tn: note,
    })
    if (!androidPackage) return `upi://pay?${params.toString()}`
    return `intent://pay?${params.toString()}#Intent;scheme=upi;package=${androidPackage};end`
}

export const UPI_APPS = [
    { key: 'phonepe', label: 'PhonePe',    androidPackage: 'com.phonepe.app' },
    { key: 'gpay',    label: 'Google Pay', androidPackage: 'com.google.android.apps.nbu.paisa.user' },
    { key: 'paytm',   label: 'Paytm',      androidPackage: 'net.one97.paytm' },
    { key: 'other',   label: 'Other UPI App', androidPackage: null },
]
