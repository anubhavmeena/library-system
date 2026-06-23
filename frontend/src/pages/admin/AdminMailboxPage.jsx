import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

function fmtMailDate(dateStr) {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    if (isNaN(d)) return dateStr
    const diff = Date.now() - d.getTime()
    if (diff < 86_400_000) return d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })
    if (diff < 604_800_000) return d.toLocaleDateString('en-IN', { weekday: 'short' })
    return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: '2-digit' })
}

function extractEmailAddress(from) {
    if (!from) return ''
    const m = from.match(/<(.+?)>/)
    return m ? m[1] : from
}

export default function AdminMailboxPage() {
    const { t } = useTranslation()
    const iframeRef = useRef(null)

    const [messages,      setMessages]      = useState([])
    const [selected,      setSelected]      = useState(null)
    const [loading,       setLoading]       = useState(false)
    const [detailLoading, setDetailLoading] = useState(false)
    const [replyText,     setReplyText]     = useState('')
    const [showReply,     setShowReply]     = useState(false)
    const [replySending,  setReplySending]  = useState(false)

    const fetchMessages = async () => {
        setLoading(true)
        try {
            const res = await api.get('/admin/inbox')
            setMessages(res.data.data ?? [])
        } catch {
            toast.error(t('adminMailbox.loadFailed'))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => { fetchMessages() }, [])

    const selectMessage = async (msg) => {
        if (selected?.messageNumber === msg.messageNumber) return
        setSelected(null)
        setShowReply(false)
        setReplyText('')
        setDetailLoading(true)
        try {
            const res = await api.get(`/admin/inbox/${msg.messageNumber}`)
            const detail = res.data.data
            setSelected(detail)
            // Mark as read in local list
            setMessages(prev => prev.map(m =>
                m.messageNumber === msg.messageNumber ? { ...m, isRead: true } : m
            ))
        } catch {
            toast.error(t('adminMailbox.loadFailed'))
        } finally {
            setDetailLoading(false)
        }
    }

    const sendReply = async () => {
        if (!replyText.trim()) return
        setReplySending(true)
        try {
            await api.post(`/admin/inbox/${selected.messageNumber}/reply`, { body: replyText })
            toast.success(t('adminMailbox.replySent'))
            setShowReply(false)
            setReplyText('')
        } catch {
            toast.error(t('adminMailbox.replyFailed'))
        } finally {
            setReplySending(false)
        }
    }

    const deleteMessage = async () => {
        try {
            await api.delete(`/admin/inbox/${selected.messageNumber}`)
            setMessages(prev => prev.filter(m => m.messageNumber !== selected.messageNumber))
            setSelected(null)
            setShowReply(false)
            setReplyText('')
        } catch {
            toast.error(t('adminMailbox.deleteFailed'))
        }
    }

    const unread = messages.filter(m => !m.isRead).length

    return (
        <div className="max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex items-center justify-between mb-6">
                <div>
                    <p className="text-primary-500 text-xs uppercase tracking-widest mb-1">{t('adminMailbox.subtitle')}</p>
                    <h1 className="page-header">{t('adminMailbox.title')}</h1>
                </div>
                <div className="flex items-center gap-3">
                    {unread > 0 && (
                        <span className="bg-amber-500/20 text-amber-400 border border-amber-500/30 text-xs font-semibold px-2.5 py-1 rounded-full">
                            {unread} unread
                        </span>
                    )}
                    <button onClick={fetchMessages} disabled={loading}
                            className="btn-outline text-sm px-4 py-2 disabled:opacity-50">
                        {loading ? '⟳' : t('adminMailbox.refresh')}
                    </button>
                </div>
            </div>

            <div className="flex gap-4 h-[calc(100vh-12rem)]">
                {/* Left — message list */}
                <div className="w-80 flex-shrink-0 card overflow-y-auto">
                    {loading && messages.length === 0 ? (
                        <div className="p-6 space-y-3">
                            {[1,2,3,4].map(i => <div key={i} className="shimmer h-14 rounded-lg" />)}
                        </div>
                    ) : messages.length === 0 ? (
                        <div className="flex flex-col items-center justify-center h-full py-16 text-primary-500">
                            <span className="text-4xl mb-3">✉</span>
                            <p className="text-sm">{t('adminMailbox.empty')}</p>
                        </div>
                    ) : (
                        <ul className="divide-y divide-primary-800/60">
                            {messages.map(msg => (
                                <li key={msg.messageNumber}
                                    onClick={() => selectMessage(msg)}
                                    className={`px-4 py-3 cursor-pointer transition-colors hover:bg-primary-800/40
                                        ${selected?.messageNumber === msg.messageNumber ? 'bg-primary-800/60 border-l-2 border-amber-500' : ''}
                                        ${!msg.isRead ? 'border-l-2 border-amber-500/60' : ''}`}>
                                    <div className="flex items-start gap-2">
                                        {!msg.isRead && (
                                            <span className="mt-1.5 w-2 h-2 rounded-full bg-amber-400 flex-shrink-0" />
                                        )}
                                        <div className={`min-w-0 flex-1 ${msg.isRead ? 'pl-4' : ''}`}>
                                            <p className={`text-sm truncate ${msg.isRead ? 'text-primary-300' : 'text-white font-semibold'}`}>
                                                {extractEmailAddress(msg.from)}
                                            </p>
                                            <p className={`text-xs truncate mt-0.5 ${msg.isRead ? 'text-primary-500' : 'text-primary-300'}`}>
                                                {msg.subject}
                                            </p>
                                        </div>
                                        <span className="text-primary-600 text-xs flex-shrink-0 mt-0.5">
                                            {fmtMailDate(msg.date)}
                                        </span>
                                    </div>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>

                {/* Right — detail panel */}
                <div className="flex-1 card overflow-y-auto flex flex-col">
                    {detailLoading ? (
                        <div className="p-8 space-y-4">
                            <div className="shimmer h-6 w-2/3 rounded" />
                            <div className="shimmer h-4 w-1/3 rounded" />
                            <div className="shimmer h-64 rounded-lg mt-6" />
                        </div>
                    ) : selected ? (
                        <div className="flex flex-col h-full">
                            {/* Message header */}
                            <div className="px-6 py-5 border-b border-primary-800/60 flex-shrink-0">
                                <h2 className="text-white font-semibold text-lg leading-snug mb-3">
                                    {selected.subject}
                                </h2>
                                <div className="flex items-center justify-between flex-wrap gap-2">
                                    <div className="text-sm text-primary-400">
                                        <span className="text-primary-500 mr-1">{t('adminMailbox.from')}:</span>
                                        <span className="text-primary-200">{selected.from}</span>
                                    </div>
                                    <span className="text-primary-500 text-xs">{selected.date}</span>
                                </div>
                            </div>

                            {/* Email body */}
                            <div className="flex-1 p-4 overflow-auto">
                                <iframe
                                    ref={iframeRef}
                                    srcDoc={selected.body || '<em>Empty message</em>'}
                                    sandbox="allow-same-origin"
                                    title="Email body"
                                    className="w-full rounded-lg border border-primary-800/40 bg-white"
                                    style={{ minHeight: '300px', height: '100%' }}
                                    onLoad={e => {
                                        try {
                                            const doc = e.target.contentDocument
                                            if (doc) {
                                                const h = doc.documentElement.scrollHeight
                                                e.target.style.height = Math.max(300, h) + 'px'
                                            }
                                        } catch (_) {}
                                    }}
                                />
                            </div>

                            {/* Action bar */}
                            <div className="px-6 py-4 border-t border-primary-800/60 flex-shrink-0 space-y-3">
                                {!showReply ? (
                                    <div className="flex gap-3">
                                        <button onClick={() => setShowReply(true)} className="btn-primary text-sm px-5 py-2">
                                            ↩ {t('adminMailbox.reply')}
                                        </button>
                                        <button onClick={deleteMessage} className="btn-outline text-sm px-5 py-2 text-red-400 border-red-500/30 hover:bg-red-500/10">
                                            🗑 {t('adminMailbox.delete')}
                                        </button>
                                    </div>
                                ) : (
                                    <div className="space-y-3">
                                        <p className="text-primary-400 text-xs">
                                            Replying to <span className="text-amber-400">{extractEmailAddress(selected.from)}</span>
                                        </p>
                                        <textarea
                                            rows={5}
                                            className="input resize-none w-full"
                                            placeholder={t('adminMailbox.replyPlaceholder')}
                                            value={replyText}
                                            onChange={e => setReplyText(e.target.value)}
                                        />
                                        <div className="flex gap-3">
                                            <button onClick={sendReply} disabled={replySending || !replyText.trim()}
                                                    className="btn-primary text-sm px-5 py-2 disabled:opacity-50">
                                                {replySending ? '...' : t('adminMailbox.sendReply')}
                                            </button>
                                            <button onClick={() => { setShowReply(false); setReplyText('') }}
                                                    className="btn-ghost text-sm px-4 py-2 text-primary-400">
                                                {t('adminMailbox.cancel')}
                                            </button>
                                        </div>
                                    </div>
                                )}
                            </div>
                        </div>
                    ) : (
                        <div className="flex flex-col items-center justify-center h-full text-primary-500 py-16">
                            <span className="text-5xl mb-4 opacity-30">✉</span>
                            <p className="text-sm">{messages.length > 0 ? 'Select a message to read' : t('adminMailbox.empty')}</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    )
}
