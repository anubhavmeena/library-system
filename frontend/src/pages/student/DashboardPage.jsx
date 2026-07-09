import { useEffect, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import toast from 'react-hot-toast'
import { fetchMyMembership, fetchMyDisplayStatus, createPendingOrder, verifyPendingPayment } from '../../store/slices/membershipSlice'
import { formatCurrency } from '../../utils/currency'
import StatusBadge from '../../components/StatusBadge'

function StatCard({ label, value, sub, color = 'amber' }) {
    const colors = {
        amber:   'from-amber-500/20 to-amber-600/5 border-amber-500/20',
        emerald: 'from-emerald-500/20 to-emerald-600/5 border-emerald-500/20',
        blue:    'from-blue-500/20 to-blue-600/5 border-blue-500/20',
    }
    const textColors = { amber: 'text-amber-400', emerald: 'text-emerald-400', blue: 'text-blue-400' }
    return (
        <div className={`card bg-gradient-to-br ${colors[color]} p-5`}>
            <p className="text-primary-400 text-sm mb-1">{label}</p>
            <p className={`text-2xl font-bold ${textColors[color]}`}>{value}</p>
            {sub && <p className="text-primary-500 text-xs mt-1">{sub}</p>}
        </div>
    )
}

export default function DashboardPage() {
    const dispatch = useDispatch()
    const { t } = useTranslation()
    const { user } = useSelector(s => s.auth)
    const { current: membership, displayStatus } = useSelector(s => s.membership)
    const [payingPending, setPayingPending] = useState(false)

    useEffect(() => {
        dispatch(fetchMyMembership())
        dispatch(fetchMyDisplayStatus())
    }, [])

    const handlePayPending = async () => {
        setPayingPending(true)
        try {
            const orderRes = await dispatch(createPendingOrder())
            if (!createPendingOrder.fulfilled.match(orderRes)) throw new Error(orderRes.payload)
            const order = orderRes.payload

            const onSuccess = async () => {
                toast.success(t('dashboard.pendingBanner.cleared'))
                dispatch(fetchMyMembership())
                dispatch(fetchMyDisplayStatus())
            }

            if (order.orderId?.startsWith('dev_')) {
                const verifyRes = await dispatch(verifyPendingPayment({
                    gatewayOrderId: order.orderId, gatewayPaymentId: 'dev_pay_' + Date.now(),
                    signature: 'dev_sig', membershipId: order.membershipId,
                }))
                if (verifyPendingPayment.fulfilled.match(verifyRes)) await onSuccess()
                else toast.error(t('dashboard.pendingBanner.verifyFailed'))
            } else if (order.gateway === 'CASHFREE') {
                const { load } = await import('@cashfreepayments/cashfree-js')
                const cashfree = await load({ mode: import.meta.env.VITE_CASHFREE_ENV || 'sandbox' })
                const result = await cashfree.checkout({
                    paymentSessionId: order.paymentSessionId,
                    redirectTarget: '_modal',
                    components: ['upi-qr', 'upi-collect', 'app', 'card', 'netbanking', 'paylater'],
                })
                if (result.error) throw new Error(result.error.message || 'Payment failed')
                const verifyRes = await dispatch(verifyPendingPayment({
                    gatewayOrderId: order.orderId, gatewayPaymentId: order.orderId,
                    signature: null, membershipId: order.membershipId,
                }))
                if (verifyPendingPayment.fulfilled.match(verifyRes)) await onSuccess()
                else toast.error(t('dashboard.pendingBanner.verifyFailed'))
            } else {
                const options = {
                    key: order.razorpayKeyId, amount: order.amount * 100, currency: 'INR',
                    name: 'Target Zone Library',
                    description: 'Clear pending amount',
                    order_id: order.orderId,
                    handler: async (response) => {
                        const verifyRes = await dispatch(verifyPendingPayment({
                            gatewayOrderId: response.razorpay_order_id,
                            gatewayPaymentId: response.razorpay_payment_id,
                            signature: response.razorpay_signature,
                            membershipId: order.membershipId,
                        }))
                        if (verifyPendingPayment.fulfilled.match(verifyRes)) await onSuccess()
                        else toast.error(t('dashboard.pendingBanner.verifyFailed'))
                    },
                    theme: { color: '#f59e0b' },
                }
                new window.Razorpay(options).open()
            }
        } catch (e) {
            toast.error(e.message || t('dashboard.pendingBanner.payFailed'))
        } finally { setPayingPending(false) }
    }

    const STATUS_META = {
        PAID:     { color: 'emerald', sub: t('dashboard.stats.activeMembership') },
        PENDING:  { color: 'amber',   sub: t('dashboard.stats.duesPending') },
        GRACE:         { color: 'amber',   sub: t('dashboard.stats.gracePeriod') },
        GRACE_OVERDUE: { color: 'amber',   sub: t('dashboard.stats.membershipExpired') },
        RELEASED:      { color: 'amber',   sub: t('dashboard.stats.seatReleased') },
        NEW:      { color: 'blue',    sub: t('dashboard.stats.getPlan') },
    }
    const statusMeta = STATUS_META[displayStatus] || { color: 'amber', sub: t('dashboard.stats.getPlan') }

    const daysLeft = membership
        ? Math.ceil((new Date(membership.endDate) - new Date()) / 86400000)
        : null

    const quickActions = [
        { to: '/student/booking',    icon: '⊞', label: t('dashboard.quickActions.booking.label'),    desc: t('dashboard.quickActions.booking.desc') },
        { to: '/student/membership', icon: '◈', label: t('dashboard.quickActions.membership.label'), desc: t('dashboard.quickActions.membership.desc') },
        { to: '/student/profile',    icon: '◉', label: t('dashboard.quickActions.profile.label'),    desc: t('dashboard.quickActions.profile.desc') },
        { to: '/student/facilities', icon: '⌂', label: t('dashboard.quickActions.facilities.label'), desc: t('dashboard.quickActions.facilities.desc') },
    ]

    const hours = [
        { key: 'morning', icon: '🌅' },
        { key: 'evening', icon: '🌆' },
        { key: 'fullDay', icon: '🌟' },
    ]

    return (
        <div>
            <div className="mb-8">
                <h1 className="page-header">{t('dashboard.greeting', { name: user?.name?.split(' ')[0] })}</h1>
                <p className="text-primary-400">{t('dashboard.subtitle')}</p>
            </div>

            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
                <StatCard
                    label={t('dashboard.stats.plan')}
                    value={membership ? (membership.planType === 'FULL_DAY' ? t('dashboard.stats.fullDay') : t('dashboard.stats.halfDay')) : '—'}
                    sub={membership ? `₹${membership.amountPaid}` : t('dashboard.stats.noActivePlan')}
                    color="amber" />
                <StatCard
                    label={t('dashboard.stats.seat')}
                    value={membership?.seatNumber || '—'}
                    sub={membership?.shift === 'MORNING' ? t('dashboard.stats.morning') : membership?.shift === 'EVENING' ? t('dashboard.stats.evening') : t('dashboard.stats.fullDayShift')}
                    color="blue" />
                <StatCard
                    label={t('dashboard.stats.daysLeft')}
                    value={daysLeft ?? '—'}
                    sub={membership ? t('dashboard.stats.expires', { date: membership.endDate }) : t('dashboard.stats.noMembership')}
                    color={daysLeft !== null && daysLeft <= 5 ? 'amber' : 'emerald'} />
                <StatCard
                    label={t('dashboard.stats.status')}
                    value={<StatusBadge status={displayStatus} />}
                    sub={statusMeta.sub}
                    color={statusMeta.color} />
            </div>

            {!membership && (
                <div className="card p-8 text-center mb-8 border-amber-500/20 bg-gradient-to-br from-amber-500/10 to-transparent">
                    <div className="text-4xl mb-4">📚</div>
                    <h2 className="section-title mb-2">{t('dashboard.noMembership.title')}</h2>
                    <p className="text-primary-400 mb-6">{t('dashboard.noMembership.desc')}</p>
                    <Link to="/student/booking" className="btn-primary inline-block">{t('dashboard.noMembership.cta')}</Link>
                </div>
            )}

            {membership?.status === 'ACTIVE' && daysLeft <= 7 && daysLeft > 0 && (
                <div className="card p-5 mb-8 border-amber-500/30 bg-amber-500/10">
                    <div className="flex items-center gap-3">
                        <span className="text-2xl">⚠️</span>
                        <div>
                            <p className="text-amber-400 font-semibold">{t('dashboard.expiring.title', { count: daysLeft })}</p>
                            <p className="text-primary-400 text-sm">{t('dashboard.expiring.desc', { seatNumber: membership.seatNumber })}</p>
                        </div>
                        <Link to="/student/membership" className="ml-auto btn-primary text-sm px-4 py-2 whitespace-nowrap">{t('dashboard.expiring.renew')}</Link>
                    </div>
                </div>
            )}

            {(displayStatus === 'GRACE' || displayStatus === 'GRACE_OVERDUE') && (
                <div className="card p-5 mb-8 border-red-500/30 bg-red-500/10">
                    <div className="flex items-center gap-3">
                        <span className="text-2xl">🔒</span>
                        <div>
                            <p className="text-red-400 font-semibold">{t('dashboard.duesBanner.title')}</p>
                            <p className="text-primary-400 text-sm">{t('dashboard.duesBanner.desc', { seatNumber: membership?.seatNumber })}</p>
                        </div>
                        <Link to="/student/membership" className="ml-auto btn-primary text-sm px-4 py-2 whitespace-nowrap">{t('dashboard.duesBanner.cta')}</Link>
                    </div>
                </div>
            )}

            {displayStatus === 'PENDING' && membership?.pendingAmount > 0 && (
                <div className="card p-5 mb-8 border-amber-500/30 bg-amber-500/10">
                    <div className="flex items-center gap-3">
                        <span className="text-2xl">💳</span>
                        <div>
                            <p className="text-amber-400 font-semibold">{t('dashboard.pendingBanner.title')}</p>
                            <p className="text-primary-400 text-sm">{t('dashboard.pendingBanner.desc', { amount: formatCurrency(membership.pendingAmount) })}</p>
                        </div>
                        <button onClick={handlePayPending} disabled={payingPending}
                                className="ml-auto btn-primary text-sm px-4 py-2 whitespace-nowrap disabled:opacity-50">
                            {payingPending ? t('dashboard.pendingBanner.processing') : t('dashboard.pendingBanner.cta')}
                        </button>
                    </div>
                </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <div className="card p-6">
                    <h3 className="section-title mb-4">{t('dashboard.quickActions.title')}</h3>
                    <div className="space-y-3">
                        {quickActions.map(({ to, icon, label, desc }) => (
                            <Link key={to} to={to} className="flex items-center gap-4 p-3 rounded-xl hover:bg-primary-800/50 transition-colors group">
                                <div className="w-10 h-10 rounded-xl bg-primary-800 flex items-center justify-center text-amber-400 group-hover:bg-amber-500/20 transition-colors">{icon}</div>
                                <div>
                                    <p className="text-white text-sm font-medium">{label}</p>
                                    <p className="text-primary-500 text-xs">{desc}</p>
                                </div>
                                <span className="ml-auto text-primary-600 group-hover:text-amber-400 transition-colors">→</span>
                            </Link>
                        ))}
                    </div>
                </div>

                <div className="card p-6">
                    <h3 className="section-title mb-4">{t('dashboard.hours.title')}</h3>
                    <div className="space-y-3">
                        {hours.map(({ key, icon }) => (
                            <div key={key} className="flex items-center gap-3 p-3 rounded-xl bg-primary-800/30">
                                <span className="text-xl">{icon}</span>
                                <div>
                                    <p className="text-white text-sm font-medium">{t(`dashboard.hours.${key}.label`)}</p>
                                    <p className="text-primary-400 text-xs">{t(`dashboard.hours.${key}.time`)}</p>
                                </div>
                                <span className="ml-auto badge-active">{t('dashboard.hours.open')}</span>
                            </div>
                        ))}
                    </div>
                    <div className="mt-4 p-3 rounded-xl bg-primary-800/30 text-primary-400 text-xs">
                        {t('dashboard.hours.note')}
                    </div>
                </div>
            </div>
        </div>
    )
}
