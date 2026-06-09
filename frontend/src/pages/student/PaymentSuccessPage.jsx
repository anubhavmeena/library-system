import { useEffect } from 'react'
import { useSelector } from 'react-redux'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

export default function PaymentSuccessPage() {
    const navigate = useNavigate()
    const { t } = useTranslation()
    const { current: membership } = useSelector(s => s.membership)

    useEffect(() => {
        if (!membership) navigate('/student/dashboard')
    }, [])

    const shiftLabel = (shift) => {
        if (shift === 'MORNING') return t('payment.details.shiftMorning')
        if (shift === 'EVENING') return t('payment.details.shiftEvening')
        return t('payment.details.shiftFullDay')
    }

    const rows = membership ? [
        { l: t('payment.details.plan'),     v: membership.planName },
        { l: t('payment.details.seat'),      v: membership.seatNumber || '—' },
        { l: t('payment.details.shift'),     v: shiftLabel(membership.shift) },
        { l: t('payment.details.validTill'), v: membership.endDate },
        { l: t('payment.details.status'),    v: membership.status },
    ] : []

    return (
        <div className="min-h-[60vh] flex items-center justify-center">
            <div className="max-w-md w-full text-center">
                <div className="relative mb-8">
                    <div className="w-24 h-24 bg-emerald-500/20 rounded-full flex items-center justify-center mx-auto border-2 border-emerald-500/40">
                        <div className="w-16 h-16 bg-emerald-500/30 rounded-full flex items-center justify-center">
                            <span className="text-4xl">✓</span>
                        </div>
                    </div>
                    <div className="absolute inset-0 rounded-full animate-ping bg-emerald-500/10" />
                </div>

                <h1 className="font-display text-3xl font-bold text-white mb-3">{t('payment.title')}</h1>
                <p className="text-primary-400 mb-8">{t('payment.subtitle')}</p>

                {membership && (
                    <div className="card p-6 mb-8 text-left space-y-3">
                        {rows.map(({ l, v }) => (
                            <div key={l} className="flex justify-between items-center py-2 border-b border-primary-700/30 last:border-0">
                                <span className="text-primary-400 text-sm">{l}</span>
                                <span className="text-white font-medium text-sm">{v}</span>
                            </div>
                        ))}
                    </div>
                )}

                <div className="flex gap-3">
                    <Link to="/student/dashboard"  className="flex-1 btn-primary text-center py-3">{t('payment.dashboard')}</Link>
                    <Link to="/student/membership" className="flex-1 btn-outline text-center py-3">{t('payment.viewMembership')}</Link>
                </div>
            </div>
        </div>
    )
}
