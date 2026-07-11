import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

// Public, unauthenticated — reached via a tap-to-pay link shared over
// WhatsApp (see AdminCreateMembershipPage's "Send Payment Request"). Wraps a
// upi://pay deep link in a real https:// URL so WhatsApp reliably linkifies
// it, then immediately hands off to whatever UPI app is installed. Stays
// visible (as a fallback) if nothing claims the upi:// intent — e.g. opened
// on a desktop browser or no UPI app installed.
export default function PayRedirectPage() {
    const [params] = useSearchParams()

    useEffect(() => {
        window.location.href = `upi://pay?${params.toString()}`
    }, [params])

    const amount = params.get('am')
    const payee  = params.get('pn') || 'Target Zone Library'

    return (
        <div className="min-h-screen flex items-center justify-center bg-primary-950 p-6 text-center">
            <div className="card p-8 max-w-sm">
                <p className="text-4xl mb-3">📱</p>
                <h1 className="text-white text-lg font-semibold mb-2">Opening your UPI app…</h1>
                <p className="text-primary-400 text-sm mb-1">
                    Pay {amount ? `₹${amount}` : ''} to {payee}
                </p>
                <p className="text-primary-500 text-xs mt-4">
                    Nothing happened? You need a UPI app (Google Pay, PhonePe, Paytm, etc.)
                    installed on this device — this link won't work on a desktop browser.
                </p>
            </div>
        </div>
    )
}
