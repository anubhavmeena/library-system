import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

const STATUS_ORDER = ['OPEN', 'UNDER_REVIEW', 'RESOLVED']
const STATUS_COLORS = {
    OPEN:         'bg-amber-500/20 text-amber-400 border-amber-500/30',
    UNDER_REVIEW: 'bg-blue-500/20 text-blue-400 border-blue-500/30',
    RESOLVED:     'bg-emerald-500/20 text-emerald-400 border-emerald-500/30',
}

export default function AdminFeedbackPage() {
    const { t } = useTranslation()
    const [list, setList]                 = useState([])
    const [loading, setLoading]           = useState(true)
    const [saving, setSaving]             = useState(false)
    const [typeFilter, setTypeFilter]     = useState('')
    const [statusFilter, setStatusFilter] = useState('')
    const [editing, setEditing]           = useState(null)

    const fetchList = () => {
        setLoading(true)
        const params = new URLSearchParams()
        if (typeFilter)   params.set('type', typeFilter)
        if (statusFilter) params.set('status', statusFilter)
        api.get(`/admin/feedback?${params.toString()}`)
            .then(r => setList(r.data.data || []))
            .catch(() => toast.error(t('adminFeedback.toasts.loadFailed')))
            .finally(() => setLoading(false))
    }

    useEffect(() => { fetchList() }, [typeFilter, statusFilter])

    const handleSave = async () => {
        setSaving(true)
        try {
            await api.patch(`/admin/feedback/${editing.id}`, {
                status: editing.status,
                adminNotes: editing.adminNotes,
            })
            toast.success(t('adminFeedback.toasts.updated'))
            setEditing(null)
            fetchList()
        } catch (err) {
            toast.error(err.response?.data?.message || t('adminFeedback.toasts.updateFailed'))
        } finally {
            setSaving(false)
        }
    }

    return (
        <div>
            <div className="flex items-start justify-between mb-6 gap-4">
                <div>
                    <h1 className="page-header">{t('adminFeedback.title')}</h1>
                    <p className="text-primary-400 mt-1">
                        {t('adminFeedback.subtitle', { count: list.length })}
                    </p>
                </div>
                <button onClick={fetchList}
                    className="btn-ghost border border-primary-700/40 text-sm px-4 py-2 rounded-xl flex-shrink-0">
                    ↻ {t('adminFeedback.refresh')}
                </button>
            </div>

            <div className="flex flex-wrap gap-2 mb-6">
                {[
                    { v: '',          l: t('adminFeedback.filters.allTypes') },
                    { v: 'FEEDBACK',  l: t('feedback.types.FEEDBACK') },
                    { v: 'COMPLAINT', l: t('feedback.types.COMPLAINT') },
                ].map(({ v, l }) => (
                    <button key={v} onClick={() => setTypeFilter(v)}
                        className={`px-4 py-2 rounded-xl text-sm font-medium border transition-all
                            ${typeFilter === v
                                ? 'bg-red-500/20 border-red-400/60 text-red-400'
                                : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                        {l}
                    </button>
                ))}
                <div className="w-px bg-primary-700/40 mx-1" />
                {[
                    { v: '',             l: t('adminFeedback.filters.allStatuses') },
                    { v: 'OPEN',         l: t('feedback.statuses.OPEN') },
                    { v: 'UNDER_REVIEW', l: t('feedback.statuses.UNDER_REVIEW') },
                    { v: 'RESOLVED',     l: t('feedback.statuses.RESOLVED') },
                ].map(({ v, l }) => (
                    <button key={v} onClick={() => setStatusFilter(v)}
                        className={`px-4 py-2 rounded-xl text-sm font-medium border transition-all
                            ${statusFilter === v
                                ? 'bg-red-500/20 border-red-400/60 text-red-400'
                                : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                        {l}
                    </button>
                ))}
            </div>

            {loading ? (
                <div className="space-y-3">
                    {[1, 2, 3, 4].map(i => <div key={i} className="shimmer h-16 rounded-xl" />)}
                </div>
            ) : list.length === 0 ? (
                <div className="card p-12 text-center">
                    <p className="text-4xl mb-3">💬</p>
                    <p className="text-white font-semibold">{t('adminFeedback.empty.title')}</p>
                    <p className="text-primary-400 text-sm mt-1">{t('adminFeedback.empty.desc')}</p>
                </div>
            ) : (
                <div className="card overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-primary-700/40">
                                    {[
                                        t('adminFeedback.table.student'),
                                        t('adminFeedback.table.type'),
                                        t('adminFeedback.table.subject'),
                                        t('adminFeedback.table.status'),
                                        t('adminFeedback.table.date'),
                                        t('adminFeedback.table.actions'),
                                    ].map(h => (
                                        <th key={h} className="p-4 text-left text-primary-400 font-medium whitespace-nowrap">{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-primary-700/20">
                                {list.map(item => (
                                    <tr key={item.id} className="hover:bg-primary-800/30 transition-colors">
                                        <td className="p-4">
                                            <p className="text-white font-medium">{item.studentName}</p>
                                            <p className="text-primary-500 text-xs">{item.studentMobile || '—'}</p>
                                        </td>
                                        <td className="p-4">
                                            <span className={`text-xs px-2 py-1 rounded-full border font-medium whitespace-nowrap
                                                ${item.type === 'COMPLAINT'
                                                    ? 'bg-red-500/20 text-red-400 border-red-500/30'
                                                    : 'bg-blue-500/20 text-blue-400 border-blue-500/30'}`}>
                                                {t(`feedback.types.${item.type}`)}
                                            </span>
                                        </td>
                                        <td className="p-4 max-w-xs">
                                            <p className="text-white truncate">{item.subject}</p>
                                            {item.adminNotes && (
                                                <p className="text-primary-500 text-xs mt-0.5 truncate">↳ {item.adminNotes}</p>
                                            )}
                                        </td>
                                        <td className="p-4">
                                            <span className={`text-xs px-2 py-1 rounded-full border font-medium whitespace-nowrap ${STATUS_COLORS[item.status]}`}>
                                                {t(`feedback.statuses.${item.status}`)}
                                            </span>
                                        </td>
                                        <td className="p-4 text-primary-400 text-xs whitespace-nowrap">
                                            {item.createdAt?.split('T')[0]}
                                        </td>
                                        <td className="p-4">
                                            <button
                                                onClick={() => setEditing({ id: item.id, status: item.status, adminNotes: item.adminNotes || '' })}
                                                className="text-xs px-3 py-1.5 rounded-lg bg-primary-700/50 text-primary-300 hover:text-white border border-primary-700/40 transition-all whitespace-nowrap">
                                                {t('adminFeedback.respond')}
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {editing && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
                    onClick={() => setEditing(null)}>
                    <div className="card p-6 w-full max-w-md border-red-900/30"
                        onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-5">
                            <h3 className="section-title">{t('adminFeedback.modal.title')}</h3>
                            <button onClick={() => setEditing(null)} className="text-primary-400 hover:text-white text-lg">✕</button>
                        </div>
                        <div className="space-y-4">
                            <div>
                                <label className="label">{t('adminFeedback.modal.status')}</label>
                                <div className="flex gap-2 mt-1">
                                    {STATUS_ORDER.map(s => (
                                        <button key={s} type="button"
                                            onClick={() => setEditing(e => ({ ...e, status: s }))}
                                            className={`flex-1 py-2 rounded-xl text-xs font-medium border transition-all
                                                ${editing.status === s
                                                    ? STATUS_COLORS[s]
                                                    : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                                            {t(`feedback.statuses.${s}`)}
                                        </button>
                                    ))}
                                </div>
                            </div>
                            <div>
                                <label className="label">{t('adminFeedback.modal.notes')}</label>
                                <textarea
                                    className="input w-full h-28 resize-none mt-1"
                                    value={editing.adminNotes}
                                    onChange={e => setEditing(ed => ({ ...ed, adminNotes: e.target.value }))}
                                    placeholder={t('adminFeedback.modal.notesPlaceholder')} />
                            </div>
                        </div>
                        <div className="flex justify-end gap-3 mt-6">
                            <button onClick={() => setEditing(null)}
                                className="btn-ghost text-sm px-4 py-2 border border-primary-700/40 rounded-xl">
                                {t('adminFeedback.modal.cancel')}
                            </button>
                            <button onClick={handleSave} disabled={saving}
                                className="bg-red-600 hover:bg-red-500 disabled:opacity-50 text-white font-semibold px-5 py-2.5 rounded-xl text-sm transition-all">
                                {saving ? t('adminFeedback.modal.saving') : t('adminFeedback.modal.save')}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
