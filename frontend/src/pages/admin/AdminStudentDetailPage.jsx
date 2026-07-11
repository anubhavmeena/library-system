import { useEffect, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'
import StudentActionsMenu from '../../components/admin/StudentActionsMenu'

const STATUS_BADGE_CLASSES = {
    NEW:      'bg-blue-500/20 text-blue-400 border-blue-500/30',
    PAID:     'bg-emerald-500/20 text-emerald-400 border-emerald-500/30',
    PENDING:  'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
    GRACE:         'bg-orange-500/20 text-orange-400 border-orange-500/30',
    GRACE_OVERDUE: 'bg-red-500/20 text-red-400 border-red-500/30',
    RELEASED:      'bg-red-950/70 text-red-300 border-red-900',
}

export default function AdminStudentDetailPage() {
    const { userId } = useParams()
    const navigate = useNavigate()
    const { t } = useTranslation()

    const [student, setStudent] = useState(null)
    const [showPhotoPreview, setShowPhotoPreview] = useState(false)
    const [loading, setLoading] = useState(true)
    const [editForm, setEditForm] = useState({})
    const [saving, setSaving]   = useState(false)

    const [payments, setPayments]               = useState([])
    const [paymentsLoading, setPaymentsLoading] = useState(true)

    const [seatHistory, setSeatHistory]               = useState([])
    const [seatHistoryLoading, setSeatHistoryLoading] = useState(true)

    const fetchStudent = async () => {
        setLoading(true)
        try {
            const res = await api.get(`/admin/students/${userId}`)
            const full = res.data.data
            setStudent(full)
            setEditForm({
                name: full.name || '', mobile: full.mobile || '', email: full.email || '',
                address: full.address || '', gender: full.gender || '',
                dateOfBirth: full.dateOfBirth || '', joinedAt: full.joinedAt?.split('T')[0] || '',
            })
        } catch {
            toast.error(t('adminStudentDetail.toasts.loadFailed'))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => { fetchStudent() }, [userId])

    const fetchPayments = () => {
        setPaymentsLoading(true)
        api.get(`/admin/students/${userId}/payments`)
            .then(r => setPayments(r.data.data || []))
            .catch(() => setPayments([]))
            .finally(() => setPaymentsLoading(false))
    }

    useEffect(fetchPayments, [userId])

    const fetchSeatHistory = () => {
        setSeatHistoryLoading(true)
        api.get(`/admin/students/${userId}/seat-history`)
            .then(r => setSeatHistory(r.data.data || []))
            .catch(() => setSeatHistory([]))
            .finally(() => setSeatHistoryLoading(false))
    }

    useEffect(fetchSeatHistory, [userId])

    const handleSave = async () => {
        setSaving(true)
        try {
            // dateOfBirth/joinedAt are Option<NaiveDate> on the backend — an empty
            // string fails to parse as a date (unlike Option<String> fields, where
            // "" is a valid value), so a blank date input must be sent as null,
            // not "", or the whole request is rejected before it reaches the
            // update logic.
            const payload = {
                ...editForm,
                dateOfBirth: editForm.dateOfBirth || null,
                joinedAt: editForm.joinedAt || null,
            }
            const res = await api.patch(`/admin/students/${userId}`, payload)
            setStudent(res.data.data)
            toast.success(t('adminStudentDetail.toasts.saved'))
        } catch (e) {
            toast.error(e.response?.data?.message || t('adminStudentDetail.toasts.saveFailed'))
        } finally {
            setSaving(false)
        }
    }

    const shiftLabel = (shift) => {
        if (shift === 'MORNING')  return t('adminStudents.shifts.MORNING')
        if (shift === 'EVENING')  return t('adminStudents.shifts.EVENING')
        if (shift === 'FULL_DAY') return t('adminStudents.shifts.FULL_DAY')
        return '—'
    }

    if (loading) {
        return (
            <div className="max-w-3xl space-y-4">
                <div className="shimmer h-10 w-40 rounded-lg" />
                <div className="shimmer h-24 rounded-xl" />
                <div className="shimmer h-64 rounded-xl" />
            </div>
        )
    }

    if (!student) {
        return <p className="text-primary-400">{t('adminStudentDetail.notFound')}</p>
    }

    return (
        <div className="max-w-3xl">
            <Link to="/admin/students" className="text-primary-400 hover:text-white text-sm inline-flex items-center gap-1 mb-4 transition-colors">
                ← {t('adminStudentDetail.back')}
            </Link>

            <div className="mb-6 flex items-center justify-between gap-4">
                <div className="flex items-center gap-4 min-w-0">
                    {student.photoUrl
                        ? <img src={student.photoUrl} alt={student.name}
                               onClick={() => setShowPhotoPreview(true)}
                               className="w-16 h-16 rounded-full object-cover flex-shrink-0 cursor-pointer hover:opacity-80 transition-opacity" />
                        : <div className="w-16 h-16 rounded-full bg-gradient-to-br from-red-400 to-primary-600 flex items-center justify-center text-2xl font-bold text-white flex-shrink-0">
                            {student.name?.[0]?.toUpperCase()}
                        </div>
                    }
                    <div className="min-w-0">
                        <h1 className="page-header truncate">{student.name}</h1>
                        <div className="flex items-center gap-2 mt-1">
                            <p className="text-primary-400">{student.mobile}</p>
                            {student.displayStatus && (
                                <span className={`text-xs px-2 py-0.5 rounded-full border ${STATUS_BADGE_CLASSES[student.displayStatus] || 'bg-primary-700/30 text-primary-400 border-primary-700/40'}`}>
                                    {t(`adminStudents.statusLabels.${student.displayStatus}`)}
                                </span>
                            )}
                        </div>
                    </div>
                </div>
                <div className="flex-shrink-0">
                    <StudentActionsMenu
                        student={student}
                        onMutated={() => { fetchStudent(); fetchSeatHistory(); fetchPayments() }}
                        onDeleted={() => navigate('/admin/students')}
                    />
                </div>
            </div>

            {/* Editable profile fields */}
            <div className="card p-6 mb-6">
                <div className="flex items-center justify-between mb-4">
                    <h2 className="section-title text-lg">{t('adminStudentDetail.detailsTitle')}</h2>
                    <button onClick={handleSave} disabled={saving}
                        className="btn-primary px-5 py-2 text-sm disabled:opacity-50 disabled:cursor-not-allowed">
                        {saving ? t('adminStudentDetail.saving') : t('adminStudentDetail.save')}
                    </button>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    {[
                        { key: 'name',    label: t('adminStudentDetail.fields.name') },
                        { key: 'mobile',  label: t('adminStudents.modal.mobile') },
                        { key: 'email',   label: t('adminStudents.modal.email') },
                        { key: 'address', label: t('adminStudents.modal.address') },
                    ].map(({ key, label }) => (
                        <div key={key}>
                            <label className="label mb-1 block">{label}</label>
                            <input className="input w-full text-sm" value={editForm[key] || ''}
                                onChange={e => setEditForm(f => ({ ...f, [key]: e.target.value }))} />
                        </div>
                    ))}
                    <div>
                        <label className="label mb-1 block">{t('adminStudents.modal.gender')}</label>
                        <select className="input w-full text-sm" value={editForm.gender || ''}
                            onChange={e => setEditForm(f => ({ ...f, gender: e.target.value }))}>
                            <option value="">—</option>
                            <option value="Male">Male</option>
                            <option value="Female">Female</option>
                            <option value="Other">Other</option>
                        </select>
                    </div>
                    <div>
                        <label className="label mb-1 block">{t('adminStudentDetail.fields.dateOfBirth')}</label>
                        <input type="date" className="input w-full text-sm" value={editForm.dateOfBirth || ''}
                            onChange={e => setEditForm(f => ({ ...f, dateOfBirth: e.target.value }))} />
                    </div>
                    <div>
                        <label className="label mb-1 block">{t('adminStudents.modal.joined')}</label>
                        <input type="date" className="input w-full text-sm" value={editForm.joinedAt || ''}
                            onChange={e => setEditForm(f => ({ ...f, joinedAt: e.target.value }))} />
                    </div>
                </div>
                <div className="mt-4 pt-4 border-t border-primary-700/20 text-sm">
                    <span className="text-primary-400">{t('adminStudents.modal.aadhaar')}: </span>
                    {student.aadhaarUrl ? (
                        <a href={student.aadhaarUrl} target="_blank" rel="noopener noreferrer"
                            className="text-emerald-400 hover:text-emerald-300 underline text-xs">
                            {t('adminStudents.modal.aadhaarView')}
                        </a>
                    ) : (
                        <span className="text-primary-600 text-xs">{t('adminStudents.modal.aadhaarNone')}</span>
                    )}
                </div>
            </div>

            {/* Current membership summary (read-only — use the Actions menu above to change seat/plan/release) */}
            <div className="card p-6 mb-6">
                <h2 className="section-title text-lg mb-4">{t('adminStudentDetail.membershipTitle')}</h2>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
                    <div>
                        <p className="text-primary-400 text-xs mb-1">{t('adminStudents.modal.seat')}</p>
                        <p className="text-white font-mono">{student.seatNumber || t('adminStudents.modal.noSeat')}</p>
                    </div>
                    <div>
                        <p className="text-primary-400 text-xs mb-1">{t('adminStudents.modal.plan')}</p>
                        <p className="text-white">{student.planName || t('adminStudents.modal.noPlan')}</p>
                    </div>
                    <div>
                        <p className="text-primary-400 text-xs mb-1">{t('adminStudents.modal.expires')}</p>
                        <p className="text-white">{student.membershipEnd || '—'}</p>
                    </div>
                    <div>
                        <p className="text-primary-400 text-xs mb-1">{t('adminStudents.modal.payment')}</p>
                        <p className="text-white">
                            {student.paymentMode === 'CASH' ? `💵 ${t('adminStudents.cash')}`
                                : student.paymentMode === 'ONLINE' ? `💳 ${t('adminStudents.online')}` : '—'}
                        </p>
                    </div>
                </div>
            </div>

            {/* Payment History */}
            <div className="card p-6 mb-6">
                <h2 className="section-title text-lg mb-4">{t('adminStudentDetail.paymentHistory')}</h2>
                {paymentsLoading ? (
                    <div className="shimmer h-16 rounded-xl" />
                ) : payments.filter(p => p.status === 'SUCCESS').length === 0 ? (
                    <p className="text-primary-500 text-xs text-center py-3">{t('adminStudentDetail.noPayments')}</p>
                ) : (
                    <div className="space-y-2">
                        {payments.filter(p => p.status === 'SUCCESS').map(p => {
                            const isCash = p.paymentGateway === 'CASH'
                            return (
                                <div key={p.id} className="rounded-lg bg-primary-800/40 border border-primary-700/30 px-3 py-2.5 text-xs">
                                    <div className="flex items-center justify-between mb-1.5">
                                        <span className="text-white font-semibold">₹{Number(p.amount).toLocaleString('en-IN')}</span>
                                        <span className={`px-2 py-0.5 rounded-full font-medium border ${isCash ? 'bg-amber-500/20 text-amber-400 border-amber-500/30' : 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30'}`}>
                                            {isCash ? t('adminStudents.cash') : t('adminStudents.online')}
                                        </span>
                                    </div>
                                    <div className="text-primary-400 space-y-0.5">
                                        <p>{p.paidAt ? new Date(p.paidAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—'}</p>
                                        {p.gatewayOrderId   && <p className="font-mono">Order: {p.gatewayOrderId}</p>}
                                        {p.gatewayPaymentId && <p className="font-mono">Ref: {p.gatewayPaymentId}</p>}
                                    </div>
                                </div>
                            )
                        })}
                    </div>
                )}
            </div>

            {/* Seat History */}
            <div className="card p-6">
                <h2 className="section-title text-lg mb-4">{t('adminStudentDetail.seatHistory.title')}</h2>
                {seatHistoryLoading ? (
                    <div className="shimmer h-16 rounded-xl" />
                ) : seatHistory.length === 0 ? (
                    <p className="text-primary-500 text-xs text-center py-3">{t('adminStudentDetail.seatHistory.empty')}</p>
                ) : (
                    <div className="space-y-2">
                        {seatHistory.map(h => (
                            <div key={h.membershipId} className="rounded-lg bg-primary-800/40 border border-primary-700/30 px-3 py-2.5 text-xs">
                                <div className="flex items-center justify-between mb-1.5">
                                    <span className="text-white font-mono font-semibold">{h.seatNumber || '—'}</span>
                                    <span className="px-2 py-0.5 rounded-full font-medium border bg-primary-700/40 text-primary-300 border-primary-600/40">
                                        {h.status}
                                    </span>
                                </div>
                                <div className="text-primary-400 space-y-0.5">
                                    <p>{h.startDate} → {h.endDate} · {shiftLabel(h.shift)}</p>
                                    {h.planName && <p>{h.planName}</p>}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {showPhotoPreview && student.photoUrl && (
                <div
                    className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center p-4"
                    onClick={() => setShowPhotoPreview(false)}
                >
                    <div className="relative max-w-lg w-full" onClick={e => e.stopPropagation()}>
                        <img
                            src={student.photoUrl}
                            alt={student.name}
                            className="w-full max-h-[80vh] object-contain rounded-xl"
                        />
                        <button
                            onClick={() => setShowPhotoPreview(false)}
                            className="btn-outline px-4 py-2 text-sm mt-4 mx-auto block"
                        >
                            ✕ Close
                        </button>
                    </div>
                </div>
            )}
        </div>
    )
}
