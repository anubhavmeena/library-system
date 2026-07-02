import { useEffect, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import toast from 'react-hot-toast'
import { fetchMyMembership, fetchQueuedMembership, fetchPlans, createPaymentOrder, verifyPayment, createDuesOrder, verifyDuesPayment } from '../../store/slices/membershipSlice'
import api from '../../services/api'

function InfoRow({ label, value, highlight }) {
    return (
        <div className="flex justify-between items-center py-3 border-b border-primary-700/30 last:border-0">
            <span className="text-primary-400 text-sm">{label}</span>
            <span className={`font-medium text-sm ${highlight ? 'text-amber-400' : 'text-white'}`}>{value || '—'}</span>
        </div>
    )
}

function StatusBadge({ status }) {
    const map = {
        ACTIVE:    'bg-emerald-500/20 text-emerald-400 border-emerald-500/30',
        EXPIRED:   'bg-red-500/20 text-red-400 border-red-500/30',
        PENDING:   'bg-amber-500/20 text-amber-400 border-amber-500/30',
        CANCELLED: 'bg-primary-700/40 text-primary-400 border-primary-600/30',
    }
    return (
        <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border ${map[status] || map.PENDING}`}>
      <span className="w-1.5 h-1.5 rounded-full bg-current" />
            {status}
    </span>
    )
}

export default function MembershipPage() {
    const dispatch = useDispatch()
    const { t } = useTranslation()
    const { current: membership, queued: queuedMembership, plans } = useSelector(s => s.membership)
    const [history, setHistory]                 = useState([])
    const [payments, setPayments]               = useState([])
    const [loading, setLoading]                 = useState(true)
    const [paymentsLoading, setPaymentsLoading] = useState(true)
    const [downloadingCard, setDownloadingCard] = useState(false)
    const [queueFlow, setQueueFlow]             = useState(null) // null | 'select' | 'paying'
    const [selectedQueuePlan, setSelectedQueuePlan] = useState(null)
    const [queuePaying, setQueuePaying]         = useState(false)
    const [payingDues, setPayingDues]           = useState(false)

    useEffect(() => {
        dispatch(fetchMyMembership())
        dispatch(fetchQueuedMembership())
        dispatch(fetchPlans())
        api.get('/memberships/my/all').then(r => setHistory(r.data.data || [])).catch(() => {})
            .finally(() => setLoading(false))
        api.get('/payments/my').then(r => setPayments(r.data.data || [])).catch(() => {})
            .finally(() => setPaymentsLoading(false))
    }, [])

    const daysLeft = membership
        ? Math.max(0, Math.ceil((new Date(membership.endDate) - new Date()) / 86400000))
        : null

    const formatShift = (shift) =>
        t(`membership.shifts.${shift || 'FULL_DAY'}`)

    const handleDownloadIdCard = async () => {
        setDownloadingCard(true)
        try {
            const response = await api.get('/memberships/my/id-card', { responseType: 'blob' })
            const url = window.URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }))
            const link = document.createElement('a')
            link.href = url
            link.setAttribute('download', 'id-card.pdf')
            document.body.appendChild(link)
            link.click()
            link.remove()
            window.URL.revokeObjectURL(url)
        } catch (err) {
            console.error('Failed to download ID card:', err)
        } finally {
            setDownloadingCard(false)
        }
    }

    const handleQueuePayment = async () => {
        if (!selectedQueuePlan) return
        setQueuePaying(true)
        try {
            const orderRes = await dispatch(createPaymentOrder({ planId: selectedQueuePlan.id }))
            if (!createPaymentOrder.fulfilled.match(orderRes)) throw new Error(orderRes.payload)
            const order = orderRes.payload

            const onSuccess = async () => {
                toast.success('Plan queued! It will activate when your current plan expires.')
                setQueueFlow(null)
                setSelectedQueuePlan(null)
                dispatch(fetchQueuedMembership())
            }

            if (order.orderId?.startsWith('dev_')) {
                const verifyRes = await dispatch(verifyPayment({
                    gatewayOrderId: order.orderId, gatewayPaymentId: 'dev_pay_' + Date.now(),
                    signature: 'dev_sig', membershipId: order.membershipId,
                }))
                if (verifyPayment.fulfilled.match(verifyRes)) {
                    const m = verifyRes.payload
                    if (m.seatNumber) await api.post('/seats/book', {
                        seatNumber: m.seatNumber, membershipId: m.id,
                        shift: m.shift, startDate: m.startDate, endDate: m.endDate,
                    }).catch(() => {})
                    await onSuccess()
                }
            } else if (order.gateway === 'CASHFREE') {
                const { load } = await import('@cashfreepayments/cashfree-js')
                const cashfree = await load({ mode: import.meta.env.VITE_CASHFREE_ENV || 'sandbox' })
                const result = await cashfree.checkout({
                    paymentSessionId: order.paymentSessionId,
                    redirectTarget: '_modal',
                    components: ['upi-qr', 'upi-collect', 'app', 'card', 'netbanking', 'paylater'],
                })
                if (result.error) throw new Error(result.error.message || 'Payment failed')
                const verifyRes = await dispatch(verifyPayment({
                    gatewayOrderId: order.orderId, gatewayPaymentId: order.orderId,
                    signature: null, membershipId: order.membershipId,
                }))
                if (verifyPayment.fulfilled.match(verifyRes)) {
                    const m = verifyRes.payload
                    if (m.seatNumber) await api.post('/seats/book', {
                        seatNumber: m.seatNumber, membershipId: m.id,
                        shift: m.shift, startDate: m.startDate, endDate: m.endDate,
                    }).catch(() => {})
                    await onSuccess()
                } else toast.error('Payment verification failed')
            } else {
                const options = {
                    key: order.razorpayKeyId, amount: order.amount * 100, currency: 'INR',
                    name: 'Target Zone Library',
                    description: `${selectedQueuePlan.name} — Queued Plan`,
                    order_id: order.orderId,
                    handler: async (response) => {
                        const verifyRes = await dispatch(verifyPayment({
                            gatewayOrderId: response.razorpay_order_id,
                            gatewayPaymentId: response.razorpay_payment_id,
                            signature: response.razorpay_signature,
                            membershipId: order.membershipId,
                        }))
                        if (verifyPayment.fulfilled.match(verifyRes)) {
                            const m = verifyRes.payload
                            if (m.seatNumber) await api.post('/seats/book', {
                                seatNumber: m.seatNumber, membershipId: m.id,
                                shift: m.shift, startDate: m.startDate, endDate: m.endDate,
                            }).catch(() => {})
                            await onSuccess()
                        } else toast.error('Payment verification failed')
                    },
                    theme: { color: '#f59e0b' },
                }
                new window.Razorpay(options).open()
            }
        } catch (e) {
            toast.error(e.message || 'Payment failed')
        } finally { setQueuePaying(false) }
    }

    const handlePayDues = async () => {
        setPayingDues(true)
        try {
            const orderRes = await dispatch(createDuesOrder())
            if (!createDuesOrder.fulfilled.match(orderRes)) throw new Error(orderRes.payload)
            const order = orderRes.payload

            const onSuccess = async () => {
                toast.success('Dues cleared — your membership is active again!')
                dispatch(fetchMyMembership())
            }

            if (order.orderId?.startsWith('dev_')) {
                const verifyRes = await dispatch(verifyDuesPayment({
                    gatewayOrderId: order.orderId, gatewayPaymentId: 'dev_pay_' + Date.now(),
                    signature: 'dev_sig', membershipId: order.membershipId,
                }))
                if (verifyDuesPayment.fulfilled.match(verifyRes)) await onSuccess()
                else toast.error('Payment verification failed')
            } else if (order.gateway === 'CASHFREE') {
                const { load } = await import('@cashfreepayments/cashfree-js')
                const cashfree = await load({ mode: import.meta.env.VITE_CASHFREE_ENV || 'sandbox' })
                const result = await cashfree.checkout({
                    paymentSessionId: order.paymentSessionId,
                    redirectTarget: '_modal',
                    components: ['upi-qr', 'upi-collect', 'app', 'card', 'netbanking', 'paylater'],
                })
                if (result.error) throw new Error(result.error.message || 'Payment failed')
                const verifyRes = await dispatch(verifyDuesPayment({
                    gatewayOrderId: order.orderId, gatewayPaymentId: order.orderId,
                    signature: null, membershipId: order.membershipId,
                }))
                if (verifyDuesPayment.fulfilled.match(verifyRes)) await onSuccess()
                else toast.error('Payment verification failed')
            } else {
                const options = {
                    key: order.razorpayKeyId, amount: order.amount * 100, currency: 'INR',
                    name: 'Target Zone Library',
                    description: 'Clear membership dues',
                    order_id: order.orderId,
                    handler: async (response) => {
                        const verifyRes = await dispatch(verifyDuesPayment({
                            gatewayOrderId: response.razorpay_order_id,
                            gatewayPaymentId: response.razorpay_payment_id,
                            signature: response.razorpay_signature,
                            membershipId: order.membershipId,
                        }))
                        if (verifyDuesPayment.fulfilled.match(verifyRes)) await onSuccess()
                        else toast.error('Payment verification failed')
                    },
                    theme: { color: '#f59e0b' },
                }
                new window.Razorpay(options).open()
            }
        } catch (e) {
            toast.error(e.message || 'Payment failed')
        } finally { setPayingDues(false) }
    }

    return (
        <div>
            <div className="mb-8">
                <h1 className="page-header">{t('membership.title')}</h1>
                <p className="text-primary-400">{t('membership.subtitle')}</p>
            </div>

            {loading ? (
                <div className="shimmer h-64 rounded-2xl mb-6" />
            ) : membership && membership.status === 'ACTIVE' ? (
                <div className="card p-6 mb-6 border-emerald-500/20 bg-gradient-to-br from-emerald-500/5 to-transparent">
                    <div className="flex items-start justify-between mb-5">
                        <div>
                            <h2 className="section-title">{t('membership.activeMembership')}</h2>
                            <p className="text-primary-400 text-sm mt-1">{membership.planName}</p>
                        </div>
                        <StatusBadge status={membership.status} />
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                        <div>
                            <InfoRow label={t('membership.planType')}   value={t(`membership.planTypes.${membership.planType}`)} />
                            <InfoRow label={t('membership.seatNumber')} value={membership.seatNumber} highlight />
                            <InfoRow label={t('membership.shift')}      value={formatShift(membership.shift)} />
                            <InfoRow label={t('membership.startDate')}  value={membership.startDate} />
                            <InfoRow label={t('membership.expiryDate')} value={membership.endDate} />
                        </div>
                        <div className="flex flex-col items-center justify-center py-4">
                            <div className={`relative w-28 h-28 rounded-full flex items-center justify-center border-4 ${daysLeft <= 5 ? 'border-amber-500/60' : 'border-emerald-500/60'}`}>
                                <div className="text-center">
                                    <p className={`text-3xl font-bold ${daysLeft <= 5 ? 'text-amber-400' : 'text-emerald-400'}`}>{daysLeft}</p>
                                    <p className="text-primary-400 text-xs">{t('membership.daysLeft')}</p>
                                </div>
                            </div>
                            {daysLeft <= 7 && <p className="text-amber-400 text-xs mt-3 text-center">{t('membership.expiringSoon')}</p>}
                        </div>
                    </div>
                    <div className="mt-5 pt-5 border-t border-primary-700/30 flex items-center justify-between">
                        <p className="text-primary-400 text-sm">Download your membership ID card as PDF</p>
                        <button
                            onClick={handleDownloadIdCard}
                            disabled={downloadingCard}
                            className="btn-outline text-sm px-5 py-2.5"
                        >
                            {downloadingCard ? 'Generating...' : 'Download ID Card'}
                        </button>
                    </div>
                    {!queuedMembership && (
                        <div className="mt-5 pt-5 border-t border-primary-700/30 flex items-center justify-between">
                            <p className="text-primary-400 text-sm">Queue your next plan — it activates automatically when this one expires.</p>
                            <button onClick={() => setQueueFlow('select')} className="btn-outline text-sm px-5 py-2.5">
                                Queue Next Plan
                            </button>
                        </div>
                    )}
                </div>
            ) : membership && membership.status === 'GRACE' ? (
                <div className="card p-6 mb-6 border-red-500/30 bg-gradient-to-br from-red-500/5 to-transparent">
                    <div className="flex items-start justify-between mb-4">
                        <div>
                            <h2 className="section-title">Membership Expired</h2>
                            <p className="text-primary-400 text-sm mt-1">{membership.planName}</p>
                        </div>
                        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border bg-red-500/20 text-red-400 border-red-500/30">
                            <span className="w-1.5 h-1.5 rounded-full bg-current" /> GRACE
                        </span>
                    </div>
                    <InfoRow label={t('membership.seatNumber')} value={membership.seatNumber} highlight />
                    <InfoRow label={t('membership.expiryDate')} value={membership.endDate} />
                    <div className="mt-5 pt-5 border-t border-primary-700/30">
                        <p className="text-primary-300 text-sm mb-1">
                            Your plan expired, but we're holding seat <span className="text-white font-mono">{membership.seatNumber}</span> for you.
                        </p>
                        <p className="text-red-400 font-semibold text-sm mb-4">
                            Pay ₹{membership.duesAmount ?? membership.planPrice} to continue your plan — your seat may be released by the library if it remains unpaid.
                        </p>
                        <button onClick={handlePayDues} disabled={payingDues} className="btn-primary text-sm px-6 py-2.5">
                            {payingDues ? 'Processing...' : `Pay ₹${membership.duesAmount ?? membership.planPrice}`}
                        </button>
                    </div>
                </div>
            ) : (
                <div className="card p-8 text-center mb-6 border-amber-500/20 bg-amber-500/5">
                    <div className="text-4xl mb-3">📋</div>
                    <h2 className="section-title mb-2">{t('membership.noActive.title')}</h2>
                    <p className="text-primary-400 mb-5">{t('membership.noActive.desc')}</p>
                    <Link to="/student/booking" className="btn-primary inline-block">{t('membership.noActive.cta')}</Link>
                </div>
            )}

            {/* Queued plan card */}
            {queuedMembership && (
                <div className="card p-6 mb-6 border-violet-500/20 bg-gradient-to-br from-violet-500/5 to-transparent">
                    <div className="flex items-start justify-between mb-4">
                        <div>
                            <h2 className="section-title">Queued Plan</h2>
                            <p className="text-primary-400 text-sm mt-1">{queuedMembership.planName}</p>
                        </div>
                        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border bg-violet-500/20 text-violet-400 border-violet-500/30">
                            <span className="w-1.5 h-1.5 rounded-full bg-current" /> QUEUED
                        </span>
                    </div>
                    <InfoRow label="Seat"       value={queuedMembership.seatNumber} highlight />
                    <InfoRow label="Shift"      value={formatShift(queuedMembership.shift)} />
                    <InfoRow label="Starts On"  value={queuedMembership.startDate} />
                    <InfoRow label="Expires On" value={queuedMembership.endDate} />
                    <p className="text-violet-400/70 text-xs mt-4">This plan will activate automatically when your current plan expires.</p>
                </div>
            )}

            {/* Queue flow — plan selection */}
            {queueFlow === 'select' && (
                <div className="card p-6 mb-6 border-violet-500/20">
                    <div className="flex items-center justify-between mb-5">
                        <h2 className="section-title">Select Next Plan</h2>
                        <button onClick={() => { setQueueFlow(null); setSelectedQueuePlan(null) }}
                                className="text-primary-400 hover:text-white text-sm transition-colors">✕ Cancel</button>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-5">
                        {plans.map(plan => (
                            <button key={plan.id} onClick={() => setSelectedQueuePlan(plan)}
                                    className={`text-left p-4 rounded-xl border transition-all ${selectedQueuePlan?.id === plan.id
                                        ? 'border-violet-400/60 bg-violet-500/10'
                                        : 'border-primary-700/40 hover:border-primary-600/60'}`}>
                                <div className="flex justify-between items-start mb-1">
                                    <p className="text-white font-semibold text-sm">{plan.name}</p>
                                    <p className="text-amber-400 font-bold">₹{plan.price}</p>
                                </div>
                                <p className="text-primary-400 text-xs">{plan.description}</p>
                            </button>
                        ))}
                    </div>
                    {selectedQueuePlan && (
                        <div className="flex items-center justify-between pt-4 border-t border-primary-700/30">
                            <div>
                                <p className="text-white text-sm font-medium">{selectedQueuePlan.name}</p>
                                <p className="text-primary-400 text-xs">Seat and shift inherited from current plan</p>
                            </div>
                            <button onClick={handleQueuePayment} disabled={queuePaying}
                                    className="btn-primary text-sm px-6 py-2.5">
                                {queuePaying ? 'Processing...' : `Pay ₹${selectedQueuePlan.price}`}
                            </button>
                        </div>
                    )}
                </div>
            )}

            <div className="mb-8">
                <h2 className="section-title mb-4">{t('membership.plans.title')}</h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    {plans.map(plan => (
                        <div key={plan.id} className="card p-5">
                            <div className="flex items-start justify-between mb-3">
                                <div>
                                    <h3 className="text-white font-semibold">{plan.name}</h3>
                                    <p className="text-primary-400 text-sm mt-0.5">{plan.description}</p>
                                </div>
                                <div className="text-right">
                                    <p className="text-2xl font-bold text-amber-400">₹{plan.price}</p>
                                    <p className="text-primary-500 text-xs">{t('membership.plans.perMonth')}</p>
                                </div>
                            </div>
                            <div className="space-y-1.5">
                                {(t(plan.planType === 'HALF_DAY' ? 'membership.plans.halfDayFeatures' : 'membership.plans.fullDayFeatures', { returnObjects: true })).map(f => (
                                    <div key={f} className="flex items-center gap-2 text-sm text-primary-300">
                                        <span className="text-amber-400 text-xs">✓</span>{f}
                                    </div>
                                ))}
                            </div>
                            {plan.planType === 'FULL_DAY' && (
                                <span className="inline-block mt-3 px-2 py-0.5 rounded-full bg-amber-500/20 border border-amber-500/30 text-amber-400 text-xs">{t('membership.plans.popular')}</span>
                            )}
                        </div>
                    ))}
                </div>
            </div>

            {history.length > 1 && (
                <div className="mb-8">
                    <h2 className="section-title mb-4">{t('membership.history.title')}</h2>
                    <div className="card divide-y divide-primary-700/30">
                        {history.map(m => (
                            <div key={m.id} className="flex items-center justify-between px-5 py-4">
                                <div>
                                    <p className="text-white text-sm font-medium">{m.planName}</p>
                                    <p className="text-primary-400 text-xs mt-0.5">{m.startDate} → {m.endDate} · Seat {m.seatNumber || '—'}</p>
                                </div>
                                <StatusBadge status={m.status} />
                            </div>
                        ))}
                    </div>
                </div>
            )}

            <div className="mb-8">
                <h2 className="section-title mb-4">Payment History</h2>
                {paymentsLoading ? (
                    <div className="shimmer h-32 rounded-2xl" />
                ) : payments.length === 0 ? (
                    <div className="card p-6 text-center text-primary-400 text-sm">No payments found.</div>
                ) : (
                    <div className="card overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-primary-700/30 text-primary-400 text-xs uppercase tracking-wide">
                                    <th className="text-left px-4 py-3">Date</th>
                                    <th className="text-left px-4 py-3">Amount</th>
                                    <th className="text-left px-4 py-3">Mode</th>
                                    <th className="text-left px-4 py-3">Order Ref</th>
                                    <th className="text-left px-4 py-3">Payment Ref</th>
                                    <th className="text-left px-4 py-3">Status</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-primary-700/20">
                                {payments.map(p => {
                                    const isCash = !p.paymentGateway || p.gatewayOrderId?.startsWith('dev_')
                                    return (
                                        <tr key={p.id} className="hover:bg-primary-700/10 transition-colors">
                                            <td className="px-4 py-3 text-primary-300">{p.createdAt ? new Date(p.createdAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : '—'}</td>
                                            <td className="px-4 py-3 text-amber-400 font-semibold">₹{Number(p.amount).toLocaleString('en-IN')}</td>
                                            <td className="px-4 py-3">
                                                <span className={`px-2 py-0.5 rounded-full text-xs font-medium border ${isCash ? 'bg-amber-500/20 text-amber-400 border-amber-500/30' : 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30'}`}>
                                                    {isCash ? 'Cash' : 'Online'}
                                                </span>
                                            </td>
                                            <td className="px-4 py-3 text-primary-300 font-mono text-xs">{p.gatewayOrderId || '—'}</td>
                                            <td className="px-4 py-3 text-primary-300 font-mono text-xs">{p.gatewayPaymentId || '—'}</td>
                                            <td className="px-4 py-3">
                                                <StatusBadge status={p.status} />
                                            </td>
                                        </tr>
                                    )
                                })}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    )
}
