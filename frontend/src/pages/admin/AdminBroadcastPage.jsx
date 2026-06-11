import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

export default function AdminBroadcastPage() {
    const { t } = useTranslation()
    const [message, setMessage]       = useState('')
    const [sending, setSending]       = useState(false)
    const [lastResult, setLastResult] = useState(null)
    const [history, setHistory]       = useState([])

    const charLimit = 1000

    useEffect(() => { fetchHistory() }, [])

    const fetchHistory = async () => {
        try {
            const res = await api.get('/admin/broadcast/history')
            setHistory(res.data.data || [])
        } catch {
            // non-critical — don't show an error toast for history load failure
        }
    }

    const handleSend = async () => {
        if (message.trim().length < 5) {
            toast.error(t('adminBroadcast.toasts.tooShort'))
            return
        }
        setSending(true)
        try {
            const res = await api.post('/admin/broadcast', { message: message.trim() })
            const count = parseInt(res.data.data?.match(/\d+/)?.[0] ?? '0', 10)
            setLastResult(count)
            setMessage('')
            toast.success(t('adminBroadcast.toasts.sent', { count }))
            fetchHistory()
        } catch {
            toast.error(t('adminBroadcast.toasts.failed'))
        } finally {
            setSending(false)
        }
    }

    const formatDate = (iso) => {
        if (!iso) return ''
        const d = new Date(iso)
        return d.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' })
            + ' ' + d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
    }

    return (
        <div className="max-w-2xl">
            <div className="mb-6">
                <h1 className="page-header">{t('adminBroadcast.title')}</h1>
                <p className="text-primary-400">{t('adminBroadcast.subtitle')}</p>
            </div>

            <div className="card p-6 space-y-5">
                <div className="flex items-start gap-3 p-4 rounded-xl bg-amber-500/10 border border-amber-500/30">
                    <span className="text-amber-400 text-xl mt-0.5">📢</span>
                    <div>
                        <p className="text-amber-300 font-semibold text-sm">{t('adminBroadcast.notice.title')}</p>
                        <p className="text-amber-400/80 text-xs mt-0.5">{t('adminBroadcast.notice.desc')}</p>
                    </div>
                </div>

                <div>
                    <label className="label mb-2 block">{t('adminBroadcast.messageLabel')}</label>
                    <textarea
                        className="input w-full resize-none text-sm leading-relaxed"
                        rows={6}
                        maxLength={charLimit}
                        placeholder={t('adminBroadcast.messagePlaceholder')}
                        value={message}
                        onChange={e => setMessage(e.target.value)}
                    />
                    <p className={`text-xs mt-1 text-right ${message.length > charLimit * 0.9 ? 'text-amber-400' : 'text-primary-500'}`}>
                        {message.length}/{charLimit}
                    </p>
                </div>

                <div className="flex items-center gap-3 pt-1">
                    <button
                        onClick={handleSend}
                        disabled={sending || message.trim().length < 5}
                        className="btn-primary px-6 py-2.5 text-sm disabled:opacity-40 disabled:cursor-not-allowed">
                        {sending ? t('adminBroadcast.sending') : t('adminBroadcast.send')}
                    </button>
                    {message && (
                        <button onClick={() => setMessage('')}
                                className="btn-ghost border border-primary-700/40 px-4 py-2.5 text-sm rounded-xl">
                            {t('adminBroadcast.clear')}
                        </button>
                    )}
                </div>

                {lastResult !== null && (
                    <div className="flex items-center gap-2 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-sm">
                        <span className="text-emerald-400">✓</span>
                        <span className="text-emerald-300">{t('adminBroadcast.lastSent', { count: lastResult })}</span>
                    </div>
                )}
            </div>

            <div className="card p-5 mt-4">
                <h2 className="section-title text-sm mb-3">{t('adminBroadcast.preview.title')}</h2>
                <div className="rounded-xl bg-primary-900/60 border border-primary-700/30 p-4 text-sm text-primary-300 whitespace-pre-wrap font-mono">
                    {message.trim()
                        ? `📢 *Target Zone Library*\n\n${message.trim()}\n\n— Library Management`
                        : <span className="text-primary-600 italic">{t('adminBroadcast.preview.empty')}</span>
                    }
                </div>
            </div>

            {history.length > 0 && (
                <div className="card p-5 mt-4">
                    <h2 className="section-title text-sm mb-3">{t('adminBroadcast.history.title')}</h2>
                    <div className="space-y-2">
                        {history.map((item, i) => (
                            <div key={item.id ?? i}
                                 className="flex items-start justify-between gap-3 p-3 rounded-xl bg-primary-800/40 border border-primary-700/30 hover:border-primary-600/50 transition-colors">
                                <div className="min-w-0 flex-1">
                                    <p className="text-white text-sm leading-snug line-clamp-2">{item.message}</p>
                                    <p className="text-primary-500 text-xs mt-1">
                                        {formatDate(item.sentAt)}
                                        {' · '}
                                        <span className="text-primary-400">
                                            {t('adminBroadcast.history.recipients', { count: item.recipientCount })}
                                        </span>
                                    </p>
                                </div>
                                <button
                                    onClick={() => setMessage(item.message)}
                                    className="flex-shrink-0 text-xs px-3 py-1.5 rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/30 hover:bg-indigo-500/20 transition-all whitespace-nowrap">
                                    {t('adminBroadcast.history.load')}
                                </button>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    )
}
