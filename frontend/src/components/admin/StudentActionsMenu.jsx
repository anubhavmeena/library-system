import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'
import { formatCurrency, formatNumber } from '../../utils/currency'
import { paymentModeInfo } from '../../utils/paymentMode'

const ROWS = ['A', 'B', 'C', 'D']
const INACTIVE_SEATS = new Set(['B8', 'B18'])
const L_TOP    = [13, 11, 9, 7, 5, 3, 1]
const L_BOTTOM = [14, 12, 10, 8, 6, 4, 2]
const R_TOP    = [15, 17, 19, 21, 23, 25, 27]
const R_BOTTOM = [16, 18, 20, 22, 24, 26, 28]

// Grouped Actions menu for a single student — used by both the admin Students
// list (one instance per row) and the student's own detail page (one
// instance in the header). Each instance is fully self-contained: it owns
// its own open/submenu state and closes itself on any outside click, so
// opening one instance implicitly closes any other (no page-level
// coordination needed).
export default function StudentActionsMenu({ student, onMutated, onDeleted }) {
    const { t } = useTranslation()
    const containerRef = useRef(null)

    const [open, setOpen] = useState(false)
    const [submenu, setSubmenu] = useState(null) // null | 'seat' | 'billing' | 'account'

    const [changeSeatOpen, setChangeSeatOpen]             = useState(false)
    const [changeSeatGrid, setChangeSeatGrid]             = useState(null)
    const [changeSeatGridLoading, setChangeSeatGridLoading] = useState(false)
    const [newSeat, setNewSeat]                           = useState(null)
    const [changeSeatSubmitting, setChangeSeatSubmitting] = useState(false)

    const [exchangeSeatOpen, setExchangeSeatOpen]         = useState(false)
    const [exchangeSearch, setExchangeSearch]             = useState('')
    const [exchangeCandidates, setExchangeCandidates]     = useState([])
    const [exchangeLoading, setExchangeLoading]           = useState(false)
    const [exchangeTarget, setExchangeTarget]             = useState(null)
    const [exchangeSubmitting, setExchangeSubmitting]     = useState(false)

    const [deleteOpen, setDeleteOpen] = useState(false)
    const [deleting, setDeleting]     = useState(false)

    const [renewingSeat, setRenewingSeat]   = useState(false)
    const [releasingSeat, setReleasingSeat] = useState(false)
    const [clearingDues, setClearingDues]   = useState(false)
    const [clearingFees, setClearingFees]   = useState(false)

    const [clearDuesOpen, setClearDuesOpen]             = useState(false)
    const [clearDuesAmountInput, setClearDuesAmountInput] = useState('')
    const [clearDuesPaymentMode, setClearDuesPaymentMode] = useState('CASH')
    const [clearFeesOpen, setClearFeesOpen]             = useState(false)
    const [clearFeesAmountInput, setClearFeesAmountInput] = useState('')
    const [clearFeesPaymentMode, setClearFeesPaymentMode] = useState('CASH')

    const [changeStatusOpen, setChangeStatusOpen]       = useState(false)
    const [changeStatusTarget, setChangeStatusTarget]   = useState('PENDING')
    const [pendingAmountInput, setPendingAmountInput]   = useState('')
    const [changeStatusSubmitting, setChangeStatusSubmitting] = useState(false)

    const [msgOpen, setMsgOpen]       = useState(false)
    const [msgText, setMsgText]       = useState('')
    const [msgSending, setMsgSending] = useState(false)

    const [sendingReceipt, setSendingReceipt] = useState(false)
    const [sendingIdCard, setSendingIdCard]   = useState(false)
    const [sendingPoll, setSendingPoll]       = useState(false)

    useEffect(() => {
        const close = (e) => {
            if (containerRef.current && !containerRef.current.contains(e.target)) {
                setOpen(false)
                setSubmenu(null)
            }
        }
        document.addEventListener('click', close)
        return () => document.removeEventListener('click', close)
    }, [])

    const canRenew = student.membershipId && student.displayStatus === 'PAID'
    const canChangeSeat = student.membershipId && student.membershipStatus === 'ACTIVE'
    const canExchangeSeat = student.membershipId && student.membershipStatus === 'ACTIVE'
    const canReleaseSeat = student.membershipId && (student.membershipStatus === 'ACTIVE' || student.membershipStatus === 'GRACE')
    const canClearDues = student.membershipId && student.membershipStatus === 'GRACE'
    const canClearFees = student.pendingAmount > 0
    const canChangeStatus = student.membershipId && student.membershipStatus === 'ACTIVE'
    const canSendRenewalPoll = student.membershipId && student.membershipStatus === 'ACTIVE'
    const showSeatGroup = canRenew || canChangeSeat || canExchangeSeat || canReleaseSeat
    const showBillingGroup = canClearDues || canClearFees

    const closeMenu = () => { setOpen(false); setSubmenu(null) }

    const openExchangeSeat = () => {
        setExchangeSeatOpen(true)
        setExchangeSearch('')
        setExchangeTarget(null)
        setExchangeCandidates([])
    }

    // Debounced search — only while the dialog is open, and re-runs whenever
    // the search text changes (matches AdminStudentsPage's debounce pattern).
    useEffect(() => {
        if (!exchangeSeatOpen) return
        setExchangeLoading(true)
        const timeout = setTimeout(() => {
            api.get(`/admin/students?page=0&size=200&membershipStatus=ACTIVE${exchangeSearch ? `&search=${encodeURIComponent(exchangeSearch)}` : ''}`)
                .then(r => {
                    const list = r.data.data?.students || []
                    setExchangeCandidates(list.filter(s => s.id !== student.id && s.seatNumber))
                })
                .catch(() => toast.error('Failed to search students'))
                .finally(() => setExchangeLoading(false))
        }, 300)
        return () => clearTimeout(timeout)
    }, [exchangeSearch, exchangeSeatOpen])

    const handleExchangeSeat = async () => {
        if (!exchangeTarget) return
        setExchangeSubmitting(true)
        try {
            await api.post(`/admin/memberships/${student.membershipId}/swap-seat`, { otherUserId: exchangeTarget.id })
            toast.success(`Seat exchanged with ${exchangeTarget.name}`)
            setExchangeSeatOpen(false)
            onMutated()
        } catch (e) {
            toast.error(e.response?.data?.message || 'Failed to exchange seat')
        } finally {
            setExchangeSubmitting(false)
        }
    }

    const handleRenewSeat = async () => {
        if (!window.confirm(`Renew ${student.name}'s seat by one month?`)) return
        setRenewingSeat(true)
        try {
            await api.patch(`/admin/memberships/${student.membershipId}/renew`)
            toast.success(`Seat renewed for ${student.name}`)
            onMutated()
        } catch (e) {
            toast.error(e.response?.data?.message || 'Failed to renew seat')
        } finally {
            setRenewingSeat(false)
        }
    }

    const openChangeSeat = async () => {
        setChangeSeatOpen(true)
        setNewSeat(null)
        setChangeSeatGrid(null)
        setChangeSeatGridLoading(true)
        try {
            // Check availability as of today, not the membership's start date — if that
            // start date is in the past, checking it instead of today hides every seat
            // booking that began after it, making currently-occupied seats look free.
            // A membership that hasn't started yet still checks against its own start date.
            const today = new Date().toISOString().split('T')[0]
            const date = student.membershipStart && student.membershipStart > today ? student.membershipStart : today
            const res = await api.get(`/seats/availability?shift=${student.shift}&date=${date}`)
            setChangeSeatGrid(res.data.data)
        } catch {
            toast.error(t('adminStudents.toasts.seatChangeFailed'))
            setChangeSeatOpen(false)
        } finally {
            setChangeSeatGridLoading(false)
        }
    }

    const handleChangeSeat = async () => {
        if (!newSeat) return
        setChangeSeatSubmitting(true)
        try {
            await api.patch(`/admin/memberships/${student.membershipId}/seat`, { seatNumber: newSeat })
            toast.success(t('adminStudents.toasts.seatChanged', { seat: newSeat }))
            setChangeSeatOpen(false)
            onMutated()
        } catch {
            toast.error(t('adminStudents.toasts.seatChangeFailed'))
        } finally {
            setChangeSeatSubmitting(false)
        }
    }

    const handleReleaseSeat = async () => {
        const activeWarning = student.membershipStatus === 'ACTIVE'
            ? `${student.name}'s membership is currently ACTIVE and paid — releasing will immediately free their seat and mark them Released. `
            : `Release seat ${student.seatNumber} for ${student.name}? `
        if (!window.confirm(`${activeWarning}Dues of ${formatCurrency(student.duesAmount ?? 0)} remain on record. This cannot be undone.`)) return

        const notifyStudent = window.confirm(
            `Send ${student.name} a notification that their seat has expired due to non-payment and been released?\n\n` +
            `Click OK to notify them, or Cancel to release the seat quietly.`
        )

        setReleasingSeat(true)
        try {
            await api.patch(`/admin/memberships/${student.membershipId}/release`, { notifyStudent })
            toast.success(`Seat ${student.seatNumber} released${notifyStudent ? ' — student notified' : ''}`)
            onMutated()
        } catch (e) {
            toast.error(e.response?.data?.message || 'Failed to release seat')
        } finally {
            setReleasingSeat(false)
        }
    }

    const closeClearDues = () => {
        setClearDuesOpen(false)
        setClearDuesAmountInput('')
        setClearDuesPaymentMode('CASH')
    }

    const handleClearDues = async () => {
        const amount = Number(clearDuesAmountInput)
        const outstanding = Number(student.duesAmount ?? 0)
        if (!amount || amount <= 0 || amount > outstanding) {
            toast.error('Enter a valid amount up to the outstanding dues')
            return
        }
        setClearingDues(true)
        try {
            await api.patch(`/admin/memberships/${student.membershipId}/clear-dues`, { amountCleared: amount, paymentMode: clearDuesPaymentMode })
            toast.success(`₹${amount} cleared for ${student.name}`)
            closeClearDues()
            onMutated()
        } catch (e) {
            toast.error(e.response?.data?.message || 'Failed to clear dues')
        } finally {
            setClearingDues(false)
        }
    }

    const closeClearFees = () => {
        setClearFeesOpen(false)
        setClearFeesAmountInput('')
        setClearFeesPaymentMode('CASH')
    }

    const handleClearPendingFees = async () => {
        const amount = Number(clearFeesAmountInput)
        const outstanding = Number(student.pendingAmount ?? 0)
        if (!amount || amount <= 0 || amount > outstanding) {
            toast.error('Enter a valid amount up to the pending amount')
            return
        }
        setClearingFees(true)
        try {
            await api.patch(`/admin/students/${student.id}/clear-pending-fees`, { amountCleared: amount, paymentMode: clearFeesPaymentMode })
            toast.success(`₹${amount} cleared for ${student.name}`)
            closeClearFees()
            onMutated()
        } catch (e) {
            toast.error(e.response?.data?.message || 'Failed to clear pending fees')
        } finally {
            setClearingFees(false)
        }
    }

    const closeChangeStatus = () => {
        setChangeStatusOpen(false)
        setChangeStatusTarget('PENDING')
        setPendingAmountInput('')
    }

    const handleChangeStatus = async () => {
        if (changeStatusTarget === 'PENDING') {
            const amount = Number(pendingAmountInput)
            if (!amount || amount <= 0) {
                toast.error('Enter a valid pending amount')
                return
            }
            setChangeStatusSubmitting(true)
            try {
                await api.patch(`/admin/memberships/${student.membershipId}/mark-pending`, { pendingAmount: amount })
                toast.success(`${student.name} marked as Pending`)
                closeChangeStatus()
                onMutated()
            } catch (e) {
                toast.error(e.response?.data?.message || 'Failed to mark as Pending')
            } finally {
                setChangeStatusSubmitting(false)
            }
            return
        }

        // GRACE
        setChangeStatusSubmitting(true)
        try {
            await api.patch(`/admin/memberships/${student.membershipId}/mark-grace`)
            toast.success(`${student.name} marked as Grace`)
            closeChangeStatus()
            onMutated()
        } catch (e) {
            toast.error(e.response?.data?.message || 'Failed to mark as Grace')
        } finally {
            setChangeStatusSubmitting(false)
        }
    }

    const closeMessage = () => { setMsgOpen(false); setMsgText('') }

    const handleSendMessage = async () => {
        setMsgSending(true)
        try {
            await api.post(`/admin/students/${student.id}/message`, { message: msgText.trim() })
            toast.success('Message sent')
            closeMessage()
        } catch (e) {
            toast.error(e.response?.data?.message || 'Failed to send')
        } finally {
            setMsgSending(false)
        }
    }

    const handleSendReceipt = async () => {
        if (!window.confirm(`WhatsApp ${student.name}'s latest payment receipt to them?`)) return
        setSendingReceipt(true)
        try {
            await api.post(`/admin/students/${student.id}/send-receipt`)
            toast.success('Payment receipt sent')
        } catch (e) {
            toast.error(e.response?.data?.message || 'Failed to send receipt')
        } finally {
            setSendingReceipt(false)
        }
    }

    const handleSendIdCard = async () => {
        if (!window.confirm(`WhatsApp ${student.name}'s student ID card to them?`)) return
        setSendingIdCard(true)
        try {
            await api.post(`/admin/students/${student.id}/send-id-card`)
            toast.success('ID card sent')
        } catch (e) {
            toast.error(e.response?.data?.message || 'Failed to send ID card')
        } finally {
            setSendingIdCard(false)
        }
    }

    const handleSendRenewalPoll = async () => {
        if (!window.confirm(`Send ${student.name} a renewal poll (Yes/No) via WhatsApp?`)) return
        setSendingPoll(true)
        try {
            await api.post(`/admin/students/${student.id}/send-renewal-poll`)
            toast.success('Renewal poll sent')
        } catch (e) {
            toast.error(e.response?.data?.message || 'Failed to send renewal poll')
        } finally {
            setSendingPoll(false)
        }
    }

    const handleDeleteStudent = async () => {
        setDeleting(true)
        try {
            await api.delete(`/admin/students/${student.id}`)
            toast.success(`${student.name} deleted`)
            setDeleteOpen(false)
            onDeleted()
        } catch (e) {
            toast.error(e.response?.data?.message || 'Delete failed')
        } finally {
            setDeleting(false)
        }
    }

    return (
        <>
            <div className="relative" ref={containerRef} onClick={e => e.stopPropagation()}>
                <button
                    onClick={() => { setOpen(o => !o); setSubmenu(null) }}
                    className="text-xs px-3 py-1.5 rounded-lg bg-primary-700/50 text-primary-300 hover:text-white border border-primary-700/40 transition-all flex items-center gap-1">
                    Actions <span className="text-[10px]">▾</span>
                </button>
                {open && (
                    <div className="absolute right-0 mt-1 w-40 bg-primary-800 border border-primary-700/60 rounded-xl shadow-xl z-20 overflow-hidden">
                        {submenu === null && (
                            <>
                                {showSeatGroup && (
                                    <button
                                        onClick={() => setSubmenu('seat')}
                                        className="w-full flex items-center justify-between text-left text-xs px-3 py-2.5 text-primary-300 hover:bg-primary-700/60 transition-colors">
                                        Seat <span className="text-[10px]">▸</span>
                                    </button>
                                )}
                                {showBillingGroup && (
                                    <button
                                        onClick={() => setSubmenu('billing')}
                                        className="w-full flex items-center justify-between text-left text-xs px-3 py-2.5 text-primary-300 hover:bg-primary-700/60 transition-colors">
                                        Billing <span className="text-[10px]">▸</span>
                                    </button>
                                )}
                                <button
                                    onClick={() => setSubmenu('account')}
                                    className="w-full flex items-center justify-between text-left text-xs px-3 py-2.5 text-primary-300 hover:bg-primary-700/60 transition-colors">
                                    Account <span className="text-[10px]">▸</span>
                                </button>
                                <button
                                    onClick={() => { setDeleteOpen(true); closeMenu() }}
                                    className="w-full text-left text-xs px-3 py-2.5 text-red-400 hover:bg-primary-700/60 transition-colors border-t border-red-900/40 mt-1">
                                    Delete
                                </button>
                            </>
                        )}
                        {submenu === 'seat' && (
                            <>
                                <button
                                    onClick={() => setSubmenu(null)}
                                    className="w-full text-left text-xs px-3 py-2.5 text-primary-400 hover:bg-primary-700/60 transition-colors border-b border-primary-700/40">
                                    ‹ Back
                                </button>
                                {canRenew && (
                                    <button
                                        disabled={renewingSeat}
                                        onClick={() => { handleRenewSeat(); closeMenu() }}
                                        className="w-full text-left text-xs px-3 py-2.5 text-blue-400 hover:bg-primary-700/60 transition-colors disabled:opacity-50">
                                        {renewingSeat ? 'Renewing…' : 'Renew Seat'}
                                    </button>
                                )}
                                {canChangeSeat && (
                                    <button
                                        onClick={() => { openChangeSeat(); closeMenu() }}
                                        className="w-full text-left text-xs px-3 py-2.5 text-indigo-400 hover:bg-primary-700/60 transition-colors">
                                        {t('adminStudents.changeSeat')}
                                    </button>
                                )}
                                {canExchangeSeat && (
                                    <button
                                        onClick={() => { openExchangeSeat(); closeMenu() }}
                                        className="w-full text-left text-xs px-3 py-2.5 text-indigo-400 hover:bg-primary-700/60 transition-colors">
                                        Exchange Seat
                                    </button>
                                )}
                                {canReleaseSeat && (
                                    <button
                                        disabled={releasingSeat}
                                        onClick={() => { handleReleaseSeat(); closeMenu() }}
                                        className="w-full text-left text-xs px-3 py-2.5 text-red-400 hover:bg-primary-700/60 transition-colors disabled:opacity-50">
                                        {releasingSeat ? 'Releasing…' : 'Release Seat'}
                                    </button>
                                )}
                            </>
                        )}
                        {submenu === 'billing' && (
                            <>
                                <button
                                    onClick={() => setSubmenu(null)}
                                    className="w-full text-left text-xs px-3 py-2.5 text-primary-400 hover:bg-primary-700/60 transition-colors border-b border-primary-700/40">
                                    ‹ Back
                                </button>
                                {canClearDues && (
                                    <button
                                        disabled={clearingDues}
                                        onClick={() => { setClearDuesAmountInput(String(student.duesAmount ?? 0)); setClearDuesOpen(true); closeMenu() }}
                                        className="w-full text-left text-xs px-3 py-2.5 text-emerald-400 hover:bg-primary-700/60 transition-colors disabled:opacity-50">
                                        {clearingDues ? 'Clearing…' : 'Clear Dues'}
                                    </button>
                                )}
                                {canClearFees && (
                                    <button
                                        disabled={clearingFees}
                                        onClick={() => { setClearFeesAmountInput(String(student.pendingAmount ?? 0)); setClearFeesOpen(true); closeMenu() }}
                                        className="w-full text-left text-xs px-3 py-2.5 text-emerald-400 hover:bg-primary-700/60 transition-colors disabled:opacity-50">
                                        {clearingFees ? 'Clearing…' : 'Clear Pending Fees'}
                                    </button>
                                )}
                            </>
                        )}
                        {submenu === 'account' && (
                            <>
                                <button
                                    onClick={() => setSubmenu(null)}
                                    className="w-full text-left text-xs px-3 py-2.5 text-primary-400 hover:bg-primary-700/60 transition-colors border-b border-primary-700/40">
                                    ‹ Back
                                </button>
                                {canChangeStatus && (
                                    <button
                                        onClick={() => { setChangeStatusTarget('PENDING'); setPendingAmountInput(''); setChangeStatusOpen(true); closeMenu() }}
                                        className="w-full text-left text-xs px-3 py-2.5 text-yellow-400 hover:bg-primary-700/60 transition-colors">
                                        Change Status
                                    </button>
                                )}
                                <button
                                    onClick={() => { setMsgOpen(true); closeMenu() }}
                                    className="w-full text-left text-xs px-3 py-2.5 text-emerald-400 hover:bg-primary-700/60 transition-colors">
                                    Message
                                </button>
                                <button
                                    disabled={sendingReceipt}
                                    onClick={() => { handleSendReceipt(); closeMenu() }}
                                    className="w-full text-left text-xs px-3 py-2.5 text-emerald-400 hover:bg-primary-700/60 transition-colors disabled:opacity-50">
                                    {sendingReceipt ? 'Sending…' : 'Send Payment Receipt'}
                                </button>
                                <button
                                    disabled={sendingIdCard}
                                    onClick={() => { handleSendIdCard(); closeMenu() }}
                                    className="w-full text-left text-xs px-3 py-2.5 text-emerald-400 hover:bg-primary-700/60 transition-colors disabled:opacity-50">
                                    {sendingIdCard ? 'Sending…' : 'Send ID Card'}
                                </button>
                                {canSendRenewalPoll && (
                                    <button
                                        disabled={sendingPoll}
                                        onClick={() => { handleSendRenewalPoll(); closeMenu() }}
                                        className="w-full text-left text-xs px-3 py-2.5 text-emerald-400 hover:bg-primary-700/60 transition-colors disabled:opacity-50">
                                        {sendingPoll ? 'Sending…' : 'Send Renewal Poll'}
                                    </button>
                                )}
                            </>
                        )}
                    </div>
                )}
            </div>

            {/* Portaled to document.body: a `fixed inset-0` modal must always be
                viewport-relative, but this component can be mounted anywhere,
                including inside ancestors with backdrop-blur/transform (e.g. the
                `.card` wrapping the Students list table) — such ancestors create
                a new CSS containing block for `position: fixed`, silently
                re-scoping "cover the viewport" to "cover that ancestor's box"
                instead. Escaping via a portal sidesteps that regardless of
                where this component ends up nested. */}
            {createPortal(
                <>
                {changeSeatOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={() => setChangeSeatOpen(false)}>
                    <div className="card p-6 w-full max-w-2xl border-indigo-900/30 max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-4">
                            <div>
                                <h3 className="section-title">{t('adminStudents.changeSeatModal.title')}</h3>
                                <p className="text-primary-400 text-sm mt-1">
                                    {student.name} &mdash; {t('adminStudents.changeSeatModal.current')}: <span className="text-white font-mono">{student.seatNumber}</span>
                                </p>
                            </div>
                            <button onClick={() => setChangeSeatOpen(false)} className="text-primary-400 hover:text-white">✕</button>
                        </div>

                        {changeSeatGridLoading ? (
                            <div className="shimmer h-48 rounded-xl" />
                        ) : changeSeatGrid ? (
                            <div className="overflow-x-auto">
                                <div className="min-w-[640px]">
                                <div className="flex gap-2 mb-1">
                                    <div className="w-5 flex-shrink-0" />
                                    <div className="invisible pointer-events-none">
                                        <div className="flex gap-1">{L_TOP.map(n => <div key={n} className="w-8 h-0" />)}</div>
                                    </div>
                                    <div className="w-6 flex-shrink-0 flex justify-center">
                                        <span className="text-primary-400 text-[10px] tracking-widest uppercase">ENTRY</span>
                                    </div>
                                </div>
                                <div className="space-y-7">
                                    {ROWS.map(row => {
                                        const rowSeats = changeSeatGrid.seatsByRow?.[row] || []
                                        const find = (sn) => rowSeats.find(s => s.seatNumber === sn)
                                        const renderSeat = (n) => {
                                            const sn = `${row}${n}`
                                            if (INACTIVE_SEATS.has(sn)) {
                                                return <div key={sn} className="w-8 h-8 rounded-lg bg-primary-900/50 border border-primary-800/20" title="Blocked" />
                                            }
                                            const s = find(sn)
                                            if (!s) return <div key={sn} className="w-8 h-8 rounded-lg bg-primary-900/40 border border-primary-800/20" />
                                            const isCurrent  = s.seatNumber === student.seatNumber
                                            const isSelected = newSeat === s.seatNumber
                                            return (
                                                <button key={sn}
                                                    disabled={s.isBooked && !isCurrent}
                                                    onClick={() => !isCurrent && setNewSeat(s.seatNumber)}
                                                    title={isCurrent ? `${sn} (current)` : s.isBooked ? `${sn} (booked)` : sn}
                                                    className={`w-8 h-8 rounded-lg text-xs font-medium border transition-all
                                                        ${isCurrent
                                                            ? 'bg-indigo-500/30 border-indigo-400/60 text-indigo-300 cursor-default'
                                                            : isSelected
                                                                ? 'bg-amber-400/30 border-amber-400/70 text-amber-300'
                                                                : s.isBooked
                                                                    ? 'bg-red-500/30 border-red-500/50 text-red-400 cursor-not-allowed opacity-60'
                                                                    : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/25 cursor-pointer'}`}>
                                                    {sn.substring(1)}
                                                </button>
                                            )
                                        }
                                        return (
                                            <div key={row} className="flex gap-2">
                                                <span className="text-primary-400 font-mono text-sm w-5 text-center self-start pt-2">{row}</span>
                                                <div>
                                                    <div className="flex gap-1">{L_TOP.map(renderSeat)}</div>
                                                    <div className="border-b border-primary-700/40 my-1" />
                                                    <div className="flex gap-1">{L_BOTTOM.map(renderSeat)}</div>
                                                </div>
                                                <div className="w-6 flex-shrink-0 relative">
                                                    <div className="absolute inset-y-0 left-1/2 w-px bg-primary-700/30 -translate-x-1/2" />
                                                </div>
                                                <div>
                                                    <div className="flex gap-1">{R_TOP.map(renderSeat)}</div>
                                                    <div className="border-b border-primary-700/40 my-1" />
                                                    <div className="flex gap-1">{R_BOTTOM.map(renderSeat)}</div>
                                                </div>
                                            </div>
                                        )
                                    })}
                                </div>
                                <div className="flex gap-2 mt-3 text-[10px] tracking-widest uppercase text-primary-600">
                                    <div className="w-5 flex-shrink-0" />
                                    <div className="flex gap-1">
                                        <div className="px-2 py-1 rounded border border-primary-800/30 bg-primary-900/40">EXIT</div>
                                        <div className="px-2 py-1 rounded border border-primary-800/30 bg-primary-900/40">RO / PANTRY</div>
                                        <div className="px-2 py-1 rounded border border-primary-800/30 bg-primary-900/40">WASHROOM</div>
                                    </div>
                                </div>
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
                            <button onClick={() => setChangeSeatOpen(false)}
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

            {exchangeSeatOpen && (
                <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4" onClick={() => setExchangeSeatOpen(false)}>
                    <div className="card p-6 max-w-sm w-full max-h-[85vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
                        <h3 className="text-lg font-semibold text-white mb-1">Exchange Seat</h3>
                        <p className="text-sm text-primary-400 mb-4">
                            Swap physical seats between <span className="text-white">{student.name}</span> (seat {student.seatNumber}) and
                            another active student — plans and shifts stay unchanged.
                        </p>

                        {exchangeTarget ? (
                            <div className="flex items-center justify-between bg-amber-500/10 border border-amber-500/30 rounded-xl p-3 mb-4">
                                <div>
                                    <p className="text-sm text-white font-medium">{exchangeTarget.name}</p>
                                    <p className="text-xs text-primary-400">Seat {exchangeTarget.seatNumber} · {exchangeTarget.mobile}</p>
                                </div>
                                <button onClick={() => setExchangeTarget(null)} className="text-xs text-amber-400 hover:text-amber-300">
                                    Change
                                </button>
                            </div>
                        ) : (
                            <>
                                <input
                                    type="text"
                                    autoFocus
                                    value={exchangeSearch}
                                    onChange={e => setExchangeSearch(e.target.value)}
                                    placeholder="Search name or mobile…"
                                    className="input w-full text-sm mb-3"
                                />
                                <div className="max-h-64 overflow-y-auto space-y-2">
                                    {exchangeLoading ? (
                                        <div className="shimmer h-12 rounded-xl" />
                                    ) : exchangeCandidates.length === 0 ? (
                                        <p className="text-xs text-primary-500 py-2">No active seated students found</p>
                                    ) : exchangeCandidates.map(s => (
                                        <button
                                            key={s.id}
                                            onClick={() => setExchangeTarget(s)}
                                            className="w-full flex items-center justify-between text-left border border-primary-700/40 rounded-xl px-3 py-2 hover:border-amber-500/40 hover:bg-primary-700/30 transition-colors">
                                            <div>
                                                <p className="text-sm text-white">{s.name}</p>
                                                <p className="text-xs text-primary-400">{s.mobile}</p>
                                            </div>
                                            <span className="text-xs text-amber-400">Seat {s.seatNumber}</span>
                                        </button>
                                    ))}
                                </div>
                            </>
                        )}

                        <div className="flex gap-3 justify-end mt-5 pt-4 border-t border-primary-700/30">
                            <button onClick={() => setExchangeSeatOpen(false)} className="btn-outline text-sm px-4 py-2">
                                Cancel
                            </button>
                            <button
                                disabled={!exchangeTarget || exchangeSubmitting}
                                onClick={handleExchangeSeat}
                                className="btn-primary px-5 py-2 text-sm disabled:opacity-40 disabled:cursor-not-allowed">
                                {exchangeSubmitting ? 'Exchanging…' : 'Exchange'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {deleteOpen && (
                <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
                    <div className="card p-6 max-w-sm w-full">
                        <h3 className="text-lg font-semibold text-white mb-2">Delete Student</h3>
                        <p className="text-sm text-gray-400 mb-6">
                            Permanently delete <span className="text-white font-medium">{student.name}</span> and all their memberships, payments, and seat bookings? This cannot be undone.
                        </p>
                        <div className="flex gap-3 justify-end">
                            <button onClick={() => setDeleteOpen(false)} className="btn-outline text-sm px-4 py-2">
                                Cancel
                            </button>
                            <button
                                onClick={handleDeleteStudent}
                                disabled={deleting}
                                className="text-sm px-4 py-2 rounded-lg bg-red-600 hover:bg-red-700 text-white disabled:opacity-50 transition-colors">
                                {deleting ? 'Deleting…' : 'Delete'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
            {msgOpen && (
                <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4" onClick={closeMessage}>
                    <div className="card p-6 max-w-sm w-full" onClick={e => e.stopPropagation()}>
                        <h3 className="text-lg font-semibold text-white mb-1">Send Message</h3>
                        <p className="text-sm text-primary-400 mb-4">
                            WhatsApp → <span className="text-white">{student.name}</span> ({student.mobile})
                        </p>
                        <textarea
                            rows={4}
                            value={msgText}
                            onChange={e => setMsgText(e.target.value)}
                            placeholder="Type a WhatsApp message…"
                            className="input w-full text-sm resize-none mb-3"
                        />
                        <div className="flex gap-3 justify-end">
                            <button onClick={closeMessage} className="btn-outline text-sm px-4 py-2">
                                Cancel
                            </button>
                            <button
                                disabled={msgSending || msgText.trim().length < 5}
                                onClick={handleSendMessage}
                                className="text-sm px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white disabled:opacity-40 transition-colors">
                                {msgSending ? 'Sending…' : 'Send via WhatsApp'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
            {changeStatusOpen && (
                <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4" onClick={closeChangeStatus}>
                    <div className="card p-6 max-w-sm w-full" onClick={e => e.stopPropagation()}>
                        <h3 className="text-lg font-semibold text-white mb-1">Change Status</h3>
                        <p className="text-sm text-primary-400 mb-4">
                            Correct <span className="text-white">{student.name}</span>'s wrongly-marked-Paid membership. This deletes their last payment record and cannot be undone.
                        </p>

                        <div className="flex gap-2 mb-4">
                            <button
                                onClick={() => setChangeStatusTarget('PENDING')}
                                className={`flex-1 text-sm px-3 py-2 rounded-lg border transition-all ${changeStatusTarget === 'PENDING' ? 'bg-yellow-500/20 border-yellow-400/60 text-yellow-400' : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                                Pending
                            </button>
                            <button
                                onClick={() => setChangeStatusTarget('GRACE')}
                                className={`flex-1 text-sm px-3 py-2 rounded-lg border transition-all ${changeStatusTarget === 'GRACE' ? 'bg-orange-500/20 border-orange-400/60 text-orange-400' : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                                Grace
                            </button>
                        </div>

                        {changeStatusTarget === 'PENDING' ? (
                            <>
                                <label className="label">Pending Amount (₹)</label>
                                <input
                                    type="number" min="0" step="1"
                                    autoFocus
                                    value={pendingAmountInput}
                                    onChange={e => setPendingAmountInput(e.target.value)}
                                    placeholder="e.g. 200"
                                    className="input w-full text-sm mb-3"
                                />
                            </>
                        ) : (
                            <p className="text-sm text-primary-400 mb-3">
                                Sets dues to the full plan price and resets their expiry date to today, so days-overdue counts from now.
                            </p>
                        )}

                        <div className="flex gap-3 justify-end">
                            <button onClick={closeChangeStatus} className="btn-outline text-sm px-4 py-2">
                                Cancel
                            </button>
                            <button
                                disabled={changeStatusSubmitting || (changeStatusTarget === 'PENDING' && (!pendingAmountInput || Number(pendingAmountInput) <= 0))}
                                onClick={handleChangeStatus}
                                className={`text-sm px-4 py-2 rounded-lg text-white disabled:opacity-40 transition-colors ${changeStatusTarget === 'PENDING' ? 'bg-yellow-600 hover:bg-yellow-700' : 'bg-orange-600 hover:bg-orange-700'}`}>
                                {changeStatusSubmitting ? 'Saving…' : changeStatusTarget === 'PENDING' ? 'Mark Pending' : 'Mark Grace'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
            {clearDuesOpen && (
                <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4" onClick={closeClearDues}>
                    <div className="card p-6 max-w-sm w-full" onClick={e => e.stopPropagation()}>
                        <h3 className="text-lg font-semibold text-white mb-1">Clear Dues</h3>
                        <p className="text-sm text-primary-400 mb-4">
                            Record a payment for <span className="text-white">{student.name}</span>, reactivate them as Paid,
                            and extend their membership by one month from where it left off. Any amount not cleared here stays
                            on record as a pending balance.
                        </p>

                        <label className="label">Amount to Clear (₹) — outstanding: {formatCurrency(student.duesAmount ?? 0)}</label>
                        <input
                            type="number" min="0" step="1" max={student.duesAmount ?? 0}
                            autoFocus
                            value={clearDuesAmountInput}
                            onChange={e => setClearDuesAmountInput(e.target.value)}
                            className="input w-full text-sm mb-1"
                        />
                        {Number(clearDuesAmountInput) > 0 && Number(clearDuesAmountInput) < Number(student.duesAmount ?? 0) && (
                            <p className="text-xs text-amber-400 mb-3">
                                ₹{formatNumber(Number(student.duesAmount ?? 0) - Number(clearDuesAmountInput))} will remain as a pending amount.
                            </p>
                        )}

                        <label className="label">Payment Mode</label>
                        <div className="flex gap-2 mb-3">
                            {['CASH', 'UPI-QR'].map(mode => {
                                const info = paymentModeInfo(mode, t)
                                const active = clearDuesPaymentMode === mode
                                return (
                                    <button
                                        key={mode}
                                        type="button"
                                        onClick={() => setClearDuesPaymentMode(mode)}
                                        className={`px-3 py-1 rounded-full border text-xs font-medium transition-colors ${active ? info.className : 'bg-primary-800 border-primary-600 text-primary-400'}`}>
                                        {info.emoji} {info.label}
                                    </button>
                                )
                            })}
                        </div>

                        <div className="flex gap-3 justify-end mt-3">
                            <button onClick={closeClearDues} className="btn-outline text-sm px-4 py-2">
                                Cancel
                            </button>
                            <button
                                disabled={clearingDues
                                    || !clearDuesAmountInput
                                    || Number(clearDuesAmountInput) <= 0
                                    || Number(clearDuesAmountInput) > Number(student.duesAmount ?? 0)}
                                onClick={handleClearDues}
                                className="text-sm px-4 py-2 rounded-lg text-white disabled:opacity-40 transition-colors bg-emerald-600 hover:bg-emerald-700">
                                {clearingDues ? 'Clearing…' : 'Clear Dues'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
            {clearFeesOpen && (
                <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4" onClick={closeClearFees}>
                    <div className="card p-6 max-w-sm w-full" onClick={e => e.stopPropagation()}>
                        <h3 className="text-lg font-semibold text-white mb-1">Clear Pending Fees</h3>
                        <p className="text-sm text-primary-400 mb-4">
                            Record a payment for <span className="text-white">{student.name}</span>'s pending cash balance.
                            Any amount not cleared here stays on record as a pending balance.
                        </p>

                        <label className="label">Amount to Clear (₹) — outstanding: {formatCurrency(student.pendingAmount ?? 0)}</label>
                        <input
                            type="number" min="0" step="1" max={student.pendingAmount ?? 0}
                            autoFocus
                            value={clearFeesAmountInput}
                            onChange={e => setClearFeesAmountInput(e.target.value)}
                            className="input w-full text-sm mb-1"
                        />
                        {Number(clearFeesAmountInput) > 0 && Number(clearFeesAmountInput) < Number(student.pendingAmount ?? 0) && (
                            <p className="text-xs text-amber-400 mb-3">
                                ₹{formatNumber(Number(student.pendingAmount ?? 0) - Number(clearFeesAmountInput))} will remain as a pending amount.
                            </p>
                        )}

                        <label className="label">Payment Mode</label>
                        <div className="flex gap-2 mb-3">
                            {['CASH', 'UPI-QR'].map(mode => {
                                const info = paymentModeInfo(mode, t)
                                const active = clearFeesPaymentMode === mode
                                return (
                                    <button
                                        key={mode}
                                        type="button"
                                        onClick={() => setClearFeesPaymentMode(mode)}
                                        className={`px-3 py-1 rounded-full border text-xs font-medium transition-colors ${active ? info.className : 'bg-primary-800 border-primary-600 text-primary-400'}`}>
                                        {info.emoji} {info.label}
                                    </button>
                                )
                            })}
                        </div>

                        <div className="flex gap-3 justify-end mt-3">
                            <button onClick={closeClearFees} className="btn-outline text-sm px-4 py-2">
                                Cancel
                            </button>
                            <button
                                disabled={clearingFees
                                    || !clearFeesAmountInput
                                    || Number(clearFeesAmountInput) <= 0
                                    || Number(clearFeesAmountInput) > Number(student.pendingAmount ?? 0)}
                                onClick={handleClearPendingFees}
                                className="text-sm px-4 py-2 rounded-lg text-white disabled:opacity-40 transition-colors bg-emerald-600 hover:bg-emerald-700">
                                {clearingFees ? 'Clearing…' : 'Clear Pending Fees'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
                </>,
                document.body
            )}
        </>
    )
}
