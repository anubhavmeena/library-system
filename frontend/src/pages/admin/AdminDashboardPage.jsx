import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

function StatCard({ icon, label, value, sub, color = 'amber', to, onClick }) {
    const colors = {
        amber:   'from-amber-500/20 to-transparent border-amber-500/20',
        emerald: 'from-emerald-500/20 to-transparent border-emerald-500/20',
        red:     'from-red-500/20 to-transparent border-red-500/20',
        blue:    'from-blue-500/20 to-transparent border-blue-500/20',
    }
    const textColors = { amber: 'text-amber-400', emerald: 'text-emerald-400', red: 'text-red-400', blue: 'text-blue-400' }
    const content = (
        <div className={`card bg-gradient-to-br ${colors[color]} p-5 h-full`}>
            <div className="flex items-start justify-between mb-3">
                <div className="text-2xl">{icon}</div>
                {(to || onClick) && <span className="text-primary-500 text-xs">→</span>}
            </div>
            <p className="text-primary-400 text-sm mb-1">{label}</p>
            <p className={`text-3xl font-bold ${textColors[color]}`}>{value ?? '—'}</p>
            {sub && <p className="text-primary-500 text-xs mt-1">{sub}</p>}
        </div>
    )
    if (to)     return <Link to={to} className="block hover:-translate-y-1 transition-transform">{content}</Link>
    if (onClick) return <button onClick={onClick} className="block w-full text-left hover:-translate-y-1 transition-transform">{content}</button>
    return content
}

function RevenueBreakdownModal({ data, onClose, t, currency, fmt }) {
    const rows = data?.dailyBreakdown ?? []
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={onClose}>
            <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />
            <div className="relative bg-primary-900 border border-primary-700/50 rounded-2xl w-full max-w-lg max-h-[80vh] flex flex-col shadow-2xl"
                 onClick={e => e.stopPropagation()}>
                <div className="flex items-center justify-between px-6 py-4 border-b border-primary-700/40">
                    <h2 className="text-white font-semibold">{t('adminDashboard.revenueModal.title')}</h2>
                    <button onClick={onClose} className="text-primary-400 hover:text-white transition-colors text-xl leading-none">✕</button>
                </div>

                <div className="overflow-y-auto flex-1">
                    {rows.length === 0 ? (
                        <p className="text-primary-500 text-sm text-center py-10">{t('adminDashboard.revenueModal.noData')}</p>
                    ) : (
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="text-primary-500 text-xs uppercase tracking-wider border-b border-primary-700/40">
                                    <th className="text-left px-6 py-3">{t('adminDashboard.revenueModal.date')}</th>
                                    <th className="text-right px-6 py-3">{t('adminDashboard.revenueModal.amount')}</th>
                                    <th className="text-right px-6 py-3">{t('adminDashboard.revenueModal.transactions')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {rows.map(row => (
                                    <tr key={row.date} className="border-b border-primary-800/60 hover:bg-primary-800/30 transition-colors">
                                        <td className="px-6 py-3 text-primary-300 font-mono">{row.date}</td>
                                        <td className="px-6 py-3 text-amber-400 font-semibold text-right">{currency(row.amount)}</td>
                                        <td className="px-6 py-3 text-primary-400 text-right">{fmt(row.count)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>

                {rows.length > 0 && (
                    <div className="flex items-center justify-between px-6 py-4 border-t border-primary-700/40 bg-primary-800/30 rounded-b-2xl">
                        <span className="text-primary-400 text-sm font-semibold">{t('adminDashboard.revenueModal.total')}</span>
                        <span className="text-amber-400 font-bold text-lg">{currency(data?.totalRevenue)}</span>
                    </div>
                )}
            </div>
        </div>
    )
}

export default function AdminDashboardPage() {
    const [stats, setStats]         = useState(null)
    const [loading, setLoading]     = useState(true)
    const [breakdown, setBreakdown] = useState(null)
    const { t } = useTranslation()

    const fetchStats = async () => {
        setLoading(true)
        try { const res = await api.get('/admin/dashboard'); setStats(res.data.data) }
        catch { toast.error(t('adminDashboard.loadFailed')) }
        finally { setLoading(false) }
    }

    useEffect(() => { fetchStats() }, [])

    const fmt      = (n) => (n ?? 0).toLocaleString('en-IN')
    const currency = (n) => `₹${Number(n ?? 0).toLocaleString('en-IN')}`

    const openRevenueBreakdown = async () => {
        const today = new Date()
        const from  = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-01`
        const to    = today.toISOString().slice(0, 10)
        try {
            const res = await api.get(`/admin/reports/revenue?from=${from}&to=${to}`)
            setBreakdown(res.data.data)
        } catch {
            toast.error(t('adminDashboard.revenueModal.loadFailed'))
        }
    }

    if (loading) return (
        <div>
            <div className="mb-8"><div className="shimmer h-8 w-48 rounded-xl mb-2" /><div className="shimmer h-4 w-32 rounded-xl" /></div>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
                {[1,2,3,4,5,6,7,8].map(i => <div key={i} className="shimmer h-28 rounded-2xl" />)}
            </div>
        </div>
    )

    const quickLinks = [
        { to: '/admin/students',  icon: '👥', key: 'manageStudents' },
        { to: '/admin/seats',     icon: '⊞',  key: 'seatMap' },
        { to: '/admin/reminders', icon: '🔔', key: 'sendReminders' },
        { to: '/admin/reminders', icon: '📊', key: 'revenueReport' },
    ]

    return (
        <div>
            {breakdown && (
                <RevenueBreakdownModal
                    data={breakdown}
                    onClose={() => setBreakdown(null)}
                    t={t}
                    currency={currency}
                    fmt={fmt}
                />
            )}

            <div className="flex items-center justify-between mb-8">
                <div>
                    <h1 className="page-header">{t('adminDashboard.title')}</h1>
                    <p className="text-primary-400">{t('adminDashboard.subtitle')}</p>
                </div>
                <button onClick={fetchStats} className="btn-ghost border border-primary-700/40 text-sm px-4 py-2 rounded-xl">↻ {t('adminDashboard.refresh')}</button>
            </div>

            <p className="text-primary-500 text-xs uppercase tracking-widest mb-3">{t('adminDashboard.students')}</p>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
                <StatCard icon="👥" label={t('adminDashboard.stats.totalStudents')}     value={fmt(stats?.totalStudents)}    color="blue"    to="/admin/students" />
                <StatCard icon="✅" label={t('adminDashboard.stats.activeStudents')}    value={fmt(stats?.activeStudents)}   color="emerald" to="/admin/students" />
                <StatCard icon="📋" label={t('adminDashboard.stats.activeMemberships')} value={fmt(stats?.activeMemberships)} color="amber" />
                <StatCard icon="⚠️" label={t('adminDashboard.stats.expiringThisWeek')} value={fmt(stats?.expiringThisWeek)} color="red" to="/admin/reminders" sub={t('adminDashboard.stats.needsAttention')} />
            </div>

            <p className="text-primary-500 text-xs uppercase tracking-widest mb-3">{t('adminDashboard.seats')}</p>
            <div className="grid grid-cols-3 gap-4 mb-8">
                <StatCard icon="⊞"  label={t('adminDashboard.stats.totalSeats')}  value={fmt(stats?.totalSeats)}      color="blue" />
                <StatCard icon="🔴" label={t('adminDashboard.stats.occupied')}     value={fmt(stats?.occupiedSeats)}   color="red"    to="/admin/seats" />
                <StatCard icon="🟢" label={t('adminDashboard.stats.available')}    value={fmt(stats?.availableSeats)}  color="emerald" to="/admin/seats" />
            </div>

            <p className="text-primary-500 text-xs uppercase tracking-widest mb-3">{t('adminDashboard.revenue')}</p>
            <div className="grid grid-cols-2 gap-4 mb-8">
                <StatCard icon="💰" label={t('adminDashboard.stats.revenueToday')}  value={currency(stats?.revenueToday)}     color="emerald" />
                <StatCard icon="📈" label={t('adminDashboard.stats.revenueMonth')}  value={currency(stats?.revenueThisMonth)} color="amber"
                    sub={t('adminDashboard.stats.transactions', { count: fmt(stats?.paymentsThisMonth) })}
                    onClick={openRevenueBreakdown} />
            </div>

            <p className="text-primary-500 text-xs uppercase tracking-widest mb-3">{t('adminDashboard.visitors')}</p>
            <div className="grid grid-cols-2 gap-4 mb-8">
                <StatCard icon="👁️" label={t('adminDashboard.stats.totalVisitors')} value={fmt(stats?.totalVisitors)} color="blue" />
                <StatCard icon="📅" label={t('adminDashboard.stats.visitorsToday')} value={fmt(stats?.visitorsToday)} color="emerald" />
            </div>

            <div className="card p-6 mb-6">
                <div className="flex justify-between mb-3">
                    <span className="text-white font-semibold">{t('adminDashboard.occupancy.title')}</span>
                    <span className="text-amber-400 font-bold">
                        {stats ? Math.round((stats.occupiedSeats / stats.totalSeats) * 100) : 0}%
                    </span>
                </div>
                <div className="h-4 bg-primary-800 rounded-full overflow-hidden">
                    <div className="h-full bg-gradient-to-r from-emerald-500 to-amber-500 rounded-full transition-all duration-700"
                         style={{ width: `${stats ? Math.round((stats.occupiedSeats / stats.totalSeats) * 100) : 0}%` }} />
                </div>
                <div className="flex justify-between text-xs text-primary-500 mt-2">
                    <span>{t('adminDashboard.occupancy.occupied', { count: fmt(stats?.occupiedSeats) })}</span>
                    <span>{t('adminDashboard.occupancy.available', { count: fmt(stats?.availableSeats) })}</span>
                </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                {quickLinks.map(({ to, icon, key }) => (
                    <Link key={key} to={to} className="card-hover p-4 block">
                        <div className="text-xl mb-2">{icon}</div>
                        <p className="text-white text-sm font-semibold">{t(`adminDashboard.quickLinks.${key}.label`)}</p>
                        <p className="text-primary-500 text-xs mt-1">{t(`adminDashboard.quickLinks.${key}.desc`)}</p>
                    </Link>
                ))}
            </div>
        </div>
    )
}
