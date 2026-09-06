import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'
import { formatCurrency } from '../../utils/currency'
import { paymentModeInfo } from '../../utils/paymentMode'
import { toDevanagari } from '../../utils/transliterate'
import StudentActionsMenu from '../../components/admin/StudentActionsMenu'

const STATUS_BADGE_CLASSES = {
    NEW:      'bg-blue-500/20 text-blue-400 border-blue-500/30',
    PAID:     'bg-emerald-500/20 text-emerald-400 border-emerald-500/30',
    PENDING:  'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
    GRACE:         'bg-orange-500/20 text-orange-400 border-orange-500/30',
    GRACE_OVERDUE: 'bg-red-500/20 text-red-400 border-red-500/30',
    RELEASED:      'bg-red-950/70 text-red-300 border-red-900',
}

export default function AdminStudentsPage() {
    const [students, setStudents] = useState([])
    const [total, setTotal]       = useState(0)
    const [loading, setLoading]   = useState(true)
    const [page, setPage]             = useState(0)
    const [pageSize, setPageSize]     = useState(20)
    const [sortBy,  setSortBy]        = useState('createdAt')
    const [sortDir, setSortDir]       = useState('desc')
    const [membershipFilter, setMembershipFilter] = useState('')
    const [search, setSearch]         = useState('')
    const [debouncedSearch, setDebouncedSearch] = useState('')
    const [detail, setDetail]     = useState(null)

    const [studentPayments, setStudentPayments]               = useState([])
    const [studentPaymentsLoading, setStudentPaymentsLoading] = useState(false)

    const { t, i18n } = useTranslation()
    const localizeName = (name) => (i18n.language?.startsWith('hi') ? toDevanagari(name) : name)

    const fetchStudents = async () => {
        setLoading(true)
        try {
            const res = await api.get(`/admin/students?page=${page}&size=${pageSize}&sortBy=${sortBy}&sortDir=${sortDir}${membershipFilter ? `&membershipStatus=${membershipFilter}` : ''}${debouncedSearch ? `&search=${encodeURIComponent(debouncedSearch)}` : ''}`)
            setStudents(res.data.data.students || [])
            setTotal(res.data.data.total || 0)
        } catch { toast.error(t('adminStudents.toasts.loadFailed')) }
        finally { setLoading(false) }
    }

    useEffect(() => {
        const t = setTimeout(() => {
            setPage(0)
            setDebouncedSearch(search)
        }, 300)
        return () => clearTimeout(t)
    }, [search])

    useEffect(() => { fetchStudents() }, [page, membershipFilter, debouncedSearch, pageSize, sortBy, sortDir])

    useEffect(() => {
        if (!detail) { setStudentPayments([]); return }
        setStudentPaymentsLoading(true)
        api.get(`/admin/students/${detail.id}/payments`)
            .then(r => setStudentPayments(r.data.data || []))
            .catch(() => setStudentPayments([]))
            .finally(() => setStudentPaymentsLoading(false))
    }, [detail])

    const shiftLabel = (shift) => {
        if (shift === 'MORNING')  return t('adminStudents.shifts.MORNING')
        if (shift === 'EVENING')  return t('adminStudents.shifts.EVENING')
        if (shift === 'FULL_DAY') return t('adminStudents.shifts.FULL_DAY')
        return '—'
    }

    // Fetches fresh details (not the list row, which lacks plan info — see
    // AdminService.getAllStudents vs getStudentDetails) before opening the modal.
    const openStudentDetail = async (student) => {
        try {
            const res = await api.get(`/admin/students/${student.id}`)
            setDetail(res.data.data)
        } catch {
            toast.error('Failed to load student details')
        }
    }

    const handleSort = (col) => {
        if (sortBy === col) setSortDir(d => d === 'asc' ? 'desc' : 'asc')
        else { setSortBy(col); setSortDir('asc') }
        setPage(0)
    }

    const sortIcon = (col) => {
        if (sortBy !== col) return <span className="ml-1 text-primary-700">↕</span>
        return <span className="ml-1 text-amber-400">{sortDir === 'asc' ? '↑' : '↓'}</span>
    }

    const membershipFilters = [
        { v: '',          l: t('adminStudents.filters.membershipAll') },
        { v: 'NEW',       l: t('adminStudents.statusLabels.NEW') },
        { v: 'PAID',      l: t('adminStudents.statusLabels.PAID') },
        { v: 'PENDING',   l: t('adminStudents.statusLabels.PENDING') },
        { v: 'GRACE',          l: t('adminStudents.statusLabels.GRACE') },
        { v: 'GRACE_OVERDUE',  l: t('adminStudents.statusLabels.GRACE_OVERDUE') },
        { v: 'RELEASED',       l: t('adminStudents.statusLabels.RELEASED') },
    ]

    const headerCols = [
        { l: t('adminStudents.table.student'),    col: 'name' },
        { l: t('adminStudents.table.contact'),    col: 'mobile' },
        { l: t('adminStudents.table.seatShift'),  col: 'seatNumber' },
        { l: t('adminStudents.table.membership'), col: 'endDate' },
        { l: t('adminStudents.table.payment'),    col: 'paymentMode' },
        { l: 'Pending',                           col: 'pendingAmount' },
        { l: 'Dues',                              col: null },
        { l: t('adminStudents.table.status'),     col: null },
        { l: t('adminStudents.table.actions'),    col: null },
    ]

    return (
        <div>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h1 className="page-header">{t('adminStudents.title')}</h1>
                    <p className="text-primary-400">{t('adminStudents.subtitle', { count: students.length, total })}</p>
                </div>
                <button onClick={fetchStudents} className="btn-ghost border border-primary-700/40 text-sm px-4 py-2 rounded-xl">↻ {t('adminStudents.refresh')}</button>
            </div>

            <div className="flex flex-wrap gap-3 mb-3">
                <input className="input w-64 text-sm py-2" placeholder={t('adminStudents.searchPlaceholder')}
                       value={search} onChange={e => setSearch(e.target.value)} />
            </div>
            <div className="flex flex-wrap gap-2 mb-6">
                <div className="flex items-center gap-2">
                    <span className="text-primary-500 text-xs">{t('adminStudents.filters.membershipLabel')}</span>
                    {membershipFilters.map(({ v, l }) => (
                        <button key={v} onClick={() => { setMembershipFilter(v); setPage(0) }}
                                className={`px-3 py-2 rounded-xl text-sm font-medium border transition-all
                ${membershipFilter === v ? 'bg-amber-500/20 border-amber-400/60 text-amber-400' : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                            {l}
                        </button>
                    ))}
                </div>
            </div>

            {loading ? (
                <div className="space-y-3">{[1,2,3,4,5].map(i => <div key={i} className="shimmer h-16 rounded-xl" />)}</div>
            ) : students.length === 0 ? (
                <div className="card p-12 text-center">
                    <p className="text-4xl mb-3">👥</p>
                    <p className="text-white font-semibold">{t('adminStudents.empty')}</p>
                </div>
            ) : (
                <div className="card overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                            <tr className="border-b border-primary-700/40">
                                {headerCols.map(({ l, col }) => (
                                    <th key={l}
                                        onClick={col ? () => handleSort(col) : undefined}
                                        className={`p-4 text-left text-primary-400 font-medium select-none
                                            ${col ? 'cursor-pointer hover:text-white transition-colors' : ''}`}>
                                        {l}{col && sortIcon(col)}
                                    </th>
                                ))}
                            </tr>
                            </thead>
                            <tbody className="divide-y divide-primary-700/20">
                            {students.map(s => (
                                <tr key={s.id} className="hover:bg-primary-800/30 transition-colors">
                                    <td className="p-4">
                                        <div className="flex items-center gap-3">
                                            {s.photoUrl
                                                ? <img src={s.photoUrl} alt={s.name} className="w-9 h-9 rounded-full object-cover" />
                                                : <div className="w-9 h-9 rounded-full bg-gradient-to-br from-red-400 to-primary-600 flex items-center justify-center text-sm font-bold text-white">
                                                    {s.name?.[0]?.toUpperCase()}
                                                </div>
                                            }
                                            <div>
                                                <Link to={`/admin/students/${s.id}`}
                                                    className="block text-white font-medium hover:text-amber-400 hover:underline transition-colors">
                                                    {localizeName(s.name)}
                                                </Link>
                                                <p className="text-primary-500 text-xs">{t('adminStudents.joined')} {s.joinedAt?.split('T')[0]}</p>
                                            </div>
                                        </div>
                                    </td>
                                    <td className="p-4">
                                        <p className="text-primary-300">{s.mobile || '—'}</p>
                                        <p className="text-primary-500 text-xs truncate max-w-[160px]">{s.email || '—'}</p>
                                    </td>
                                    <td className="p-4">
                                        <p className="text-white font-mono">{s.seatNumber || '—'}</p>
                                        <p className="text-primary-500 text-xs">{shiftLabel(s.shift)}</p>
                                    </td>
                                    <td className="p-4">
                                        {s.displayStatus === 'RELEASED' ? (
                                            <span className="text-primary-600 text-xs">{t('adminStudents.noPlan')}</span>
                                        ) : s.membershipStatus === 'GRACE' ? (
                                            <>
                                                <p className="text-primary-300 text-xs">{t('adminStudents.expires')} {s.membershipEnd}</p>
                                                <p className="text-xs font-semibold text-red-400">
                                                    {Math.max(0, Math.ceil((new Date() - new Date(s.membershipEnd)) / 86400000))}d overdue — {s.displayStatus === 'GRACE_OVERDUE' ? 'expired' : 'grace'}
                                                </p>
                                            </>
                                        ) : s.membershipEnd ? (
                                            <>
                                                <p className="text-primary-300 text-xs">{t('adminStudents.expires')} {s.membershipEnd}</p>
                                                <p className={`text-xs font-semibold ${s.daysRemaining <= 3 ? 'text-red-400' : s.daysRemaining <= 7 ? 'text-amber-400' : 'text-emerald-400'}`}>
                                                    {t('adminStudents.daysLeft', { count: s.daysRemaining })}
                                                </p>
                                            </>
                                        ) : (
                                            <span className="text-primary-600 text-xs">{t('adminStudents.noPlan')}</span>
                                        )}
                                    </td>
                                    <td className="p-4">
                                        {(() => {
                                            const info = paymentModeInfo(s.paymentMode, t)
                                            return info ? (
                                                <span className={`text-xs px-2 py-1 rounded-full border ${info.className}`}>{info.emoji} {info.label}</span>
                                            ) : (
                                                <span className="text-primary-600 text-xs">—</span>
                                            )
                                        })()}
                                    </td>
                                    <td className="p-4">
                                        {s.pendingAmount > 0 ? (
                                            <span className="text-red-400 font-semibold text-sm">{formatCurrency(s.pendingAmount)}</span>
                                        ) : (
                                            <span className="text-primary-600 text-xs">—</span>
                                        )}
                                    </td>
                                    <td className="p-4">
                                        {s.duesAmount > 0 ? (
                                            <span className="text-red-400 font-semibold text-sm">{formatCurrency(s.duesAmount)}</span>
                                        ) : (
                                            <span className="text-primary-600 text-xs">—</span>
                                        )}
                                    </td>
                                    <td className="p-4">
                                        <span className={`badge border text-xs px-2 py-1 rounded-full ${STATUS_BADGE_CLASSES[s.displayStatus] || 'bg-primary-700/30 text-primary-400 border-primary-700/40'}`}>
                                            {t(`adminStudents.statusLabels.${s.displayStatus}`)}
                                        </span>
                                    </td>
                                    <td className="p-4">
                                        <div className="flex items-center gap-2">
                                            <button onClick={() => openStudentDetail(s)}
                                                    className="text-xs px-3 py-1.5 rounded-lg bg-primary-700/50 text-primary-300 hover:text-white border border-primary-700/40 transition-all">
                                                {t('adminStudents.view')}
                                            </button>
                                            <StudentActionsMenu student={s} onMutated={fetchStudents} onDeleted={fetchStudents} />
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                    <div className="flex items-center justify-between p-4 border-t border-primary-700/30">
                        <div className="flex items-center gap-3">
                            <span className="text-primary-400 text-sm">{t('adminStudents.page', { page: page + 1 })}</span>
                            <select value={pageSize} onChange={e => { setPageSize(Number(e.target.value)); setPage(0) }}
                                    className="input text-sm py-1 w-24">
                                {[10, 20, 50, 100].map(n => <option key={n} value={n}>{n} / page</option>)}
                            </select>
                        </div>
                        <div className="flex gap-2">
                            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
                                    className="btn-ghost disabled:opacity-40 text-sm px-3 py-1.5 border border-primary-700/40 rounded-lg">← {t('adminStudents.prev')}</button>
                            <button onClick={() => setPage(p => p + 1)} disabled={students.length < pageSize}
                                    className="btn-ghost disabled:opacity-40 text-sm px-3 py-1.5 border border-primary-700/40 rounded-lg">{t('adminStudents.next')} →</button>
                        </div>
                    </div>
                </div>
            )}


            {detail && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={() => setDetail(null)}>
                    <div className="card p-6 w-full max-w-md border-primary-700/30 max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
                        {/* Header */}
                        <div className="flex items-center justify-between mb-5">
                            <h3 className="section-title">{t('adminStudents.modal.title')}</h3>
                            <button onClick={() => setDetail(null)} className="text-primary-400 hover:text-white ml-1">✕</button>
                        </div>

                        {/* Avatar + name */}
                        <div className="flex items-center gap-4 mb-5 pb-5 border-b border-primary-700/30">
                            {detail.photoUrl
                                ? <img src={detail.photoUrl} alt={detail.name} className="w-14 h-14 rounded-full object-cover flex-shrink-0" />
                                : <div className="w-14 h-14 rounded-full bg-gradient-to-br from-red-400 to-primary-600 flex items-center justify-center text-xl font-bold text-white flex-shrink-0">
                                    {detail.name?.[0]?.toUpperCase()}
                                </div>
                            }
                            <div className="min-w-0">
                                <p className="text-white font-bold text-lg truncate">{localizeName(detail.name)}</p>
                                <p className="text-primary-400 text-sm">{detail.mobile}</p>
                            </div>
                        </div>

                        {/* Fields (read-only — edit via the student detail page) */}
                        <div className="space-y-2">
                            {[
                                { key: 'mobile',      label: t('adminStudents.modal.mobile') },
                                { key: 'email',       label: t('adminStudents.modal.email') },
                                { key: 'address',     label: t('adminStudents.modal.address') },
                                { key: 'dateOfBirth', label: 'Date of Birth' },
                            ].map(({ key, label }) => (
                                <div key={key} className="flex justify-between items-center py-1.5 border-b border-primary-700/20 text-sm gap-4">
                                    <span className="text-primary-400 shrink-0">{label}</span>
                                    <span className="text-white text-right max-w-[55%] truncate">{detail[key] || '—'}</span>
                                </div>
                            ))}

                            <div className="flex justify-between items-center py-1.5 border-b border-primary-700/20 text-sm gap-4">
                                <span className="text-primary-400 shrink-0">{t('adminStudents.modal.gender')}</span>
                                <span className="text-white">{detail.gender || '—'}</span>
                            </div>

                            <div className="flex justify-between items-center py-1.5 border-b border-primary-700/20 text-sm gap-4">
                                <span className="text-primary-400 shrink-0">{t('adminStudents.modal.seat')}</span>
                                <span className="text-white font-mono">{detail.seatNumber || t('adminStudents.modal.noSeat')}</span>
                            </div>

                            <div className="flex justify-between items-center py-1.5 border-b border-primary-700/20 text-sm gap-4">
                                <span className="text-primary-400 shrink-0">{t('adminStudents.modal.plan')}</span>
                                <span className="text-white">{detail.planName || t('adminStudents.modal.noPlan')}</span>
                            </div>

                            {/* Expires (always read-only) */}
                            <div className="flex justify-between py-1.5 border-b border-primary-700/20 text-sm">
                                <span className="text-primary-400">{t('adminStudents.modal.expires')}</span>
                                <span className="text-white">{detail.membershipEnd || '—'}</span>
                            </div>

                            {/* Days remaining (always read-only) */}
                            <div className="flex justify-between py-1.5 border-b border-primary-700/20 text-sm">
                                <span className="text-primary-400">{t('adminStudents.modal.daysLeftLabel')}</span>
                                <span className="text-white">{detail.daysRemaining ? t('adminStudents.modal.daysLeft', { count: detail.daysRemaining }) : '—'}</span>
                            </div>

                            {/* Payment mode (always read-only) */}
                            <div className="flex justify-between py-1.5 border-b border-primary-700/20 text-sm">
                                <span className="text-primary-400">{t('adminStudents.modal.payment')}</span>
                                <span className="text-white">{(() => {
                                    const info = paymentModeInfo(detail.paymentMode, t)
                                    return info ? `${info.emoji} ${info.label}` : '—'
                                })()}</span>
                            </div>

                            <div className="flex justify-between items-center py-1.5 border-b border-primary-700/20 text-sm gap-4">
                                <span className="text-primary-400 shrink-0">{t('adminStudents.modal.joined')}</span>
                                <span className="text-white">{detail.joinedAt?.split('T')[0] || '—'}</span>
                            </div>

                            <div className="flex justify-between py-1.5 text-sm">
                                <span className="text-primary-400">{t('adminStudents.modal.aadhaar')}</span>
                                {detail.aadhaarUrl ? (
                                    <a href={detail.aadhaarUrl} target="_blank" rel="noopener noreferrer"
                                        className="text-emerald-400 hover:text-emerald-300 underline text-xs">
                                        {t('adminStudents.modal.aadhaarView')}
                                    </a>
                                ) : (
                                    <span className="text-primary-600 text-xs">{t('adminStudents.modal.aadhaarNone')}</span>
                                )}
                            </div>
                        </div>

                        {/* Payment History */}
                        <div className="mt-5 pt-5 border-t border-primary-700/30">
                            <h4 className="text-white font-semibold text-sm mb-3">Payment History</h4>
                            {studentPaymentsLoading ? (
                                <div className="shimmer h-16 rounded-xl" />
                            ) : studentPayments.filter(p => p.status === 'SUCCESS').length === 0 ? (
                                <p className="text-primary-500 text-xs text-center py-3">No payments found.</p>
                            ) : (
                                <div className="space-y-2">
                                    {studentPayments.filter(p => p.status === 'SUCCESS').map(p => {
                                        const info = paymentModeInfo(p.paymentGateway, t)
                                        return (
                                            <div key={p.id} className="rounded-lg bg-primary-800/40 border border-primary-700/30 px-3 py-2.5 text-xs">
                                                <div className="flex items-center justify-between mb-1.5">
                                                    <span className="text-white font-semibold">₹{Number(p.amount).toLocaleString('en-IN')}</span>
                                                    <div className="flex items-center gap-2">
                                                        <span className={`px-2 py-0.5 rounded-full font-medium border ${info?.className ?? 'bg-primary-700/40 text-primary-400 border-primary-600/30'}`}>
                                                            {info ? `${info.emoji} ${info.label}` : '—'}
                                                        </span>
                                                        <span className={`px-2 py-0.5 rounded-full font-medium border ${
                                                            p.status === 'SUCCESS'  ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30' :
                                                            p.status === 'PENDING'  ? 'bg-amber-500/20 text-amber-400 border-amber-500/30' :
                                                            'bg-red-500/20 text-red-400 border-red-500/30'
                                                        }`}>{p.status}</span>
                                                    </div>
                                                </div>
                                                <div className="text-primary-400 space-y-0.5">
                                                    <p>{p.paidAt ? new Date(p.paidAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—'}</p>
                                                    {p.gatewayOrderId  && <p className="font-mono">Order: {p.gatewayOrderId}</p>}
                                                    {p.gatewayPaymentId && <p className="font-mono">Ref: {p.gatewayPaymentId}</p>}
                                                </div>
                                            </div>
                                        )
                                    })}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}

        </div>
    )
}
