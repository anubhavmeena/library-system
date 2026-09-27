import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

const PAGE_SIZE_OPTIONS = [25, 50, 100, 'all']
const DATE_FILTER_OPTIONS = ['all', 'today', 'yesterday', 'last7', 'last30', 'thisMonth']

// Full class strings (not built dynamically) so Tailwind's purge keeps them.
const BADGE = {
    red:     'bg-red-500/20 text-red-400 border-red-500/30',
    rose:    'bg-rose-500/20 text-rose-400 border-rose-500/30',
    orange:  'bg-orange-500/20 text-orange-400 border-orange-500/30',
    amber:   'bg-amber-500/20 text-amber-400 border-amber-500/30',
    yellow:  'bg-yellow-500/20 text-yellow-300 border-yellow-500/30',
    lime:    'bg-lime-500/20 text-lime-400 border-lime-500/30',
    green:   'bg-green-500/20 text-green-400 border-green-500/30',
    emerald: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30',
    teal:    'bg-teal-500/20 text-teal-400 border-teal-500/30',
    cyan:    'bg-cyan-500/20 text-cyan-400 border-cyan-500/30',
    sky:     'bg-sky-500/20 text-sky-400 border-sky-500/30',
    blue:    'bg-blue-500/20 text-blue-400 border-blue-500/30',
    indigo:  'bg-indigo-500/20 text-indigo-400 border-indigo-500/30',
    violet:  'bg-violet-500/20 text-violet-400 border-violet-500/30',
    purple:  'bg-purple-500/20 text-purple-400 border-purple-500/30',
    fuchsia: 'bg-fuchsia-500/20 text-fuchsia-400 border-fuchsia-500/30',
    pink:    'bg-pink-500/20 text-pink-400 border-pink-500/30',
    slate:   'bg-slate-500/20 text-slate-300 border-slate-500/30',
}

// Roughly grouped by meaning: red/rose = destructive, green/emerald = money
// and creation, blue/sky = seat changes, violet/purple = messaging,
// amber/orange = membership state, slate = settings/maintenance.
const ACTION_COLORS = {
    DELETE_STUDENT: 'red', DELETE_COUPON: 'red', DELETE_BROADCAST: 'red', DELETE_INBOX_MESSAGE: 'red',
    RELEASE_SEAT: 'rose', DEACTIVATE_SEAT: 'rose',
    CLEAR_DUES: 'emerald', CLEAR_PENDING_FEES: 'emerald',
    CREATE_PAY_LINK: 'teal', REVIEW_PAYMENT_CLAIM: 'teal', SAVE_EXPENSE: 'teal',
    ADD_STUDENT: 'green', BULK_IMPORT: 'green', CREATE_MEMBERSHIP: 'lime', RENEW_SEAT: 'lime',
    CHANGE_SEAT: 'blue', SWAP_SEAT: 'sky', ACTIVATE_SEAT: 'cyan',
    UPDATE_STUDENT: 'indigo', UPDATE_PHOTO: 'indigo', UPDATE_STATUS: 'indigo', UPDATE_PLAN: 'indigo',
    SEND_MESSAGE: 'violet', SEND_RECEIPT: 'violet', SEND_ID_CARD: 'violet',
    SEND_REMINDERS: 'purple', SEND_RENEWAL_POLL: 'purple', BROADCAST: 'fuchsia', REPLY_INBOX_MESSAGE: 'pink',
    MARK_PENDING: 'orange', MARK_GRACE: 'amber', RUN_EXPIRY_CHECK: 'yellow',
    UPDATE_FEEDBACK: 'pink', CREATE_COUPON: 'fuchsia', UPDATE_COUPON: 'fuchsia',
    SAVE_SETTINGS: 'slate', UPDATE_NOTIFICATION_SETTING: 'slate',
}
const FALLBACK_COLORS = Object.keys(BADGE)

// Unknown (newly added) actions still get a stable color from a name hash.
function actionBadgeClass(action) {
    let color = ACTION_COLORS[action]
    if (!color) {
        let hash = 0
        for (const ch of action || '') hash = (hash * 31 + ch.charCodeAt(0)) >>> 0
        color = FALLBACK_COLORS[hash % FALLBACK_COLORS.length]
    }
    return BADGE[color]
}

// The backend sends a naive "YYYY-MM-DDTHH:MM:SS[.ffffff]" timestamp that is
// actually UTC (rust-backend stores plain TIMESTAMP columns as UTC wall-clock
// -- see rust-backend/CLAUDE.md). Appending "Z" before parsing tells the
// browser it's UTC instead of guessing local time, so the Asia/Kolkata
// conversion below lands on the real IST moment.
function formatIST(createdAt) {
    if (!createdAt) return ''
    const date = new Date(createdAt.endsWith('Z') ? createdAt : `${createdAt}Z`)
    if (Number.isNaN(date.getTime())) return createdAt
    return date.toLocaleString('en-IN', {
        timeZone: 'Asia/Kolkata',
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
    })
}

// Inclusive IST [from, to] as YYYY-MM-DD for a date-filter option; the
// backend compares against created_at shifted to IST.
function dateRangeFor(option) {
    if (option === 'all') return {}
    const todayIST = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Kolkata' })
    const daysAgo = (n) => {
        const d = new Date(`${todayIST}T00:00:00Z`)
        d.setUTCDate(d.getUTCDate() - n)
        return d.toISOString().slice(0, 10)
    }
    switch (option) {
        case 'today':     return { from: todayIST, to: todayIST }
        case 'yesterday': return { from: daysAgo(1), to: daysAgo(1) }
        case 'last7':     return { from: daysAgo(6), to: todayIST }
        case 'last30':    return { from: daysAgo(29), to: todayIST }
        case 'thisMonth': return { from: `${todayIST.slice(0, 8)}01`, to: todayIST }
        default:          return {}
    }
}

// Wraps each mention of a resolved student in the description with a link to
// their profile. Prefers the full "Name (mobile)" label the backend writes,
// falling back to the bare name (e.g. "Updated profile for Name").
function linkifyStudents(text, students) {
    if (!text || !students?.length) return text
    const matches = []
    for (const s of students) {
        const candidates = s.mobile ? [`${s.name} (${s.mobile})`, s.name] : [s.name]
        for (const label of candidates) {
            if (!label) continue
            let idx = text.indexOf(label)
            if (idx === -1) continue
            while (idx !== -1) {
                matches.push({ start: idx, end: idx + label.length, id: s.id })
                idx = text.indexOf(label, idx + label.length)
            }
            break
        }
    }
    if (!matches.length) return text

    matches.sort((a, b) => a.start - b.start || b.end - a.end)
    const parts = []
    let cursor = 0
    for (const m of matches) {
        if (m.start < cursor) continue
        if (m.start > cursor) parts.push(text.slice(cursor, m.start))
        parts.push(
            <Link key={`${m.id}-${m.start}`} to={`/admin/students/${m.id}`}
                  className="text-primary-300 hover:text-white underline underline-offset-2 decoration-primary-500/60">
                {text.slice(m.start, m.end)}
            </Link>
        )
        cursor = m.end
    }
    if (cursor < text.length) parts.push(text.slice(cursor))
    return parts
}

export default function AdminActivityLogsPage() {
    const { t } = useTranslation()
    const [logs, setLogs]         = useState([])
    const [total, setTotal]       = useState(0)
    const [loading, setLoading]   = useState(true)
    const [page, setPage]         = useState(0)
    const [pageSize, setPageSize] = useState(100)

    const [searchInput, setSearchInput] = useState('')
    const [search, setSearch]           = useState('')
    const [dateFilter, setDateFilter]   = useState('all')
    const [adminFilter, setAdminFilter] = useState('all')
    const [actionFilter, setActionFilter] = useState('all')
    const [filterOptions, setFilterOptions] = useState({ admins: [], actions: [] })

    const hasFilters = search !== '' || dateFilter !== 'all' || adminFilter !== 'all' || actionFilter !== 'all'

    // Debounce typing so each keystroke doesn't hit the backend.
    useEffect(() => {
        const id = setTimeout(() => {
            setSearch(searchInput.trim())
            setPage(0)
        }, 300)
        return () => clearTimeout(id)
    }, [searchInput])

    const fetchLogs = () => {
        setLoading(true)
        const params = { page, size: pageSize, ...dateRangeFor(dateFilter) }
        if (search) params.q = search
        if (adminFilter !== 'all') params.adminId = adminFilter
        if (actionFilter !== 'all') params.action = actionFilter
        api.get('/admin/activity-logs', { params })
            .then(res => {
                const data = res.data.data
                setLogs(data.logs || [])
                setTotal(data.total || 0)
                if (data.filters) setFilterOptions(data.filters)
            })
            .catch(() => toast.error(t('adminActivityLogs.toasts.loadFailed')))
            .finally(() => setLoading(false))
    }

    useEffect(() => { fetchLogs() }, [page, pageSize, search, dateFilter, adminFilter, actionFilter])

    const handlePageSizeChange = (value) => {
        setPageSize(value === 'all' ? 'all' : Number(value))
        setPage(0)
    }

    const withPageReset = (setter) => (e) => {
        setter(e.target.value)
        setPage(0)
    }

    const clearFilters = () => {
        setSearchInput('')
        setSearch('')
        setDateFilter('all')
        setAdminFilter('all')
        setActionFilter('all')
        setPage(0)
    }

    const filterSelectClass = 'input text-xs py-1 px-2 mt-1.5 w-full min-w-[7rem] font-normal'

    const columns = [
        {
            key: 'date',
            label: t('adminActivityLogs.table.date'),
            filter: (
                <select value={dateFilter} onChange={withPageReset(setDateFilter)} className={filterSelectClass}>
                    {DATE_FILTER_OPTIONS.map(o => (
                        <option key={o} value={o}>{t(`adminActivityLogs.dateFilter.${o}`)}</option>
                    ))}
                </select>
            ),
        },
        {
            key: 'admin',
            label: t('adminActivityLogs.table.admin'),
            filter: (
                <select value={adminFilter} onChange={withPageReset(setAdminFilter)} className={filterSelectClass}>
                    <option value="all">{t('adminActivityLogs.all')}</option>
                    {filterOptions.admins.map(a => (
                        <option key={a.id} value={a.id}>{a.name}{a.mobile ? ` (${a.mobile})` : ''}</option>
                    ))}
                </select>
            ),
        },
        {
            key: 'action',
            label: t('adminActivityLogs.table.action'),
            filter: (
                <select value={actionFilter} onChange={withPageReset(setActionFilter)} className={filterSelectClass}>
                    <option value="all">{t('adminActivityLogs.all')}</option>
                    {filterOptions.actions.map(a => <option key={a} value={a}>{a}</option>)}
                </select>
            ),
        },
        { key: 'description', label: t('adminActivityLogs.table.description') },
    ]

    return (
        <div>
            <div className="flex items-start justify-between mb-6 gap-4">
                <div>
                    <h1 className="page-header">{t('adminActivityLogs.title')}</h1>
                    <p className="text-primary-400 mt-1">{t('adminActivityLogs.subtitle', { count: total })}</p>
                </div>
                <button onClick={fetchLogs}
                    className="btn-ghost border border-primary-700/40 text-sm px-4 py-2 rounded-xl flex-shrink-0">
                    ↻ {t('adminActivityLogs.refresh')}
                </button>
            </div>

            <div className="flex flex-col sm:flex-row gap-3 mb-4">
                <div className="relative flex-1">
                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-primary-500 pointer-events-none">🔍</span>
                    <input type="search" value={searchInput} onChange={e => setSearchInput(e.target.value)}
                           placeholder={t('adminActivityLogs.searchPlaceholder')}
                           className="input w-full pl-10" />
                </div>
                {hasFilters && (
                    <button onClick={clearFilters}
                            className="btn-ghost border border-primary-700/40 text-sm px-4 py-2 rounded-xl flex-shrink-0">
                        ✕ {t('adminActivityLogs.clearFilters')}
                    </button>
                )}
            </div>

            {!loading && logs.length === 0 && !hasFilters ? (
                <div className="card p-12 text-center">
                    <p className="text-4xl mb-3">📋</p>
                    <p className="text-white font-semibold">{t('adminActivityLogs.empty.title')}</p>
                    <p className="text-primary-400 text-sm mt-1">{t('adminActivityLogs.empty.desc')}</p>
                </div>
            ) : (
                <div className="card overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-primary-700/40">
                                    {columns.map(col => (
                                        <th key={col.key} className="p-4 text-left text-primary-400 font-medium whitespace-nowrap align-top">
                                            {col.label}
                                            {col.filter}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-primary-700/20">
                                {loading ? (
                                    [1, 2, 3, 4, 5].map(i => (
                                        <tr key={i}><td colSpan={columns.length} className="p-2"><div className="shimmer h-10 rounded-xl" /></td></tr>
                                    ))
                                ) : logs.length === 0 ? (
                                    <tr>
                                        <td colSpan={columns.length} className="p-10 text-center text-primary-400">
                                            {t('adminActivityLogs.noMatches')}
                                        </td>
                                    </tr>
                                ) : logs.map(entry => (
                                    <tr key={entry.id} className="hover:bg-primary-800/30 transition-colors">
                                        <td className="p-4 text-primary-400 text-xs whitespace-nowrap align-top">
                                            {formatIST(entry.createdAt)}
                                        </td>
                                        <td className="p-4 text-white font-medium whitespace-nowrap align-top">
                                            {entry.adminName}{entry.adminMobile ? ` (${entry.adminMobile})` : ''}
                                        </td>
                                        <td className="p-4 align-top">
                                            <span className={`text-xs px-2 py-1 rounded-full border font-medium whitespace-nowrap ${actionBadgeClass(entry.action)}`}>
                                                {entry.action}
                                            </span>
                                        </td>
                                        <td className="p-4 text-primary-200">
                                            {linkifyStudents(entry.description, entry.students)}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <div className="flex items-center justify-between p-4 border-t border-primary-700/30">
                        <div className="flex items-center gap-3">
                            <span className="text-primary-400 text-sm">{t('adminActivityLogs.page', { page: page + 1 })}</span>
                            <span className="text-primary-500 text-xs">{t('adminActivityLogs.perPage')}</span>
                            <select value={pageSize} onChange={e => handlePageSizeChange(e.target.value)}
                                    className="input text-sm py-1 w-24">
                                {PAGE_SIZE_OPTIONS.map(n => (
                                    <option key={n} value={n}>{n === 'all' ? t('adminActivityLogs.all') : n}</option>
                                ))}
                            </select>
                        </div>
                        <div className="flex gap-2">
                            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={pageSize === 'all' || page === 0}
                                    className="btn-ghost disabled:opacity-40 text-sm px-3 py-1.5 border border-primary-700/40 rounded-lg">← {t('adminActivityLogs.prev')}</button>
                            <button onClick={() => setPage(p => p + 1)} disabled={pageSize === 'all' || (page + 1) * pageSize >= total}
                                    className="btn-ghost disabled:opacity-40 text-sm px-3 py-1.5 border border-primary-700/40 rounded-lg">{t('adminActivityLogs.next')} →</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
