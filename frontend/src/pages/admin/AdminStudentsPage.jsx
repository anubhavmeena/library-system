import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

export default function AdminStudentsPage() {
    const [students, setStudents] = useState([])
    const [loading, setLoading]   = useState(true)
    const [page, setPage]             = useState(0)
    const [status, setStatus]         = useState('')
    const [membershipFilter, setMembershipFilter] = useState('')
    const [search, setSearch]         = useState('')
    const [detail, setDetail]     = useState(null)
    const { t } = useTranslation()

    const fetchStudents = async () => {
        setLoading(true)
        try {
            const res = await api.get(`/admin/students?page=${page}&size=20${status ? `&status=${status}` : ''}${membershipFilter ? `&membershipStatus=${membershipFilter}` : ''}`)
            setStudents(res.data.data || [])
        } catch { toast.error(t('adminStudents.toasts.loadFailed')) }
        finally { setLoading(false) }
    }

    useEffect(() => { fetchStudents() }, [page, status, membershipFilter])

    const handleToggleStatus = async (student) => {
        try {
            await api.patch(`/admin/students/${student.id}/status`, { active: !student.isActive })
            const action = !student.isActive
                ? t('adminStudents.toasts.activated', { name: student.name })
                : t('adminStudents.toasts.deactivated', { name: student.name })
            toast.success(action)
            fetchStudents()
        } catch { toast.error(t('adminStudents.toasts.updateFailed')) }
    }

    const shiftLabel = (shift) => {
        if (shift === 'MORNING')  return t('adminStudents.shifts.MORNING')
        if (shift === 'EVENING')  return t('adminStudents.shifts.EVENING')
        if (shift === 'FULL_DAY') return t('adminStudents.shifts.FULL_DAY')
        return '—'
    }

    const filtered = students.filter(s =>
        !search || s.name?.toLowerCase().includes(search.toLowerCase()) ||
        s.mobile?.includes(search) || s.email?.toLowerCase().includes(search.toLowerCase())
    )

    const filters = [
        { v: '',         l: t('adminStudents.filters.all') },
        { v: 'ACTIVE',   l: t('adminStudents.filters.active') },
        { v: 'INACTIVE', l: t('adminStudents.filters.inactive') },
    ]

    const membershipFilters = [
        { v: '',         l: t('adminStudents.filters.membershipAll') },
        { v: 'ACTIVE',   l: t('adminStudents.filters.membershipActive') },
        { v: 'INACTIVE', l: t('adminStudents.filters.membershipInactive') },
    ]

    const headers = [
        t('adminStudents.table.student'),
        t('adminStudents.table.contact'),
        t('adminStudents.table.seatShift'),
        t('adminStudents.table.membership'),
        t('adminStudents.table.status'),
        t('adminStudents.table.actions'),
    ]

    return (
        <div>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h1 className="page-header">{t('adminStudents.title')}</h1>
                    <p className="text-primary-400">{t('adminStudents.subtitle', { count: students.length })}</p>
                </div>
                <button onClick={fetchStudents} className="btn-ghost border border-primary-700/40 text-sm px-4 py-2 rounded-xl">↻ {t('adminStudents.refresh')}</button>
            </div>

            <div className="flex flex-wrap gap-3 mb-3">
                <input className="input w-64 text-sm py-2" placeholder={t('adminStudents.searchPlaceholder')}
                       value={search} onChange={e => setSearch(e.target.value)} />
                <div className="flex items-center gap-2">
                    <span className="text-primary-500 text-xs">{t('adminStudents.filters.accountLabel')}</span>
                    {filters.map(({ v, l }) => (
                        <button key={v} onClick={() => { setStatus(v); setPage(0) }}
                                className={`px-3 py-2 rounded-xl text-sm font-medium border transition-all
                ${status === v ? 'bg-red-500/20 border-red-400/60 text-red-400' : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                            {l}
                        </button>
                    ))}
                </div>
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
            ) : filtered.length === 0 ? (
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
                                {headers.map(h => (
                                    <th key={h} className="p-4 text-left text-primary-400 font-medium">{h}</th>
                                ))}
                            </tr>
                            </thead>
                            <tbody className="divide-y divide-primary-700/20">
                            {filtered.map(s => (
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
                                                <p className="text-white font-medium">{s.name}</p>
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
                                        {s.membershipEnd ? (
                                            <>
                                                <p className="text-primary-300 text-xs">{t('adminStudents.expires')} {s.membershipEnd}</p>
                                                <p className={`text-xs font-semibold ${s.daysRemaining <= 3 ? 'text-red-400' : s.daysRemaining <= 7 ? 'text-amber-400' : 'text-emerald-400'}`}>
                                                    {t('adminStudents.daysLeft', { count: s.daysRemaining })}
                                                </p>
                                            </>
                                        ) : <span className="text-primary-600 text-xs">{t('adminStudents.noPlan')}</span>}
                                    </td>
                                    <td className="p-4">
                                        <span className={`badge border text-xs px-2 py-1 rounded-full
                        ${s.isActive ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30' : 'bg-red-500/20 text-red-400 border-red-500/30'}`}>
                                            {s.isActive ? t('adminStudents.active') : t('adminStudents.inactive')}
                                        </span>
                                    </td>
                                    <td className="p-4">
                                        <div className="flex gap-2">
                                            <button onClick={() => setDetail(s)}
                                                    className="text-xs px-3 py-1.5 rounded-lg bg-primary-700/50 text-primary-300 hover:text-white border border-primary-700/40 transition-all">
                                                {t('adminStudents.view')}
                                            </button>
                                            <button onClick={() => handleToggleStatus(s)}
                                                    className={`text-xs px-3 py-1.5 rounded-lg border transition-all
                            ${s.isActive ? 'bg-red-500/10 text-red-400 border-red-500/30 hover:bg-red-500/20' : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/20'}`}>
                                                {s.isActive ? t('adminStudents.disable') : t('adminStudents.enable')}
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                    <div className="flex items-center justify-between p-4 border-t border-primary-700/30">
                        <span className="text-primary-400 text-sm">{t('adminStudents.page', { page: page + 1 })}</span>
                        <div className="flex gap-2">
                            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
                                    className="btn-ghost disabled:opacity-40 text-sm px-3 py-1.5 border border-primary-700/40 rounded-lg">← {t('adminStudents.prev')}</button>
                            <button onClick={() => setPage(p => p + 1)} disabled={students.length < 20}
                                    className="btn-ghost disabled:opacity-40 text-sm px-3 py-1.5 border border-primary-700/40 rounded-lg">{t('adminStudents.next')} →</button>
                        </div>
                    </div>
                </div>
            )}

            {detail && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={() => setDetail(null)}>
                    <div className="card p-6 w-full max-w-md border-red-900/30" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-5">
                            <h3 className="section-title">{t('adminStudents.modal.title')}</h3>
                            <button onClick={() => setDetail(null)} className="text-primary-400 hover:text-white">✕</button>
                        </div>
                        <div className="flex items-center gap-4 mb-5 pb-5 border-b border-primary-700/30">
                            {detail.photoUrl
                                ? <img src={detail.photoUrl} alt={detail.name} className="w-14 h-14 rounded-full object-cover" />
                                : <div className="w-14 h-14 rounded-full bg-gradient-to-br from-red-400 to-primary-600 flex items-center justify-center text-xl font-bold text-white">
                                    {detail.name?.[0]?.toUpperCase()}
                                </div>
                            }
                            <div>
                                <p className="text-white font-bold text-lg">{detail.name}</p>
                                <p className="text-primary-400 text-sm">{detail.mobile || detail.email}</p>
                            </div>
                        </div>
                        <div className="space-y-2">
                            {[
                                { l: t('adminStudents.modal.mobile'),   v: detail.mobile || '—' },
                                { l: t('adminStudents.modal.email'),    v: detail.email || '—' },
                                { l: t('adminStudents.modal.address'),  v: detail.address || '—' },
                                { l: t('adminStudents.modal.gender'),   v: detail.gender || '—' },
                                { l: t('adminStudents.modal.seat'),     v: detail.seatNumber || t('adminStudents.modal.noSeat') },
                                { l: t('adminStudents.modal.plan'),     v: detail.planName || t('adminStudents.modal.noPlan') },
                                { l: t('adminStudents.modal.expires'),  v: detail.membershipEnd || '—' },
                                { l: t('adminStudents.modal.daysLeftLabel'), v: detail.daysRemaining ? t('adminStudents.modal.daysLeft', { count: detail.daysRemaining }) : '—' },
                                { l: t('adminStudents.modal.joined'),   v: detail.joinedAt?.split('T')[0] || '—' },
                            ].map(({ l, v }) => (
                                <div key={l} className="flex justify-between py-1.5 border-b border-primary-700/20 last:border-0 text-sm">
                                    <span className="text-primary-400">{l}</span>
                                    <span className="text-white text-right max-w-[55%] truncate">{v}</span>
                                </div>
                            ))}
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
                    </div>
                </div>
            )}
        </div>
    )
}
