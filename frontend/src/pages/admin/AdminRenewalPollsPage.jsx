import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

const PAGE_SIZE_OPTIONS = [25, 50, 100, 'all']

// The backend sends a naive "YYYY-MM-DDTHH:MM:SS[.ffffff]" timestamp that is
// actually UTC (rust-backend stores plain TIMESTAMP columns as UTC wall-clock
// -- see rust-backend/CLAUDE.md). Appending "Z" before parsing tells the
// browser it's UTC instead of guessing local time, so the Asia/Kolkata
// conversion below lands on the real IST moment.
function formatIST(value) {
    if (!value) return ''
    const date = new Date(value.endsWith('Z') ? value : `${value}Z`)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleString('en-IN', {
        timeZone: 'Asia/Kolkata',
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
    })
}

function formatDate(value) {
    if (!value) return ''
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
}

// endDate is a plain "YYYY-MM-DD" calendar date, not a timestamp — compared
// against local midnight so "days left" reads naturally (0 = expires today,
// negative = already past) regardless of the time of day the page loads.
function daysLeft(endDate) {
    if (!endDate) return null
    const end = new Date(endDate)
    if (Number.isNaN(end.getTime())) return null
    const today = new Date()
    end.setHours(0, 0, 0, 0)
    today.setHours(0, 0, 0, 0)
    return Math.round((end - today) / 86400000)
}

function DaysLeftLabel({ endDate }) {
    const n = daysLeft(endDate)
    if (n === null) return '—'
    const color = n <= 3 ? 'text-red-400' : n <= 7 ? 'text-amber-400' : 'text-primary-200'
    return <span className={color}>{n}</span>
}

function ResponseBadge({ response, t }) {
    if (response === 'YES') {
        return (
            <span className="text-xs px-2 py-1 rounded-full border font-medium whitespace-nowrap bg-emerald-500/20 text-emerald-400 border-emerald-500/30">
                {t('adminRenewalPolls.response.yes')}
            </span>
        )
    }
    if (response === 'NO') {
        return (
            <span className="text-xs px-2 py-1 rounded-full border font-medium whitespace-nowrap bg-red-500/20 text-red-400 border-red-500/30">
                {t('adminRenewalPolls.response.no')}
            </span>
        )
    }
    return (
        <span className="text-xs px-2 py-1 rounded-full border font-medium whitespace-nowrap bg-amber-500/20 text-amber-400 border-amber-500/30">
            {t('adminRenewalPolls.response.pending')}
        </span>
    )
}

export default function AdminRenewalPollsPage() {
    const { t } = useTranslation()
    const [polls, setPolls]       = useState([])
    const [total, setTotal]       = useState(0)
    const [loading, setLoading]   = useState(true)
    const [page, setPage]         = useState(0)
    const [pageSize, setPageSize] = useState(100)
    const [resendingId, setResendingId] = useState(null)
    const [releasingId, setReleasingId] = useState(null)

    const fetchPolls = () => {
        setLoading(true)
        api.get(`/admin/renewal-polls?page=${page}&size=${pageSize}`)
            .then(res => {
                setPolls(res.data.data.logs || [])
                setTotal(res.data.data.total || 0)
            })
            .catch(() => toast.error(t('adminRenewalPolls.toasts.loadFailed')))
            .finally(() => setLoading(false))
    }

    useEffect(() => { fetchPolls() }, [page, pageSize])

    const handlePageSizeChange = (value) => {
        setPageSize(value === 'all' ? 'all' : Number(value))
        setPage(0)
    }

    const handleResend = (poll) => {
        if (!window.confirm(t('adminRenewalPolls.resendConfirm', { name: poll.name }))) return
        setResendingId(poll.id)
        api.post(`/admin/renewal-polls/${poll.id}/resend`)
            .then(() => {
                toast.success(t('adminRenewalPolls.toasts.resendSuccess'))
                fetchPolls()
            })
            .catch(() => toast.error(t('adminRenewalPolls.toasts.resendFailed')))
            .finally(() => setResendingId(null))
    }

    // Mirrors StudentActionsMenu's handleReleaseSeat: a first confirm for the
    // release itself, then a second asking whether to notify the student —
    // kept as separate window.confirm steps to match that existing UX exactly.
    const handleReleaseSeat = (poll) => {
        if (!window.confirm(`Release seat ${poll.seatNumber || ''} for ${poll.name}? This cannot be undone.`)) return
        const notifyStudent = window.confirm(
            `Send ${poll.name} a notification that their seat has expired due to non-renewal and been released?\n\n` +
            `Click OK to notify them, or Cancel to release the seat quietly.`
        )
        setReleasingId(poll.id)
        api.patch(`/admin/memberships/${poll.membershipId}/release`, { notifyStudent })
            .then(() => {
                toast.success(`Seat released${notifyStudent ? ' — student notified' : ''}`)
                fetchPolls()
            })
            .catch(e => toast.error(e.response?.data?.message || 'Failed to release seat'))
            .finally(() => setReleasingId(null))
    }

    return (
        <div>
            <div className="flex items-start justify-between mb-6 gap-4">
                <div>
                    <h1 className="page-header">{t('adminRenewalPolls.title')}</h1>
                    <p className="text-primary-400 mt-1">{t('adminRenewalPolls.subtitle', { count: total })}</p>
                </div>
                <button onClick={fetchPolls}
                    className="btn-ghost border border-primary-700/40 text-sm px-4 py-2 rounded-xl flex-shrink-0">
                    ↻ {t('adminRenewalPolls.refresh')}
                </button>
            </div>

            {loading ? (
                <div className="space-y-3">
                    {[1, 2, 3, 4, 5].map(i => <div key={i} className="shimmer h-14 rounded-xl" />)}
                </div>
            ) : polls.length === 0 ? (
                <div className="card p-12 text-center">
                    <p className="text-4xl mb-3">☑</p>
                    <p className="text-white font-semibold">{t('adminRenewalPolls.empty.title')}</p>
                    <p className="text-primary-400 text-sm mt-1">{t('adminRenewalPolls.empty.desc')}</p>
                </div>
            ) : (
                <div className="card overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-primary-700/40">
                                    {[
                                        t('adminRenewalPolls.table.student'),
                                        t('adminRenewalPolls.table.seat'),
                                        t('adminRenewalPolls.table.endDate'),
                                        t('adminRenewalPolls.table.daysLeft'),
                                        t('adminRenewalPolls.table.sentAt'),
                                        t('adminRenewalPolls.table.response'),
                                        t('adminRenewalPolls.table.respondedAt'),
                                        t('adminRenewalPolls.table.actions'),
                                    ].map(h => (
                                        <th key={h} className="p-4 text-left text-primary-400 font-medium whitespace-nowrap">{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-primary-700/20">
                                {polls.map(poll => (
                                    <tr key={poll.id} className="hover:bg-primary-800/30 transition-colors">
                                        <td className="p-4 text-white font-medium whitespace-nowrap align-top">
                                            <Link to={`/admin/students/${poll.userId}`} className="hover:text-amber-400 hover:underline transition-colors">
                                                {poll.name}
                                            </Link>
                                            {poll.mobile ? ` (${poll.mobile})` : ''}
                                        </td>
                                        <td className="p-4 text-primary-200 font-mono whitespace-nowrap align-top">
                                            {poll.seatNumber || '—'}
                                        </td>
                                        <td className="p-4 text-primary-200 whitespace-nowrap align-top">
                                            {formatDate(poll.endDate)}
                                        </td>
                                        <td className="p-4 whitespace-nowrap align-top">
                                            <DaysLeftLabel endDate={poll.endDate} />
                                        </td>
                                        <td className="p-4 text-primary-400 text-xs whitespace-nowrap align-top">
                                            {formatIST(poll.sentAt)}
                                        </td>
                                        <td className="p-4 align-top">
                                            <ResponseBadge response={poll.response} t={t} />
                                        </td>
                                        <td className="p-4 text-primary-400 text-xs whitespace-nowrap align-top">
                                            {poll.respondedAt ? formatIST(poll.respondedAt) : '—'}
                                        </td>
                                        <td className="p-4 align-top">
                                            <div className="flex gap-2">
                                                <button onClick={() => handleResend(poll)} disabled={resendingId === poll.id}
                                                    className="text-xs px-3 py-1.5 rounded-lg border font-medium whitespace-nowrap bg-indigo-500/10 text-indigo-400 border-indigo-500/30 hover:bg-indigo-500/20 disabled:opacity-40">
                                                    {resendingId === poll.id ? '…' : t('adminRenewalPolls.resend')}
                                                </button>
                                                {poll.response === 'NO' && (poll.membershipStatus === 'ACTIVE' || poll.membershipStatus === 'GRACE') && (
                                                    <button onClick={() => handleReleaseSeat(poll)} disabled={releasingId === poll.id}
                                                        className="text-xs px-3 py-1.5 rounded-lg border font-medium whitespace-nowrap bg-red-500/10 text-red-400 border-red-500/30 hover:bg-red-500/20 disabled:opacity-40">
                                                        {releasingId === poll.id ? '…' : t('adminRenewalPolls.releaseSeat')}
                                                    </button>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <div className="flex items-center justify-between p-4 border-t border-primary-700/30">
                        <div className="flex items-center gap-3">
                            <span className="text-primary-400 text-sm">{t('adminRenewalPolls.page', { page: page + 1 })}</span>
                            <span className="text-primary-500 text-xs">{t('adminRenewalPolls.perPage')}</span>
                            <select value={pageSize} onChange={e => handlePageSizeChange(e.target.value)}
                                    className="input text-sm py-1 w-24">
                                {PAGE_SIZE_OPTIONS.map(n => (
                                    <option key={n} value={n}>{n === 'all' ? t('adminRenewalPolls.all') : n}</option>
                                ))}
                            </select>
                        </div>
                        <div className="flex gap-2">
                            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={pageSize === 'all' || page === 0}
                                    className="btn-ghost disabled:opacity-40 text-sm px-3 py-1.5 border border-primary-700/40 rounded-lg">← {t('adminRenewalPolls.prev')}</button>
                            <button onClick={() => setPage(p => p + 1)} disabled={pageSize === 'all' || (page + 1) * pageSize >= total}
                                    className="btn-ghost disabled:opacity-40 text-sm px-3 py-1.5 border border-primary-700/40 rounded-lg">{t('adminRenewalPolls.next')} →</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
