import { useState, useEffect } from 'react'
import { useSelector } from 'react-redux'
import api from '../../services/api'

export default function ContactAdminPage() {
    const { current: membership } = useSelector(s => s.membership)
    const [contact, setContact]           = useState(null)
    const [loading, setLoading]           = useState(true)
    const [callAdminSent, setCallAdminSent] = useState(false)

    useEffect(() => {
        api.get('/users/admin-contact')
            .then(r => setContact(r.data.data))
            .catch(() => {})
            .finally(() => setLoading(false))
    }, [])

    const handleCallAdmin = async () => {
        setCallAdminSent(true)
        try { await api.post('/memberships/my/call-admin') } catch {}
        setTimeout(() => setCallAdminSent(false), 10000)
    }

    const hasActiveSeat = membership?.status === 'ACTIVE' && membership?.seatNumber

    return (
        <div>
            <div className="mb-8">
                <h1 className="page-header">Contact Admin</h1>
                <p className="text-primary-400">Reach the library admin directly</p>
            </div>

            {/* Admin contact info */}
            <div className="card p-6 mb-6">
                <h3 className="section-title mb-4">👤 Admin Contact</h3>
                {loading ? (
                    <div className="space-y-3">
                        {[1, 2, 3].map(i => (
                            <div key={i} className="h-5 shimmer rounded-lg w-48" />
                        ))}
                    </div>
                ) : (
                    <div className="space-y-4">
                        {contact?.name && (
                            <div className="flex items-center gap-3">
                                <span className="text-primary-400 w-16 text-sm">Name</span>
                                <span className="text-white font-medium">{contact.name}</span>
                            </div>
                        )}
                        {contact?.mobile && (
                            <div className="flex items-center gap-3">
                                <span className="text-primary-400 w-16 text-sm">Mobile</span>
                                <div className="flex items-center gap-3">
                                    <span className="text-white font-medium">{contact.mobile}</span>
                                    <a
                                        href={`tel:${contact.mobile}`}
                                        className="text-sm px-3 py-1 rounded-lg bg-blue-500/20 text-blue-400 border border-blue-500/30 hover:bg-blue-500/30 transition-colors"
                                    >
                                        📞 Call
                                    </a>
                                    <a
                                        href={`https://wa.me/91${contact.mobile}`}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="text-sm px-3 py-1 rounded-lg bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/30 transition-colors"
                                    >
                                        💬 WhatsApp
                                    </a>
                                </div>
                            </div>
                        )}
                        {contact?.email && (
                            <div className="flex items-center gap-3">
                                <span className="text-primary-400 w-16 text-sm">Email</span>
                                <div className="flex items-center gap-3">
                                    <span className="text-white font-medium">{contact.email}</span>
                                    <a
                                        href={`mailto:${contact.email}`}
                                        className="text-sm px-3 py-1 rounded-lg bg-amber-500/20 text-amber-400 border border-amber-500/30 hover:bg-amber-500/30 transition-colors"
                                    >
                                        ✉️ Email
                                    </a>
                                </div>
                            </div>
                        )}
                        {!contact?.mobile && !contact?.email && !loading && (
                            <p className="text-primary-400 text-sm">Contact details not available.</p>
                        )}
                    </div>
                )}
            </div>

            {/* Call Admin to Seat — only when student has an active seat */}
            {hasActiveSeat && (
                <div className="card p-6 border-blue-500/30 bg-blue-500/10">
                    <div className="flex items-start gap-4">
                        <span className="text-3xl">🙋</span>
                        <div className="flex-1">
                            <h3 className="text-blue-400 font-semibold text-lg mb-1">Need help at your seat?</h3>
                            <p className="text-primary-400 text-sm mb-4">
                                Seat <span className="text-white font-medium">{membership.seatNumber}</span> — tap the button to notify the admin via WhatsApp. They'll come to you.
                            </p>
                            <button
                                onClick={handleCallAdmin}
                                disabled={callAdminSent}
                                className="btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                {callAdminSent ? '✓ Admin Notified' : 'Call Admin to My Seat'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
