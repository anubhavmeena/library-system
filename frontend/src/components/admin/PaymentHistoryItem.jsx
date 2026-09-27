import { useTranslation } from 'react-i18next'
import { paymentModeInfo } from '../../utils/paymentMode'

const fmtAmount = (n) => `₹${Number(n).toLocaleString('en-IN')}`

// Backend sends naive timestamps/dates (no zone); parse the date part as local
// so a DATE like 2026-09-28 doesn't shift a day in negative-offset zones.
const fmtDate = (d) => {
    if (!d) return null
    const [y, m, day] = d.split('T')[0].split('-').map(Number)
    return new Date(y, m - 1, day).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
}
const fmtDateTime = (d) => d
    ? new Date(d).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
    : null

const Row = ({ label, children, mono }) => (
    <div className="flex justify-between gap-3">
        <span className="text-primary-500">{label}</span>
        <span className={`text-primary-200 text-right break-all ${mono ? 'font-mono' : ''}`}>{children}</span>
    </div>
)

// One row of a student's payment history on the admin side. `paidAt` comes
// from the Rust backend's StudentPaymentItem; `createdAt` is the fallback for
// backends that still return raw Payment rows.
export default function PaymentHistoryItem({ payment: p, showStatus = false }) {
    const { t } = useTranslation()
    const info = paymentModeInfo(p.paymentGateway, t)
    const paidAt = fmtDateTime(p.paidAt || p.createdAt)
    const start = fmtDate(p.startDate)
    const end = fmtDate(p.endDate)
    const pending = Number(p.pendingAmount || 0)
    const discount = Number(p.discountAmount || 0)

    return (
        <div className="rounded-lg bg-primary-800/40 border border-primary-700/30 px-3 py-2.5 text-xs">
            <div className="flex items-center justify-between mb-2">
                <span className="text-white font-semibold">{fmtAmount(p.amount)}</span>
                <div className="flex items-center gap-2">
                    <span className={`px-2 py-0.5 rounded-full font-medium border ${info?.className ?? 'bg-primary-700/40 text-primary-400 border-primary-600/30'}`}>
                        {info ? `${info.emoji} ${info.label}` : '—'}
                    </span>
                    {showStatus && (
                        <span className={`px-2 py-0.5 rounded-full font-medium border ${
                            p.status === 'SUCCESS' ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30' :
                            p.status === 'PENDING' ? 'bg-amber-500/20 text-amber-400 border-amber-500/30' :
                            'bg-red-500/20 text-red-400 border-red-500/30'
                        }`}>{p.status}</span>
                    )}
                </div>
            </div>
            <div className="space-y-1">
                <Row label={t('adminStudentDetail.payment.paidOn')}>{paidAt || '—'}</Row>
                {p.planName && <Row label={t('adminStudentDetail.payment.plan')}>{p.planName}</Row>}
                {p.seatNumber && <Row label={t('adminStudentDetail.payment.seat')} mono>{p.seatNumber}</Row>}
                {start && end && <Row label={t('adminStudentDetail.payment.period')}>{start} → {end}</Row>}
                {discount > 0 && (
                    <Row label={t('adminStudentDetail.payment.discount')}>
                        {fmtAmount(discount)}{p.couponCode ? ` (${p.couponCode})` : ''}
                    </Row>
                )}
                {pending > 0 && (
                    <Row label={t('adminStudentDetail.payment.pending')}>
                        <span className="text-orange-400">{fmtAmount(pending)}</span>
                    </Row>
                )}
                {p.invoiceId        && <Row label={t('adminStudentDetail.payment.invoice')} mono>{p.invoiceId}</Row>}
                {p.gatewayOrderId   && <Row label={t('adminStudentDetail.payment.order')} mono>{p.gatewayOrderId}</Row>}
                {p.gatewayPaymentId && <Row label={t('adminStudentDetail.payment.ref')} mono>{p.gatewayPaymentId}</Row>}
            </div>
        </div>
    )
}
