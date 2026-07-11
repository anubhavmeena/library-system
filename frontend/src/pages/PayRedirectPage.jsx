import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import toast from 'react-hot-toast'
import api from '../services/api'
import { buildUpiIntentLink, UPI_APPS } from '../utils/upiPay'

// Public, unauthenticated — reached via a tap-to-pay link shared over
// WhatsApp (see AdminCreateMembershipPage's "Send Payment Request", and the
// Pending Fee / Grace Dues reminders). The link only carries a short opaque
// `id` (not the UPI params directly — those are resolved server-side via
// GET /api/pay/:id, keeping the shared WhatsApp link short).
//
// Deliberately does NOT auto-redirect via `window.location.href` on load —
// tested live and found that a script-triggered upi://pay redirect fired
// inside WhatsApp's in-app browser gets silently absorbed by WhatsApp's own
// UPI payments feature instead of handing off to the tapped app (PhonePe,
// GPay, etc). Real per-app links the student taps themselves (a genuine
// user-gesture navigation, not a JS redirect) route correctly instead.
export default function PayRedirectPage() {
    const [params] = useSearchParams()
    const id = params.get('id')

    const [info, setInfo] = useState(null)
    const [loadError, setLoadError] = useState(false)
    const [screenshot, setScreenshot] = useState(null)
    const [submitting, setSubmitting] = useState(false)
    const [submitted, setSubmitted] = useState(false)
    const [error, setError] = useState('')

    useEffect(() => {
        if (!id) { setLoadError(true); return }
        api.get(`/pay/${id}`)
            .then(r => setInfo(r.data.data))
            .catch(() => setLoadError(true))
    }, [id])

    const handleSubmit = async (e) => {
        e.preventDefault()
        if (!screenshot) { setError('Please attach a screenshot of the payment.'); return }
        setError('')
        setSubmitting(true)
        try {
            const formData = new FormData()
            formData.append('linkId', id)
            formData.append('file', screenshot)
            await api.post('/payments/claims', formData)
            setSubmitted(true)
        } catch (err) {
            const msg = err.response?.data?.message || 'Failed to submit — please try again.'
            setError(msg)
            toast.error(msg)
        } finally {
            setSubmitting(false)
        }
    }

    if (loadError) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-primary-950 p-6 text-center">
                <div className="card p-8 max-w-sm w-full">
                    <p className="text-4xl mb-3">⚠️</p>
                    <h1 className="text-white text-lg font-semibold mb-2">Link not found or expired</h1>
                    <p className="text-primary-400 text-sm">
                        This payment link is no longer valid. Please contact the library or ask for a new reminder.
                    </p>
                </div>
            </div>
        )
    }

    return (
        <div className="min-h-screen flex items-center justify-center bg-primary-950 p-6 text-center">
            <div className="card p-8 max-w-sm w-full">
                <p className="text-4xl mb-3">📱</p>
                <h1 className="text-white text-lg font-semibold mb-2">Pay via UPI</h1>
                {info && (
                    <p className="text-primary-400 text-sm mb-4">
                        Pay ₹{info.amount} to {info.payeeName}
                    </p>
                )}

                {info && (
                    <div className="flex flex-col gap-2">
                        {UPI_APPS.map(app => (
                            <a
                                key={app.key}
                                href={buildUpiIntentLink({
                                    vpa: info.vpa, payeeName: info.payeeName, amount: info.amount, note: info.note,
                                    androidPackage: app.androidPackage,
                                })}
                                className="btn-primary w-full py-2.5 text-sm text-center"
                            >
                                Pay with {app.label}
                            </a>
                        ))}
                    </div>
                )}

                <p className="text-primary-500 text-xs mt-4">
                    Tap the app you use — if it doesn't open, that app may not be installed on this device.
                    This won't work on a desktop browser.
                </p>

                {info?.claimType && (
                    <div className="mt-6 pt-6 border-t border-primary-700/30 text-left">
                        {submitted ? (
                            <p className="text-primary-200 text-sm text-center">
                                ⏳ Waiting for admin to verify the payment.
                            </p>
                        ) : (
                            <form onSubmit={handleSubmit}>
                                <h2 className="text-white text-sm font-semibold mb-2 text-center">Confirm Your Payment</h2>
                                <p className="text-primary-400 text-xs mb-3 text-center">
                                    Once you've paid ₹{info.amount}, upload a screenshot of the payment to confirm.
                                </p>
                                <label className="label">Payment Screenshot</label>
                                <input
                                    type="file"
                                    accept="image/jpeg,image/png,image/webp"
                                    onChange={e => setScreenshot(e.target.files?.[0] || null)}
                                    className="input w-full text-sm mb-2"
                                />
                                {error && <p className="text-red-400 text-xs mb-2">{error}</p>}
                                <button
                                    type="submit"
                                    disabled={submitting || !screenshot}
                                    className="btn-primary w-full py-2.5 text-sm disabled:opacity-40 disabled:cursor-not-allowed">
                                    {submitting ? 'Submitting…' : `Yes, I've paid ₹${info.amount} — Submit`}
                                </button>
                            </form>
                        )}
                    </div>
                )}
            </div>
        </div>
    )
}
