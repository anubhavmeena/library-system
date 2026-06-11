import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

const STATUS_COLORS = {
    OPEN:         'bg-amber-500/20 text-amber-400 border-amber-500/30',
    UNDER_REVIEW: 'bg-blue-500/20 text-blue-400 border-blue-500/30',
    RESOLVED:     'bg-emerald-500/20 text-emerald-400 border-emerald-500/30',
}

export default function FeedbackPage() {
    const { t } = useTranslation()
    const [list, setList]             = useState([])
    const [loading, setLoading]       = useState(true)
    const [submitting, setSubmitting] = useState(false)
    const [showForm, setShowForm]     = useState(false)
    const [detail, setDetail]         = useState(null)
    const [form, setForm]             = useState({ type: 'FEEDBACK', subject: '', description: '' })

    const fetchList = () => {
        setLoading(true)
        api.get('/users/feedback/my')
            .then(r => setList(r.data.data || []))
            .catch(() => toast.error(t('feedback.toasts.loadFailed')))
            .finally(() => setLoading(false))
    }

    useEffect(() => { fetchList() }, [])

    const handleSubmit = async (e) => {
        e.preventDefault()
        if (!form.subject.trim() || !form.description.trim()) {
            toast.error(t('feedback.toasts.fillRequired'))
            return
        }
        setSubmitting(true)
        try {
            await api.post('/users/feedback', form)
            toast.success(t('feedback.toasts.submitted'))
            setForm({ type: 'FEEDBACK', subject: '', description: '' })
            setShowForm(false)
            fetchList()
        } catch (err) {
            toast.error(err.response?.data?.message || t('feedback.toasts.submitFailed'))
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div>
            <div className="flex items-start justify-between mb-6 gap-4">
                <div>
                    <h1 className="page-header">{t('feedback.title')}</h1>
                    <p className="text-primary-400 mt-1">{t('feedback.subtitle')}</p>
                </div>
                <button
                    onClick={() => setShowForm(v => !v)}
                    className="btn-primary text-sm px-4 py-2 flex-shrink-0">
                    {showForm ? t('feedback.cancel') : t('feedback.newBtn')}
                </button>
            </div>

            {showForm && (
                <div className="card p-6 mb-6 border-amber-500/20">
                    <h3 className="section-title mb-4">{t('feedback.formTitle')}</h3>
                    <form onSubmit={handleSubmit} className="space-y-4">
                        <div className="flex gap-3">
                            {['FEEDBACK', 'COMPLAINT'].map(tp => (
                                <button type="button" key={tp}
                                    onClick={() => setForm(f => ({ ...f, type: tp }))}
                                    className={`px-4 py-2 rounded-xl text-sm font-medium border transition-all
                                        ${form.type === tp
                                            ? 'bg-amber-500/20 border-amber-400/60 text-amber-400'
                                            : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                                    {t(`feedback.types.${tp}`)}
                                </button>
                            ))}
                        </div>
                        <div>
                            <label className="label">{t('feedback.form.subject')}</label>
                            <input
                                className="input w-full mt-1"
                                value={form.subject}
                                onChange={e => setForm(f => ({ ...f, subject: e.target.value }))}
                                placeholder={t('feedback.form.subjectPlaceholder')}
                                maxLength={255} />
                        </div>
                        <div>
                            <label className="label">{t('feedback.form.description')}</label>
                            <textarea
                                className="input w-full h-28 resize-none mt-1"
                                value={form.description}
                                onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                                placeholder={t('feedback.form.descriptionPlaceholder')} />
                        </div>
                        <button type="submit" disabled={submitting} className="btn-primary disabled:opacity-50">
                            {submitting ? t('feedback.form.submitting') : t('feedback.form.submit')}
                        </button>
                    </form>
                </div>
            )}

            {loading ? (
                <div className="space-y-3">
                    {[1, 2, 3].map(i => <div key={i} className="shimmer h-20 rounded-xl" />)}
                </div>
            ) : list.length === 0 ? (
                <div className="card p-12 text-center">
                    <p className="text-4xl mb-3">💬</p>
                    <p className="text-white font-semibold">{t('feedback.empty.title')}</p>
                    <p className="text-primary-400 text-sm mt-1">{t('feedback.empty.desc')}</p>
                </div>
            ) : (
                <div className="space-y-3">
                    {list.map(item => (
                        <div key={item.id}
                            className="card p-5 cursor-pointer hover:border-amber-500/30 transition-all"
                            onClick={() => setDetail(item)}>
                            <div className="flex items-start justify-between gap-3">
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2 mb-1.5 flex-wrap">
                                        <span className={`text-xs px-2 py-0.5 rounded-full border font-medium
                                            ${item.type === 'COMPLAINT'
                                                ? 'bg-red-500/20 text-red-400 border-red-500/30'
                                                : 'bg-blue-500/20 text-blue-400 border-blue-500/30'}`}>
                                            {t(`feedback.types.${item.type}`)}
                                        </span>
                                        <span className={`text-xs px-2 py-0.5 rounded-full border font-medium ${STATUS_COLORS[item.status]}`}>
                                            {t(`feedback.statuses.${item.status}`)}
                                        </span>
                                    </div>
                                    <p className="text-white font-medium truncate">{item.subject}</p>
                                    <p className="text-primary-500 text-xs mt-0.5">{item.createdAt?.split('T')[0]}</p>
                                </div>
                                <span className="text-primary-600 text-sm flex-shrink-0 mt-1">→</span>
                            </div>
                            {item.adminNotes && (
                                <div className="mt-3 pt-3 border-t border-primary-700/30">
                                    <p className="text-xs text-primary-400">{t('feedback.adminResponse')}</p>
                                    <p className="text-primary-200 text-sm mt-1 line-clamp-2">{item.adminNotes}</p>
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}

            {detail && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
                    onClick={() => setDetail(null)}>
                    <div className="card p-6 w-full max-w-lg border-amber-500/20 overflow-y-auto max-h-[90vh]"
                        onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-5">
                            <h3 className="section-title">{t('feedback.modal.title')}</h3>
                            <button onClick={() => setDetail(null)} className="text-primary-400 hover:text-white text-lg">✕</button>
                        </div>
                        <div className="flex gap-2 mb-4 flex-wrap">
                            <span className={`text-xs px-2 py-1 rounded-full border font-medium
                                ${detail.type === 'COMPLAINT'
                                    ? 'bg-red-500/20 text-red-400 border-red-500/30'
                                    : 'bg-blue-500/20 text-blue-400 border-blue-500/30'}`}>
                                {t(`feedback.types.${detail.type}`)}
                            </span>
                            <span className={`text-xs px-2 py-1 rounded-full border font-medium ${STATUS_COLORS[detail.status]}`}>
                                {t(`feedback.statuses.${detail.status}`)}
                            </span>
                        </div>
                        <p className="text-white font-bold text-lg mb-1">{detail.subject}</p>
                        <p className="text-primary-400 text-xs mb-4">
                            {t('feedback.modal.submitted')} {detail.createdAt?.split('T')[0]}
                        </p>
                        <p className="text-primary-200 text-sm mb-5 whitespace-pre-wrap leading-relaxed">{detail.description}</p>
                        {detail.adminNotes && (
                            <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/20">
                                <p className="text-emerald-400 text-xs font-medium mb-2">{t('feedback.modal.adminResponse')}</p>
                                <p className="text-primary-100 text-sm whitespace-pre-wrap leading-relaxed">{detail.adminNotes}</p>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    )
}
