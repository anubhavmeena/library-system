import { useEffect, useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { fetchMyMembership, fetchPlans } from '../../store/slices/membershipSlice'
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
    const { current: membership, plans } = useSelector(s => s.membership)
    const [history, setHistory] = useState([])
    const [loading, setLoading] = useState(true)
    const [downloadingCard, setDownloadingCard] = useState(false)

    useEffect(() => {
        dispatch(fetchMyMembership())
        dispatch(fetchPlans())
        api.get('/memberships/my/all').then(r => setHistory(r.data.data || [])).catch(() => {})
        api.get('/payments/my').then(r => r.data.data || []).catch(() => {})
            .finally(() => setLoading(false))
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
                    {daysLeft <= 10 && (
                        <div className="mt-5 pt-5 border-t border-primary-700/30 flex items-center justify-between">
                            <p className="text-primary-400 text-sm">{t('membership.readyToRenew')}</p>
                            <Link to="/student/booking" className="btn-primary text-sm px-5 py-2.5">{t('membership.renewBtn')}</Link>
                        </div>
                    )}
                </div>
            ) : (
                <div className="card p-8 text-center mb-6 border-amber-500/20 bg-amber-500/5">
                    <div className="text-4xl mb-3">📋</div>
                    <h2 className="section-title mb-2">{t('membership.noActive.title')}</h2>
                    <p className="text-primary-400 mb-5">{t('membership.noActive.desc')}</p>
                    <Link to="/student/booking" className="btn-primary inline-block">{t('membership.noActive.cta')}</Link>
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
        </div>
    )
}
