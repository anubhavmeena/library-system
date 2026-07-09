// Colors the resolved student membership display status (see
// resolve_display_status on the backend): NEW | PAID | PENDING | GRACE |
// GRACE_OVERDUE | RELEASED.
//
// This is a distinct component/domain from MembershipPage.jsx's local
// StatusBadge, which colors raw Membership.status / Payment.status values
// for history and payment-log rows — same label text can mean different
// things (e.g. that component's "PENDING" is an unpaid Payment row, not a
// membership with an outstanding balance).
const STATUS_STYLES = {
    PAID:          'bg-emerald-500/20 text-emerald-400 border-emerald-500/30',
    PENDING:       'bg-amber-500/20 text-amber-400 border-amber-500/30',
    GRACE:         'bg-amber-500/20 text-amber-400 border-amber-500/30',
    GRACE_OVERDUE: 'bg-red-500/20 text-red-400 border-red-500/30',
    RELEASED:      'bg-primary-700/40 text-primary-400 border-primary-600/30',
    NEW:           'bg-blue-500/20 text-blue-400 border-blue-500/30',
}

// GRACE_OVERDUE is an internal bucket name (distinct from the DB's terminal
// EXPIRED status, see rust-backend/CLAUDE.md) — students still just see "EXPIRED".
const STATUS_LABELS = {
    GRACE_OVERDUE: 'EXPIRED',
}

export default function StatusBadge({ status }) {
    if (!status) return null
    return (
        <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border ${STATUS_STYLES[status] || STATUS_STYLES.NEW}`}>
            <span className="w-1.5 h-1.5 rounded-full bg-current" />
            {STATUS_LABELS[status] || status}
        </span>
    )
}
