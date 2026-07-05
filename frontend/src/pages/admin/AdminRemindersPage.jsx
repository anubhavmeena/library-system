import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

export default function AdminRemindersPage() {
    const [students, setStudents] = useState([])
    const [loading, setLoading]   = useState(true)
    const [sending, setSending]   = useState(false)
    const [selected, setSelected] = useState(new Set())
    const [withinDays, setWithinDays] = useState(7)

    const [pendingStudents, setPendingStudents]   = useState([])
    const [pendingLoading, setPendingLoading]     = useState(true)
    const [pendingSending, setPendingSending]     = useState(false)
    const [pendingSelected, setPendingSelected]   = useState(new Set())

    const [graceStudents, setGraceStudents] = useState([])
    const [graceLoading, setGraceLoading]   = useState(true)
    const [graceSending, setGraceSending]   = useState(false)
    const [graceSelected, setGraceSelected] = useState(new Set())

    const [orphanedStudents, setOrphanedStudents] = useState([])
    const [orphanedLoading, setOrphanedLoading]   = useState(true)

    const { t } = useTranslation()

    const fetchExpiring = async () => {
        setLoading(true)
        try { const res = await api.get(`/admin/memberships/expiring?withinDays=${withinDays}`); setStudents(res.data.data || []) }
        catch { toast.error(t('adminReminders.toasts.loadFailed')) }
        finally { setLoading(false) }
    }

    const fetchPendingFees = async () => {
        setPendingLoading(true)
        try { const res = await api.get('/admin/students/pending-fees'); setPendingStudents(res.data.data || []) }
        catch { toast.error('Failed to load pending fee students') }
        finally { setPendingLoading(false) }
    }

    const fetchOrphanedSeats = async () => {
        setOrphanedLoading(true)
        try { const res = await api.get('/admin/students/orphaned-seats'); setOrphanedStudents(res.data.data || []) }
        catch { toast.error('Failed to load students with unassigned seats') }
        finally { setOrphanedLoading(false) }
    }

    const fetchGraceDues = async () => {
        setGraceLoading(true)
        try { const res = await api.get('/admin/students/grace-dues'); setGraceStudents(res.data.data || []) }
        catch { toast.error('Failed to load grace-period students') }
        finally { setGraceLoading(false) }
    }

    useEffect(() => { fetchExpiring() }, [withinDays])
    useEffect(() => { fetchPendingFees() }, [])
    useEffect(() => { fetchOrphanedSeats() }, [])
    useEffect(() => { fetchGraceDues() }, [])

    const toggleAll = () => {
        if (selected.size === students.length) setSelected(new Set())
        else setSelected(new Set(students.map(s => s.id)))
    }
    const toggleOne = (id) => {
        const next = new Set(selected); next.has(id) ? next.delete(id) : next.add(id); setSelected(next)
    }

    const togglePendingAll = () => {
        if (pendingSelected.size === pendingStudents.length) setPendingSelected(new Set())
        else setPendingSelected(new Set(pendingStudents.map(s => s.id)))
    }
    const togglePendingOne = (id) => {
        const next = new Set(pendingSelected); next.has(id) ? next.delete(id) : next.add(id); setPendingSelected(next)
    }

    const toggleGraceAll = () => {
        if (graceSelected.size === graceStudents.length) setGraceSelected(new Set())
        else setGraceSelected(new Set(graceStudents.map(s => s.id)))
    }
    const toggleGraceOne = (id) => {
        const next = new Set(graceSelected); next.has(id) ? next.delete(id) : next.add(id); setGraceSelected(next)
    }

    const daysOverdue = (membershipEnd) =>
        Math.max(0, Math.ceil((new Date() - new Date(membershipEnd)) / 86400000))

    const handleSend = async () => {
        setSending(true)
        try {
            const res = await api.post('/admin/reminders/send', { userIds: selected.size > 0 ? [...selected] : [] })
            toast.success(res.data.data)
            setSelected(new Set())
        } catch { toast.error(t('adminReminders.toasts.sendFailed')) }
        finally { setSending(false) }
    }

    const handleSendPendingFee = async () => {
        setPendingSending(true)
        try {
            const res = await api.post('/admin/reminders/pending-fees', { userIds: pendingSelected.size > 0 ? [...pendingSelected] : [] })
            toast.success(res.data.data)
            setPendingSelected(new Set())
        } catch { toast.error('Failed to send pending fee reminders') }
        finally { setPendingSending(false) }
    }

    const handleSendGraceDues = async () => {
        setGraceSending(true)
        try {
            const res = await api.post('/admin/reminders/grace-dues', { userIds: graceSelected.size > 0 ? [...graceSelected] : [] })
            toast.success(res.data.data)
            setGraceSelected(new Set())
        } catch { toast.error('Failed to send grace dues reminders') }
        finally { setGraceSending(false) }
    }

    const urgencyColor = (days) => {
        if (days <= 3) return 'text-red-400 bg-red-500/10 border-red-500/30'
        if (days <= 5) return 'text-amber-400 bg-amber-500/10 border-amber-500/30'
        return 'text-blue-400 bg-blue-500/10 border-blue-500/30'
    }

    const sendBtnLabel = sending
        ? t('adminReminders.sending')
        : selected.size > 0
            ? t('adminReminders.sendSelected', { count: selected.size })
            : t('adminReminders.sendAll')

    return (
        <div className="space-y-10">

            {/* ── Section 1: Renewal Reminders ── */}
            <div>
                <div className="mb-6">
                    <h1 className="page-header">{t('adminReminders.title')}</h1>
                    <p className="text-primary-400">{t('adminReminders.subtitle')}</p>
                </div>

                <div className="flex flex-wrap items-center gap-3 mb-6">
                    <div className="flex items-center gap-2">
                        <span className="text-primary-400 text-sm">{t('adminReminders.showWithin')}</span>
                        {[3, 7, 14, 30].map(d => (
                            <button key={d} onClick={() => setWithinDays(d)}
                                    className={`px-3 py-1.5 rounded-lg text-sm font-medium border transition-all
                    ${withinDays === d ? 'bg-red-500/20 border-red-400/60 text-red-400' : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                                {d}{t('adminReminders.days')}
                            </button>
                        ))}
                    </div>
                    <div className="ml-auto flex gap-3">
                        {students.length > 0 && (
                            <button onClick={toggleAll} className="btn-ghost text-sm border border-primary-700/40 px-4 py-2 rounded-xl">
                                {selected.size === students.length ? t('adminReminders.deselectAll') : t('adminReminders.selectAll')}
                            </button>
                        )}
                        <button onClick={handleSend} disabled={sending || students.length === 0}
                                className="bg-red-600 hover:bg-red-500 disabled:opacity-50 text-white font-semibold px-5 py-2.5 rounded-xl text-sm transition-all">
                            {sendBtnLabel}
                        </button>
                    </div>
                </div>

                <div className="grid grid-cols-3 gap-4 mb-6">
                    {[
                        { l: t('adminReminders.stats.total'),    v: students.length,                                   c: 'text-white' },
                        { l: t('adminReminders.stats.critical'), v: students.filter(s => s.daysRemaining <= 3).length, c: 'text-red-400' },
                        { l: t('adminReminders.stats.warning'),  v: students.filter(s => s.daysRemaining <= 7).length, c: 'text-amber-400' },
                    ].map(({ l, v, c }) => (
                        <div key={l} className="card p-4 text-center">
                            <p className={`text-2xl font-bold ${c}`}>{v}</p>
                            <p className="text-primary-400 text-sm">{l}</p>
                        </div>
                    ))}
                </div>

                {loading ? (
                    <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="shimmer h-16 rounded-xl" />)}</div>
                ) : students.length === 0 ? (
                    <div className="card p-12 text-center">
                        <div className="text-4xl mb-3">🎉</div>
                        <p className="text-white font-semibold">{t('adminReminders.empty.title')}</p>
                        <p className="text-primary-400 text-sm mt-1">{t('adminReminders.empty.desc')}</p>
                    </div>
                ) : (
                    <div className="card overflow-hidden">
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                <tr className="border-b border-primary-700/40">
                                    <th className="p-4 text-left">
                                        <input type="checkbox" checked={selected.size === students.length && students.length > 0} onChange={toggleAll} className="rounded" />
                                    </th>
                                    <th className="p-4 text-left text-primary-400 font-medium">{t('adminReminders.table.student')}</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">{t('adminReminders.table.contact')}</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">{t('adminReminders.table.seat')}</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">{t('adminReminders.table.expires')}</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">{t('adminReminders.table.daysLeft')}</th>
                                </tr>
                                </thead>
                                <tbody className="divide-y divide-primary-700/20">
                                {students.map(s => (
                                    <tr key={s.id} className={`hover:bg-primary-800/30 transition-colors ${selected.has(s.id) ? 'bg-primary-800/20' : ''}`}>
                                        <td className="p-4">
                                            <input type="checkbox" checked={selected.has(s.id)} onChange={() => toggleOne(s.id)} className="rounded" />
                                        </td>
                                        <td className="p-4">
                                            <div className="flex items-center gap-3">
                                                <div className="w-8 h-8 rounded-full bg-gradient-to-br from-red-400 to-primary-600 flex items-center justify-center text-xs font-bold text-white flex-shrink-0">
                                                    {s.name?.[0]?.toUpperCase()}
                                                </div>
                                                <span className="text-white font-medium">{s.name}</span>
                                            </div>
                                        </td>
                                        <td className="p-4">
                                            <p className="text-primary-300">{s.mobile || '—'}</p>
                                            <p className="text-primary-500 text-xs">{s.email || '—'}</p>
                                        </td>
                                        <td className="p-4 text-primary-300">{s.seatNumber || '—'}</td>
                                        <td className="p-4 text-primary-300">{s.membershipEnd}</td>
                                        <td className="p-4">
                                            <span className={`badge border text-xs font-semibold px-2 py-1 rounded-full ${urgencyColor(s.daysRemaining)}`}>
                                                {t('adminReminders.daysLeft', { count: s.daysRemaining })}
                                            </span>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}
            </div>

            {/* ── Section 2: Pending Fee Reminders ── */}
            <div>
                <div className="mb-6">
                    <h2 className="section-title">Pending Fee Reminders</h2>
                    <p className="text-primary-400 text-sm mt-1">Students with outstanding cash fee balance</p>
                </div>

                <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                        <span className="text-sm text-primary-400">
                            {pendingStudents.length} student{pendingStudents.length !== 1 ? 's' : ''} with pending fees
                        </span>
                        <button onClick={fetchPendingFees} disabled={pendingLoading}
                                className="text-xs text-primary-500 hover:text-primary-300 border border-primary-700/40 px-2 py-1 rounded-lg transition-colors">
                            ↻ Refresh
                        </button>
                    </div>
                    <div className="flex gap-3">
                        {pendingStudents.length > 0 && (
                            <button onClick={togglePendingAll} className="btn-ghost text-sm border border-primary-700/40 px-4 py-2 rounded-xl">
                                {pendingSelected.size === pendingStudents.length ? 'Deselect All' : 'Select All'}
                            </button>
                        )}
                        <button onClick={handleSendPendingFee} disabled={pendingSending || pendingStudents.length === 0}
                                className="bg-amber-600 hover:bg-amber-500 disabled:opacity-50 text-white font-semibold px-5 py-2.5 rounded-xl text-sm transition-all">
                            {pendingSending
                                ? 'Sending…'
                                : pendingSelected.size > 0
                                    ? `Send to ${pendingSelected.size} selected`
                                    : 'Send to All'}
                        </button>
                    </div>
                </div>

                {pendingLoading ? (
                    <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="shimmer h-16 rounded-xl" />)}</div>
                ) : pendingStudents.length === 0 ? (
                    <div className="card p-12 text-center">
                        <div className="text-4xl mb-3">✅</div>
                        <p className="text-white font-semibold">No pending fees</p>
                        <p className="text-primary-400 text-sm mt-1">All students are up to date with their payments.</p>
                    </div>
                ) : (
                    <div className="card overflow-hidden">
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                <tr className="border-b border-primary-700/40">
                                    <th className="p-4 text-left">
                                        <input type="checkbox" checked={pendingSelected.size === pendingStudents.length && pendingStudents.length > 0} onChange={togglePendingAll} className="rounded" />
                                    </th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Student</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Contact</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Seat</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Membership Ends</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Pending Amount</th>
                                </tr>
                                </thead>
                                <tbody className="divide-y divide-primary-700/20">
                                {pendingStudents.map(s => (
                                    <tr key={s.id} className={`hover:bg-primary-800/30 transition-colors ${pendingSelected.has(s.id) ? 'bg-primary-800/20' : ''}`}>
                                        <td className="p-4">
                                            <input type="checkbox" checked={pendingSelected.has(s.id)} onChange={() => togglePendingOne(s.id)} className="rounded" />
                                        </td>
                                        <td className="p-4">
                                            <div className="flex items-center gap-3">
                                                <div className="w-8 h-8 rounded-full bg-gradient-to-br from-amber-400 to-primary-600 flex items-center justify-center text-xs font-bold text-white flex-shrink-0">
                                                    {s.name?.[0]?.toUpperCase()}
                                                </div>
                                                <span className="text-white font-medium">{s.name}</span>
                                            </div>
                                        </td>
                                        <td className="p-4">
                                            <p className="text-primary-300">{s.mobile || '—'}</p>
                                            <p className="text-primary-500 text-xs">{s.email || '—'}</p>
                                        </td>
                                        <td className="p-4 text-primary-300 font-mono">{s.seatNumber || '—'}</td>
                                        <td className="p-4 text-primary-300">{s.membershipEnd || '—'}</td>
                                        <td className="p-4">
                                            <span className="text-red-400 font-bold text-sm">₹{s.pendingAmount}</span>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}
            </div>

            {/* ── Section 3: Grace Period Dues Reminders ── */}
            <div>
                <div className="mb-6">
                    <h2 className="section-title">Grace Period Dues Reminders</h2>
                    <p className="text-primary-400 text-sm mt-1">Students in grace period who still owe dues to keep their seat</p>
                </div>

                <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                        <span className="text-sm text-primary-400">
                            {graceStudents.length} student{graceStudents.length !== 1 ? 's' : ''} with grace-period dues
                        </span>
                        <button onClick={fetchGraceDues} disabled={graceLoading}
                                className="text-xs text-primary-500 hover:text-primary-300 border border-primary-700/40 px-2 py-1 rounded-lg transition-colors">
                            ↻ Refresh
                        </button>
                    </div>
                    <div className="flex gap-3">
                        {graceStudents.length > 0 && (
                            <button onClick={toggleGraceAll} className="btn-ghost text-sm border border-primary-700/40 px-4 py-2 rounded-xl">
                                {graceSelected.size === graceStudents.length ? 'Deselect All' : 'Select All'}
                            </button>
                        )}
                        <button onClick={handleSendGraceDues} disabled={graceSending || graceStudents.length === 0}
                                className="bg-orange-600 hover:bg-orange-500 disabled:opacity-50 text-white font-semibold px-5 py-2.5 rounded-xl text-sm transition-all">
                            {graceSending
                                ? 'Sending…'
                                : graceSelected.size > 0
                                    ? `Send to ${graceSelected.size} selected`
                                    : 'Send to All'}
                        </button>
                    </div>
                </div>

                {graceLoading ? (
                    <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="shimmer h-16 rounded-xl" />)}</div>
                ) : graceStudents.length === 0 ? (
                    <div className="card p-12 text-center">
                        <div className="text-4xl mb-3">✅</div>
                        <p className="text-white font-semibold">No dues to clear</p>
                        <p className="text-primary-400 text-sm mt-1">All grace-period students are settled.</p>
                    </div>
                ) : (
                    <div className="card overflow-hidden">
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                <tr className="border-b border-primary-700/40">
                                    <th className="p-4 text-left">
                                        <input type="checkbox" checked={graceSelected.size === graceStudents.length && graceStudents.length > 0} onChange={toggleGraceAll} className="rounded" />
                                    </th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Student</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Contact</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Seat</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Membership Ended</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Days Overdue</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Dues Amount</th>
                                </tr>
                                </thead>
                                <tbody className="divide-y divide-primary-700/20">
                                {graceStudents.map(s => (
                                    <tr key={s.id} className={`hover:bg-primary-800/30 transition-colors ${graceSelected.has(s.id) ? 'bg-primary-800/20' : ''}`}>
                                        <td className="p-4">
                                            <input type="checkbox" checked={graceSelected.has(s.id)} onChange={() => toggleGraceOne(s.id)} className="rounded" />
                                        </td>
                                        <td className="p-4">
                                            <div className="flex items-center gap-3">
                                                <div className="w-8 h-8 rounded-full bg-gradient-to-br from-orange-400 to-primary-600 flex items-center justify-center text-xs font-bold text-white flex-shrink-0">
                                                    {s.name?.[0]?.toUpperCase()}
                                                </div>
                                                <span className="text-white font-medium">{s.name}</span>
                                            </div>
                                        </td>
                                        <td className="p-4">
                                            <p className="text-primary-300">{s.mobile || '—'}</p>
                                            <p className="text-primary-500 text-xs">{s.email || '—'}</p>
                                        </td>
                                        <td className="p-4 text-primary-300 font-mono">{s.seatNumber || '—'}</td>
                                        <td className="p-4 text-primary-300">{s.membershipEnd || '—'}</td>
                                        <td className="p-4">
                                            <span className="badge border text-xs font-semibold px-2 py-1 rounded-full text-orange-400 bg-orange-500/10 border-orange-500/30">
                                                {daysOverdue(s.membershipEnd)}d overdue
                                            </span>
                                        </td>
                                        <td className="p-4">
                                            <span className="text-red-400 font-bold text-sm">₹{s.duesAmount}</span>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}
            </div>

            {/* ── Section 4: Students Needing a Seat ── */}
            <div>
                <div className="mb-6">
                    <h2 className="section-title">Students Needing a Seat</h2>
                    <p className="text-primary-400 text-sm mt-1">
                        Paid, active memberships with no seat actually reserved — usually because the seat-reservation
                        step failed right after payment. Assign a seat via the student's detail page.
                    </p>
                </div>

                <div className="flex items-center gap-2 mb-4">
                    <span className="text-sm text-primary-400">
                        {orphanedStudents.length} student{orphanedStudents.length !== 1 ? 's' : ''} without a reserved seat
                    </span>
                    <button onClick={fetchOrphanedSeats} disabled={orphanedLoading}
                            className="text-xs text-primary-500 hover:text-primary-300 border border-primary-700/40 px-2 py-1 rounded-lg transition-colors">
                        ↻ Refresh
                    </button>
                </div>

                {orphanedLoading ? (
                    <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="shimmer h-16 rounded-xl" />)}</div>
                ) : orphanedStudents.length === 0 ? (
                    <div className="card p-12 text-center">
                        <div className="text-4xl mb-3">✅</div>
                        <p className="text-white font-semibold">Everyone has a seat</p>
                        <p className="text-primary-400 text-sm mt-1">No paid memberships are missing a seat reservation.</p>
                    </div>
                ) : (
                    <div className="card overflow-hidden">
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                <tr className="border-b border-primary-700/40">
                                    <th className="p-4 text-left text-primary-400 font-medium">Student</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Contact</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Plan</th>
                                    <th className="p-4 text-left text-primary-400 font-medium">Membership Ends</th>
                                    <th className="p-4 text-left text-primary-400 font-medium"></th>
                                </tr>
                                </thead>
                                <tbody className="divide-y divide-primary-700/20">
                                {orphanedStudents.map(s => (
                                    <tr key={s.id} className="hover:bg-primary-800/30 transition-colors">
                                        <td className="p-4">
                                            <div className="flex items-center gap-3">
                                                <div className="w-8 h-8 rounded-full bg-gradient-to-br from-red-400 to-primary-600 flex items-center justify-center text-xs font-bold text-white flex-shrink-0">
                                                    {s.name?.[0]?.toUpperCase()}
                                                </div>
                                                <span className="text-white font-medium">{s.name}</span>
                                            </div>
                                        </td>
                                        <td className="p-4">
                                            <p className="text-primary-300">{s.mobile || '—'}</p>
                                            <p className="text-primary-500 text-xs">{s.email || '—'}</p>
                                        </td>
                                        <td className="p-4 text-primary-300">{s.planName || '—'}</td>
                                        <td className="p-4 text-primary-300">{s.membershipEnd || '—'}</td>
                                        <td className="p-4 text-right">
                                            <Link to={`/admin/students/${s.id}`}
                                                  className="text-xs px-3 py-1.5 rounded-lg bg-primary-700/50 text-primary-300 hover:text-white border border-primary-700/40 transition-all">
                                                Assign Seat →
                                            </Link>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}
            </div>
        </div>
    )
}
