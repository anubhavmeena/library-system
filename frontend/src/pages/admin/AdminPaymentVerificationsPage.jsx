import { useState, useEffect } from 'react'
import api from '../../services/api'
import toast from 'react-hot-toast'
import { formatCurrency } from '../../utils/currency'

const STATUS_COLORS = {
    PENDING:  'bg-amber-500/20 text-amber-400 border-amber-500/30',
    VERIFIED: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30',
    REJECTED: 'bg-red-500/20 text-red-400 border-red-500/30',
}

const CLAIM_TYPE_LABELS = {
    DUES: 'Grace Dues',
    PENDING_FEE: 'Pending Fee',
}

export default function AdminPaymentVerificationsPage() {
    const [list, setList]         = useState([])
    const [loading, setLoading]   = useState(true)
    const [reviewing, setReviewing] = useState(null) // { id, action: 'VERIFIED'|'REJECTED' }
    const [statusFilter, setStatusFilter] = useState('PENDING')
    const [previewUrl, setPreviewUrl] = useState(null)

    const fetchList = () => {
        setLoading(true)
        const params = new URLSearchParams()
        if (statusFilter) params.set('status', statusFilter)
        api.get(`/admin/payment-claims?${params.toString()}`)
            .then(r => setList(r.data.data || []))
            .catch(() => toast.error('Failed to load payment verifications'))
            .finally(() => setLoading(false))
    }

    useEffect(() => { fetchList() }, [statusFilter])

    const handleReview = async (id, status) => {
        setReviewing({ id, status })
        try {
            await api.patch(`/admin/payment-claims/${id}`, { status })
            toast.success(status === 'VERIFIED' ? 'Payment verified and applied' : 'Claim rejected')
            fetchList()
        } catch (err) {
            toast.error(err.response?.data?.message || 'Failed to update claim')
        } finally {
            setReviewing(null)
        }
    }

    return (
        <div>
            <div className="flex items-start justify-between mb-6 gap-4">
                <div>
                    <h1 className="page-header">Verify Payments</h1>
                    <p className="text-primary-400 mt-1">
                        Student-submitted UPI payment proofs awaiting review ({list.length})
                    </p>
                </div>
                <button onClick={fetchList}
                    className="btn-ghost border border-primary-700/40 text-sm px-4 py-2 rounded-xl flex-shrink-0">
                    ↻ Refresh
                </button>
            </div>

            <div className="flex flex-wrap gap-2 mb-6">
                {[
                    { v: 'PENDING',  l: 'Pending' },
                    { v: 'VERIFIED', l: 'Verified' },
                    { v: 'REJECTED', l: 'Rejected' },
                    { v: '',         l: 'All' },
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
                    {[1, 2, 3].map(i => <div key={i} className="shimmer h-24 rounded-xl" />)}
                </div>
            ) : list.length === 0 ? (
                <div className="card p-12 text-center">
                    <p className="text-4xl mb-3">✅</p>
                    <p className="text-white font-semibold">Nothing to review</p>
                    <p className="text-primary-400 text-sm mt-1">No payment verifications match this filter.</p>
                </div>
            ) : (
                <div className="grid gap-4">
                    {list.map(claim => (
                        <div key={claim.id} className="card p-5 flex gap-4 items-start flex-wrap sm:flex-nowrap">
                            <img
                                src={claim.screenshotUrl}
                                alt="Payment screenshot"
                                onClick={() => setPreviewUrl(claim.screenshotUrl)}
                                className="w-20 h-20 object-cover rounded-lg cursor-pointer border border-primary-700/40 flex-shrink-0"
                            />
                            <div className="flex-1 min-w-[200px]">
                                <div className="flex items-center gap-2 flex-wrap mb-1">
                                    <span className="text-white font-medium">{claim.studentName}</span>
                                    <span className={`text-xs px-2 py-0.5 rounded-full border font-medium ${STATUS_COLORS[claim.status]}`}>
                                        {claim.status}
                                    </span>
                                    <span className="text-xs px-2 py-0.5 rounded-full border border-primary-700/40 text-primary-300">
                                        {CLAIM_TYPE_LABELS[claim.claimType] || claim.claimType}
                                    </span>
                                </div>
                                <p className="text-primary-400 text-xs">{claim.studentMobile || '—'}</p>
                                <p className="text-white font-semibold mt-1">{formatCurrency(claim.amountClaimed)}</p>
                                <p className="text-primary-500 text-xs mt-0.5">{claim.createdAt?.replace('T', ' ').slice(0, 16)}</p>
                            </div>
                            {claim.status === 'PENDING' && (
                                <div className="flex gap-2 flex-shrink-0">
                                    <button
                                        disabled={reviewing?.id === claim.id}
                                        onClick={() => handleReview(claim.id, 'REJECTED')}
                                        className="text-xs px-3 py-2 rounded-lg bg-red-600/20 text-red-400 border border-red-500/30 hover:bg-red-600/30 transition-all disabled:opacity-50">
                                        {reviewing?.id === claim.id && reviewing.status === 'REJECTED' ? 'Rejecting…' : 'Reject'}
                                    </button>
                                    <button
                                        disabled={reviewing?.id === claim.id}
                                        onClick={() => handleReview(claim.id, 'VERIFIED')}
                                        className="text-xs px-3 py-2 rounded-lg bg-emerald-600 text-white hover:bg-emerald-500 transition-all disabled:opacity-50">
                                        {reviewing?.id === claim.id && reviewing.status === 'VERIFIED' ? 'Verifying…' : 'Verify'}
                                    </button>
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}

            {previewUrl && (
                <div
                    className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center p-4"
                    onClick={() => setPreviewUrl(null)}
                >
                    <div className="relative max-w-lg w-full" onClick={e => e.stopPropagation()}>
                        <img src={previewUrl} alt="Payment screenshot" className="w-full max-h-[80vh] object-contain rounded-xl" />
                        <button
                            onClick={() => setPreviewUrl(null)}
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
