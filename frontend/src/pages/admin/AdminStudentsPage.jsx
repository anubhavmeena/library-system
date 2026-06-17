import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

const ROWS = ['A', 'B', 'C', 'D']

export default function AdminStudentsPage() {
    const [students, setStudents] = useState([])
    const [total, setTotal]       = useState(0)
    const [loading, setLoading]   = useState(true)
    const [page, setPage]             = useState(0)
    const [status, setStatus]         = useState('')
    const [membershipFilter, setMembershipFilter] = useState('')
    const [search, setSearch]         = useState('')
    const [detail, setDetail]     = useState(null)
    const [editMode, setEditMode] = useState(false)
    const [editForm, setEditForm] = useState({})
    const [saving, setSaving]     = useState(false)

    const [changeSeatFor, setChangeSeatFor]               = useState(null)
    const [changeSeatGrid, setChangeSeatGrid]             = useState(null)
    const [changeSeatGridLoading, setChangeSeatGridLoading] = useState(false)
    const [newSeat, setNewSeat]                           = useState(null)
    const [changeSeatSubmitting, setChangeSeatSubmitting] = useState(false)

    const { t } = useTranslation()

    const fetchStudents = async () => {
        setLoading(true)
        try {
            const res = await api.get(`/admin/students?page=${page}&size=20${status ? `&status=${status}` : ''}${membershipFilter ? `&membershipStatus=${membershipFilter}` : ''}`)
            setStudents(res.data.data.students || [])
            setTotal(res.data.data.total || 0)
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

    const openChangeSeat = async (student) => {
        setChangeSeatFor(student)
        setNewSeat(null)
        setChangeSeatGrid(null)
        setChangeSeatGridLoading(true)
        try {
            const date = student.membershipStart || new Date().toISOString().split('T')[0]
            const res = await api.get(`/seats/availability?shift=${student.shift}&date=${date}`)
            setChangeSeatGrid(res.data.data)
        } catch {
            toast.error(t('adminStudents.toasts.seatChangeFailed'))
            setChangeSeatFor(null)
        } finally {
            setChangeSeatGridLoading(false)
        }
    }

    const handleChangeSeat = async () => {
        if (!newSeat || !changeSeatFor) return
        setChangeSeatSubmitting(true)
        try {
            await api.patch(`/admin/memberships/${changeSeatFor.membershipId}/seat`, { seatNumber: newSeat })
            toast.success(t('adminStudents.toasts.seatChanged', { seat: newSeat }))
            setChangeSeatFor(null)
            fetchStudents()
        } catch {
            toast.error(t('adminStudents.toasts.seatChangeFailed'))
        } finally {
            setChangeSeatSubmitting(false)
        }
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
        t('adminStudents.table.payment'),
        t('adminStudents.table.status'),
        t('adminStudents.table.actions'),
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
                                        {s.paymentMode === 'CASH' ? (
                                            <span className="text-xs px-2 py-1 rounded-full border bg-amber-500/20 text-amber-400 border-amber-500/30">💵 {t('adminStudents.cash')}</span>
                                        ) : s.paymentMode === 'ONLINE' ? (
                                            <span className="text-xs px-2 py-1 rounded-full border bg-indigo-500/20 text-indigo-400 border-indigo-500/30">💳 {t('adminStudents.online')}</span>
                                        ) : (
                                            <span className="text-primary-600 text-xs">—</span>
                                        )}
                                    </td>
                                    <td className="p-4">
                                        <span className={`badge border text-xs px-2 py-1 rounded-full
                        ${s.isActive ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30' : 'bg-red-500/20 text-red-400 border-red-500/30'}`}>
                                            {s.isActive ? t('adminStudents.active') : t('adminStudents.inactive')}
                                        </span>
                                    </td>
                                    <td className="p-4">
                                        <div className="flex flex-wrap gap-2">
                                            <button onClick={() => {
                                                setDetail(s)
                                                setEditMode(false)
                                                setEditForm({ name: s.name||'', email: s.email||'', address: s.address||'', gender: s.gender||'', dateOfBirth: s.dateOfBirth||'' })
                                            }}
                                                    className="text-xs px-3 py-1.5 rounded-lg bg-primary-700/50 text-primary-300 hover:text-white border border-primary-700/40 transition-all">
                                                {t('adminStudents.view')}
                                            </button>
                                            {s.membershipId && (
                                                <button onClick={() => openChangeSeat(s)}
                                                        className="text-xs px-3 py-1.5 rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/30 hover:bg-indigo-500/20 transition-all">
                                                    {t('adminStudents.changeSeat')}
                                                </button>
                                            )}
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

            {changeSeatFor && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={() => setChangeSeatFor(null)}>
                    <div className="card p-6 w-full max-w-2xl border-indigo-900/30 max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-4">
                            <div>
                                <h3 className="section-title">{t('adminStudents.changeSeatModal.title')}</h3>
                                <p className="text-primary-400 text-sm mt-1">
                                    {changeSeatFor.name} &mdash; {t('adminStudents.changeSeatModal.current')}: <span className="text-white font-mono">{changeSeatFor.seatNumber}</span>
                                </p>
                            </div>
                            <button onClick={() => setChangeSeatFor(null)} className="text-primary-400 hover:text-white">✕</button>
                        </div>

                        {changeSeatGridLoading ? (
                            <div className="shimmer h-48 rounded-xl" />
                        ) : changeSeatGrid ? (
                            <div className="overflow-x-auto">
                                <div className="flex justify-center mb-3">
                                    <div className="px-8 py-1.5 rounded-lg bg-primary-700/40 border border-primary-600/30 text-primary-400 text-xs tracking-widest uppercase">
                                        {t('adminStudents.changeSeatModal.entrance')}
                                    </div>
                                </div>
                                <div className="space-y-2 min-w-[520px]">
                                    {ROWS.map(row => {
                                        const seats = changeSeatGrid.seatsByRow?.[row] || []
                                        const half  = Math.ceil(seats.length / 2)
                                        const left  = seats.slice(0, half)
                                        const right = seats.slice(half)
                                        const renderSeat = s => {
                                            const isCurrent  = s.seatNumber === changeSeatFor.seatNumber
                                            const isSelected = newSeat === s.seatNumber
                                            return (
                                                <button
                                                    key={s.seatNumber}
                                                    disabled={s.isBooked && !isCurrent}
                                                    onClick={() => !isCurrent && setNewSeat(s.seatNumber)}
                                                    title={isCurrent ? `${s.seatNumber} (current)` : s.isBooked ? `${s.seatNumber} (booked)` : s.seatNumber}
                                                    className={`w-8 h-8 rounded-lg text-xs font-medium border transition-all
                                                        ${isCurrent
                                                            ? 'bg-indigo-500/30 border-indigo-400/60 text-indigo-300 cursor-default'
                                                            : isSelected
                                                                ? 'bg-amber-400/30 border-amber-400/70 text-amber-300'
                                                                : s.isBooked
                                                                    ? 'bg-red-500/30 border-red-500/50 text-red-400 cursor-not-allowed opacity-60'
                                                                    : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/25 cursor-pointer'}`}>
                                                    {s.seatNumber.substring(1)}
                                                </button>
                                            )
                                        }
                                        return (
                                            <div key={row} className="flex items-center gap-3">
                                                <span className="text-primary-400 font-mono text-sm w-5 text-center">{row}</span>
                                                <div className="flex gap-1">{left.map(renderSeat)}</div>
                                                <div className="w-6" />
                                                <div className="flex gap-1">{right.map(renderSeat)}</div>
                                            </div>
                                        )
                                    })}
                                </div>
                                <div className="flex items-center gap-4 mt-4 text-xs text-primary-400">
                                    <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded bg-indigo-500/30 border border-indigo-400/60 inline-block" /> {t('adminStudents.changeSeatModal.current')}</span>
                                    <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded bg-emerald-500/10 border border-emerald-500/30 inline-block" /> {t('adminStudents.changeSeatModal.available')}</span>
                                    <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded bg-amber-400/30 border border-amber-400/70 inline-block" /> {t('adminStudents.changeSeatModal.selected')}</span>
                                    <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded bg-red-500/30 border border-red-500/50 inline-block" /> {t('adminStudents.changeSeatModal.booked')}</span>
                                </div>
                            </div>
                        ) : null}

                        <div className="flex gap-3 mt-5 pt-4 border-t border-primary-700/30">
                            <button onClick={() => setChangeSeatFor(null)}
                                    className="btn-ghost border border-primary-700/40 px-5 py-2 rounded-xl text-sm">
                                {t('adminStudents.modal.cancel') || 'Cancel'}
                            </button>
                            <button
                                onClick={handleChangeSeat}
                                disabled={!newSeat || changeSeatSubmitting}
                                className="btn-primary px-5 py-2 text-sm disabled:opacity-40 disabled:cursor-not-allowed">
                                {changeSeatSubmitting
                                    ? t('adminStudents.changeSeatModal.confirming')
                                    : t('adminStudents.changeSeatModal.confirm')}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {detail && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={() => { setDetail(null); setEditMode(false) }}>
                    <div className="card p-6 w-full max-w-md border-primary-700/30 max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
                        {/* Header */}
                        <div className="flex items-center justify-between mb-5">
                            <h3 className="section-title">{t('adminStudents.modal.title')}</h3>
                            <div className="flex items-center gap-2">
                                {!editMode ? (
                                    <button onClick={() => setEditMode(true)}
                                        className="text-xs px-3 py-1 rounded-lg bg-amber-500/20 text-amber-400 hover:bg-amber-500/30 border border-amber-500/30 transition-all">
                                        Edit
                                    </button>
                                ) : (
                                    <>
                                        <button onClick={() => { setEditMode(false); setEditForm({ name: detail.name||'', email: detail.email||'', address: detail.address||'', gender: detail.gender||'', dateOfBirth: detail.dateOfBirth||'' }) }}
                                            className="text-xs px-3 py-1 rounded-lg bg-primary-700/50 text-primary-300 hover:text-white border border-primary-700/40 transition-all">
                                            Cancel
                                        </button>
                                        <button
                                            disabled={saving}
                                            onClick={async () => {
                                                setSaving(true)
                                                try {
                                                    const res = await api.patch(`/admin/students/${detail.id}`, editForm)
                                                    const updated = res.data.data
                                                    setDetail(updated)
                                                    setEditForm({ name: updated.name||'', email: updated.email||'', address: updated.address||'', gender: updated.gender||'', dateOfBirth: updated.dateOfBirth||'' })
                                                    setEditMode(false)
                                                    fetchStudents()
                                                    toast.success('Student profile updated')
                                                } catch (e) {
                                                    toast.error(e.response?.data?.message || 'Save failed')
                                                } finally {
                                                    setSaving(false)
                                                }
                                            }}
                                            className="text-xs px-3 py-1 rounded-lg bg-amber-500 text-navy-deep font-semibold hover:bg-amber-400 disabled:opacity-50 transition-all">
                                            {saving ? 'Saving…' : 'Save'}
                                        </button>
                                    </>
                                )}
                                <button onClick={() => { setDetail(null); setEditMode(false) }} className="text-primary-400 hover:text-white ml-1">✕</button>
                            </div>
                        </div>

                        {/* Avatar + name */}
                        <div className="flex items-center gap-4 mb-5 pb-5 border-b border-primary-700/30">
                            {detail.photoUrl
                                ? <img src={detail.photoUrl} alt={detail.name} className="w-14 h-14 rounded-full object-cover flex-shrink-0" />
                                : <div className="w-14 h-14 rounded-full bg-gradient-to-br from-red-400 to-primary-600 flex items-center justify-center text-xl font-bold text-white flex-shrink-0">
                                    {(editMode ? editForm.name : detail.name)?.[0]?.toUpperCase()}
                                </div>
                            }
                            <div className="min-w-0">
                                {editMode
                                    ? <input className="input text-sm py-1 w-full font-bold" value={editForm.name}
                                        onChange={e => setEditForm(f => ({ ...f, name: e.target.value }))} placeholder="Name" />
                                    : <p className="text-white font-bold text-lg truncate">{detail.name}</p>
                                }
                                <p className="text-primary-400 text-sm">{detail.mobile}</p>
                            </div>
                        </div>

                        {/* Fields */}
                        <div className="space-y-2">
                            {/* Mobile — always read-only */}
                            <div className="flex justify-between py-1.5 border-b border-primary-700/20 text-sm">
                                <span className="text-primary-400">{t('adminStudents.modal.mobile')}</span>
                                <span className="text-white">{detail.mobile || '—'}</span>
                            </div>

                            {/* Editable fields */}
                            {[
                                { key: 'email',       label: t('adminStudents.modal.email'),   type: 'text',  placeholder: 'Email' },
                                { key: 'address',     label: t('adminStudents.modal.address'), type: 'text',  placeholder: 'Address' },
                                { key: 'dateOfBirth', label: 'Date of Birth',                  type: 'date',  placeholder: '' },
                            ].map(({ key, label, type, placeholder }) => (
                                <div key={key} className="flex justify-between items-center py-1.5 border-b border-primary-700/20 text-sm gap-4">
                                    <span className="text-primary-400 shrink-0">{label}</span>
                                    {editMode
                                        ? <input type={type} className="input text-sm py-0.5 text-right w-44"
                                            value={editForm[key]} placeholder={placeholder}
                                            onChange={e => setEditForm(f => ({ ...f, [key]: e.target.value }))} />
                                        : <span className="text-white text-right max-w-[55%] truncate">{detail[key] || '—'}</span>
                                    }
                                </div>
                            ))}

                            {/* Gender — dropdown in edit mode */}
                            <div className="flex justify-between items-center py-1.5 border-b border-primary-700/20 text-sm gap-4">
                                <span className="text-primary-400 shrink-0">{t('adminStudents.modal.gender')}</span>
                                {editMode
                                    ? <select className="input text-sm py-0.5 w-44 text-right"
                                        value={editForm.gender}
                                        onChange={e => setEditForm(f => ({ ...f, gender: e.target.value }))}>
                                        <option value="">—</option>
                                        <option value="Male">Male</option>
                                        <option value="Female">Female</option>
                                        <option value="Other">Other</option>
                                    </select>
                                    : <span className="text-white">{detail.gender || '—'}</span>
                                }
                            </div>

                            {/* Read-only membership fields */}
                            {[
                                { l: t('adminStudents.modal.seat'),           v: detail.seatNumber || t('adminStudents.modal.noSeat') },
                                { l: t('adminStudents.modal.plan'),           v: detail.planName || t('adminStudents.modal.noPlan') },
                                { l: t('adminStudents.modal.expires'),        v: detail.membershipEnd || '—' },
                                { l: t('adminStudents.modal.daysLeftLabel'),  v: detail.daysRemaining ? t('adminStudents.modal.daysLeft', { count: detail.daysRemaining }) : '—' },
                                { l: t('adminStudents.modal.payment'),        v: detail.paymentMode === 'CASH' ? `💵 ${t('adminStudents.cash')}` : detail.paymentMode === 'ONLINE' ? `💳 ${t('adminStudents.online')}` : '—' },
                                { l: t('adminStudents.modal.joined'),         v: detail.joinedAt?.split('T')[0] || '—' },
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
