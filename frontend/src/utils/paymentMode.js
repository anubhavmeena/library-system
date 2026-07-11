// Shared bucketing for the admin-facing "Payment Mode" concept. Works on both
// the bucketed `paymentMode` field (CASH / UPI-QR / ONLINE-PG / null, from
// StudentListItem) and the raw `paymentGateway` field on individual payment
// rows (CASH / UPI-QR / RAZORPAY / CASHFREE / ...) — anything that isn't
// CASH or UPI-QR falls into the generic "Online" bucket regardless of which
// real gateway wrote it, matching the backend's STUDENT_SELECT CASE logic.
export function paymentModeInfo(mode, t) {
    if (mode === 'CASH') {
        return { emoji: '💵', label: t('adminStudents.cash'), className: 'bg-amber-500/20 text-amber-400 border-amber-500/30' }
    }
    if (mode === 'UPI-QR') {
        return { emoji: '📱', label: t('adminStudents.upiQr'), className: 'bg-sky-500/20 text-sky-400 border-sky-500/30' }
    }
    if (mode) {
        return { emoji: '💳', label: t('adminStudents.online'), className: 'bg-indigo-500/20 text-indigo-400 border-indigo-500/30' }
    }
    return null
}
