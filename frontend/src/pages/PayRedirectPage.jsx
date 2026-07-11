import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import toast from 'react-hot-toast'
import api from '../services/api'
import { buildUpiDeepLink } from '../utils/upiPay'

// Public, unauthenticated — reached via a tap-to-pay link shared over
// WhatsApp (see AdminCreateMembershipPage's "Send Payment Request", and the
// Pending Fee / Grace Dues reminders). The link only carries a short opaque
// `id` (not the UPI params directly — those are resolved server-side via
// GET /api/pay/:id, keeping the shared WhatsApp link short). Once resolved,
// immediately redirects to a upi://pay deep link, handing off to whatever
// UPI app is installed. Stays visible (as a fallback) if nothing claims the
// upi:// intent — e.g. opened on a desktop browser or no UPI app installed.
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
            .then(r => {
                const data = r.data.data
                setInfo(data)
                const link = buildUpiDeepLink({
                    vpa: data.vpa, payeeName: data.payeeName, amount: data.amount, note: data.note,
                })
                window.location.href = link
            })
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
                <h1 className="text-white text-lg font-semibold mb-2">Opening your UPI app…</h1>
                {info && (
                    <p className="text-primary-400 text-sm mb-1">
                        Pay ₹{info.amount} to {info.payeeName}
                    </p>
                )}
                <p className="text-primary-500 text-xs mt-4">
                    Nothing happened? You need a UPI app (Google Pay, PhonePe, Paytm, etc.)
                    installed on this device — this link won't work on a desktop browser.
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
