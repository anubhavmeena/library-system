import { useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { fetchMyMembership } from '../../store/slices/membershipSlice'

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
    const { current: membership } = useSelector(s => s.membership)

    useEffect(() => { dispatch(fetchMyMembership()) }, [])

    const daysLeft = membership
        ? Math.max(0, Math.ceil((new Date(membership.endDate) - new Date()) / 86400000))
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
                    value={membership?.status || t('dashboard.stats.inactive')}
                    sub={membership ? t('dashboard.stats.activeMembership') : t('dashboard.stats.getPlan')}
                    color={membership?.status === 'ACTIVE' ? 'emerald' : 'amber'} />
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
                        <Link to="/student/booking" className="ml-auto btn-primary text-sm px-4 py-2 whitespace-nowrap">{t('dashboard.expiring.renew')}</Link>
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
