import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { DatePicker } from '@mui/x-date-pickers/DatePicker'
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFnsV3'
import { parseISO, format } from 'date-fns'
import { formatCurrency } from '../../utils/currency'

const DATE_PICKER_SX = {
    '& .MuiOutlinedInput-root': {
        backgroundColor: 'rgba(13,27,75,0.6)', borderRadius: '12px', color: '#f0f4ff',
        fontFamily: '"DM Sans", system-ui, sans-serif', fontSize: '0.875rem',
        '& fieldset': { borderColor: 'rgba(32,53,163,0.4)' },
        '&:hover fieldset': { borderColor: 'rgba(251,191,36,0.4)' },
        '&.Mui-focused fieldset': { borderColor: 'rgba(245,158,11,0.6)', boxShadow: '0 0 0 2px rgba(245,158,11,0.1)' },
    },
    '& .MuiInputAdornment-root .MuiIconButton-root': { color: '#6080f0', '&:hover': { color: '#fbbf24' } },
    '& .MuiInputBase-input': { padding: '12px 16px', color: '#f0f4ff' },
}

const DATE_PICKER_POPPER_SX = {
    '& .MuiPaper-root': { backgroundColor: '#1c2e84', border: '1px solid rgba(32,53,163,0.3)', borderRadius: '12px', color: '#f0f4ff' },
    '& .MuiPickersDay-root': {
        color: '#8aa6f8', backgroundColor: 'transparent',
        '&:hover': { backgroundColor: 'rgba(245,158,11,0.15)' },
        '&.Mui-selected': { backgroundColor: '#f59e0b', color: '#1a2a68', '&:hover': { backgroundColor: '#fbbf24' } },
    },
    '& .MuiPickersCalendarHeader-root': { color: '#f0f4ff' },
    '& .MuiPickersArrowSwitcher-button': { color: '#6080f0', '&:hover': { color: '#fbbf24' } },
    '& .MuiDayCalendar-weekDayLabel': { color: '#6080f0' },
    '& .MuiPickersYear-yearButton.Mui-selected': { backgroundColor: '#f59e0b', color: '#1a2a68' },
}

const STATUS_BADGE_CLASSES = {
    NEW:      'bg-blue-500/20 text-blue-400 border-blue-500/30',
    PAID:     'bg-emerald-500/20 text-emerald-400 border-emerald-500/30',
    PENDING:  'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
    GRACE:         'bg-orange-500/20 text-orange-400 border-orange-500/30',
    GRACE_OVERDUE: 'bg-red-500/20 text-red-400 border-red-500/30',
    RELEASED:      'bg-red-950/70 text-red-300 border-red-900',
}

const ROWS = ['A', 'B', 'C', 'D']
const INACTIVE_SEATS = new Set(['B8', 'B18'])
const L_TOP    = [13, 11, 9, 7, 5, 3, 1]
const L_BOTTOM = [14, 12, 10, 8, 6, 4, 2]
const R_TOP    = [15, 17, 19, 21, 23, 25, 27]
const R_BOTTOM = [16, 18, 20, 22, 24, 26, 28]

// A 180° rotation reverses the row order (D at the physical top instead of
// A) and, within each row, reverses the left/right blocks *and* flips which
// sub-line (top/bottom) reads first — i.e. each line becomes the horizontal
// reverse of the line diagonally opposite it. Seat numbers/labels never
// change, only the position they're rendered in.
const ROWS_ROTATED     = [...ROWS].reverse()
const L_TOP_ROTATED    = [...R_BOTTOM].reverse()
const R_TOP_ROTATED    = [...L_BOTTOM].reverse()
const L_BOTTOM_ROTATED = [...R_TOP].reverse()
const R_BOTTOM_ROTATED = [...L_TOP].reverse()

const daysToExpiry = (membershipEnd, today) => {
    if (!membershipEnd) return null
    // Negative = overdue (membership in GRACE, seat held but past its endDate).
    return Math.ceil((new Date(membershipEnd) - new Date(today)) / 86400000)
}

const expiryClasses = (days) => {
    if (days < 0)   return 'bg-red-950/90 border-red-800 text-red-400 hover:bg-red-950'
    if (days <= 3)  return 'bg-red-500/60 border-red-400/80 text-red-100 hover:bg-red-500/80'
    if (days <= 7)  return 'bg-orange-500/50 border-orange-400/70 text-orange-100 hover:bg-orange-500/70'
    if (days <= 15) return 'bg-yellow-500/40 border-yellow-400/60 text-yellow-100 hover:bg-yellow-500/60'
    return 'bg-emerald-500/30 border-emerald-400/50 text-emerald-100 hover:bg-emerald-500/50'
}

export default function AdminSeatsPage() {
    const [seatMap, setSeatMap]     = useState(null)
    const [loading, setLoading]     = useState(true)
    const [shift, setShift]         = useState('FULL_DAY')
    const [date, setDate]           = useState(new Date().toISOString().split('T')[0])
    const [selected, setSelected]   = useState(null)
    const [viewMode, setViewMode]   = useState('default') // 'default' | 'expiry'
    const [rotated, setRotated]     = useState(false)
    const [historyOpen, setHistoryOpen]       = useState(false)
    const [seatHistory, setSeatHistory]       = useState([])
    const [historyLoading, setHistoryLoading] = useState(false)
    const { t } = useTranslation()

    const fetchMap = async () => {
        setLoading(true)
        try { const res = await api.get(`/admin/seats/map?shift=${shift}&date=${date}`); setSeatMap(res.data.data) }
        catch { toast.error(t('adminSeats.loadFailed')) }
        finally { setLoading(false) }
    }

    useEffect(() => { fetchMap() }, [shift, date])

    // Seat History always starts collapsed — resets whenever the modal is
    // closed or a different seat is opened, so the next expand re-fetches
    // fresh data rather than showing the previous seat's history.
    useEffect(() => {
        setHistoryOpen(false)
        setSeatHistory([])
    }, [selected?.seatNumber])

    const toggleSeatHistory = () => {
        const opening = !historyOpen
        setHistoryOpen(opening)
        if (opening && seatHistory.length === 0 && !historyLoading) {
            setHistoryLoading(true)
            api.get(`/admin/seats/${selected.seatNumber}/history`)
                .then(r => setSeatHistory(r.data.data || []))
                .catch(() => setSeatHistory([]))
                .finally(() => setHistoryLoading(false))
        }
    }

    const occupied = seatMap?.occupiedSeats ?? 0
    const total    = seatMap?.totalSeats ?? 110
    const pct      = Math.round((occupied / total) * 100)

    const shiftLabel = (s) => {
        if (s === 'MORNING')  return t('adminSeats.shifts.MORNING')
        if (s === 'EVENING')  return t('adminSeats.shifts.EVENING')
        return t('adminSeats.shifts.FULL_DAY')
    }

    return (
        <div>
            <div className="mb-6">
                <h1 className="page-header">{t('adminSeats.title')}</h1>
                <p className="text-primary-400">{t('adminSeats.subtitle')}</p>
            </div>

            <div className="flex flex-wrap gap-3 mb-6">
                {['MORNING','EVENING','FULL_DAY'].map(s => (
                    <button key={s} onClick={() => setShift(s)}
                            className={`px-4 py-2 rounded-xl text-sm font-medium border transition-all
              ${shift === s ? 'bg-red-500/20 border-red-400/60 text-red-400' : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                        {s === 'MORNING' ? `🌅 ${t('adminSeats.shifts.MORNING')}` : s === 'EVENING' ? `🌆 ${t('adminSeats.shifts.EVENING')}` : `🌟 ${t('adminSeats.shifts.FULL_DAY')}`}
                    </button>
                ))}
                <LocalizationProvider dateAdapter={AdapterDateFns}>
                    <DatePicker
                        value={date ? parseISO(date) : null}
                        onChange={(d) => setDate(d ? format(d, 'yyyy-MM-dd') : date)}
                        sx={{ width: 180, ...DATE_PICKER_SX }}
                        slotProps={{ textField: { size: 'small' }, popper: { sx: DATE_PICKER_POPPER_SX } }}
                    />
                </LocalizationProvider>
                <button onClick={fetchMap} className="px-4 py-2 rounded-xl text-sm bg-primary-700/50 text-primary-300 hover:text-white border border-primary-700/40 transition-all">↻ {t('adminSeats.refresh')}</button>
                <button onClick={() => setViewMode(v => v === 'expiry' ? 'default' : 'expiry')}
                        className={`px-4 py-2 rounded-xl text-sm font-medium border transition-all
                            ${viewMode === 'expiry'
                                ? 'bg-amber-500/20 border-amber-400/60 text-amber-400'
                                : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                    📅 {t('adminSeats.expiryView')}
                </button>
                <button onClick={() => setRotated(r => !r)}
                        className={`px-4 py-2 rounded-xl text-sm font-medium border transition-all
                            ${rotated
                                ? 'bg-amber-500/20 border-amber-400/60 text-amber-400'
                                : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                    ⟳ {t('adminSeats.rotate')}
                </button>
            </div>

            <div className="grid grid-cols-3 gap-4 mb-6">
                {[
                    { l: t('adminSeats.stats.total'),     v: total,            color: 'text-white' },
                    { l: t('adminSeats.stats.occupied'),  v: occupied,         color: 'text-red-400' },
                    { l: t('adminSeats.stats.available'), v: total - occupied, color: 'text-emerald-400' },
                ].map(({ l, v, color }) => (
                    <div key={l} className="card p-4 text-center">
                        <p className={`text-2xl font-bold ${color}`}>{v}</p>
                        <p className="text-primary-400 text-sm">{l}</p>
                    </div>
                ))}
            </div>

            <div className="card p-4 mb-6">
                <div className="flex justify-between text-sm mb-2">
                    <span className="text-primary-400">{t('adminSeats.occupancy')}</span>
                    <span className="text-white font-semibold">{pct}%</span>
                </div>
                <div className="h-3 bg-primary-800 rounded-full overflow-hidden">
                    <div className="h-full bg-gradient-to-r from-emerald-500 to-red-500 rounded-full transition-all duration-500" style={{ width: `${pct}%` }} />
                </div>
            </div>

            {loading ? (
                <div className="card p-8"><div className="shimmer w-full h-64 rounded-xl" /></div>
            ) : seatMap ? (
                <div className="card p-6 overflow-x-auto">
                    <div className="min-w-[640px]">
                    <div className="flex gap-2 mb-1">
                        <div className="w-5 flex-shrink-0" />
                        <div className="invisible pointer-events-none">
                            <div className="flex gap-1">{L_TOP.map(n => <div key={n} className="w-8 h-0" />)}</div>
                        </div>
                        {rotated ? (
                            <div className="flex-shrink-0 flex gap-1">
                                <div className="px-2 py-1 rounded border border-primary-800/30 bg-primary-900/40 text-[10px] tracking-widest uppercase text-primary-600">EXIT</div>
                                <div className="px-2 py-1 rounded border border-primary-800/30 bg-primary-900/40 text-[10px] tracking-widest uppercase text-primary-600">RO / PANTRY</div>
                                <div className="px-2 py-1 rounded border border-primary-800/30 bg-primary-900/40 text-[10px] tracking-widest uppercase text-primary-600">WASHROOM</div>
                            </div>
                        ) : (
                            <div className="w-6 flex-shrink-0 flex justify-center">
                                <span className="text-primary-400 text-[10px] tracking-widest uppercase">ENTRY</span>
                            </div>
                        )}
                    </div>
                    <div className="space-y-7">
                        {(rotated ? ROWS_ROTATED : ROWS).map(row => {
                            const rowSeats = seatMap.seatsByRow?.[row] || []
                            const find = (sn) => rowSeats.find(s => s.seatNumber === sn)
                            const renderSeat = (n) => {
                                const sn = `${row}${n}`
                                if (INACTIVE_SEATS.has(sn)) {
                                    return <div key={sn} className="w-8 h-8 rounded-lg bg-primary-900/50 border border-primary-800/20" title="Blocked" />
                                }
                                const seat = find(sn) ?? { seatNumber: sn, isOccupied: false }
                                const isFullDayOccupant = shift !== 'FULL_DAY' && seat.isOccupied && seat.shift === 'FULL_DAY'
                                const isOtherShiftOccupied = shift !== 'FULL_DAY' && !seat.isOccupied && seat.otherShiftOccupied
                                const otherShift = shift === 'MORNING' ? 'EVENING' : 'MORNING'

                                if (viewMode === 'expiry' && seat.isOccupied) {
                                    const days = daysToExpiry(seat.membershipEnd, date)
                                    const title = days < 0
                                        ? `${sn} — ${seat.studentName} — ${Math.abs(days)}d overdue (grace, seat held)`
                                        : `${sn} — ${seat.studentName} — ${days}d left`
                                    return (
                                        <button key={sn}
                                                onClick={() => setSelected(seat)}
                                                title={title}
                                                className={`w-8 h-8 rounded-lg text-xs font-bold border transition-all cursor-pointer flex items-center justify-center ${expiryClasses(days)}`}>
                                            <span className="w-5 h-5 rounded-full border border-current flex items-center justify-center leading-none">{days}</span>
                                        </button>
                                    )
                                }

                                return (
                                    <div key={sn} className="relative">
                                        <button onClick={() => setSelected(seat.isOccupied ? seat : null)}
                                                title={seat.isOccupied
                                                    ? `${seat.studentName} — ${shiftLabel(seat.shift)}`
                                                    : isOtherShiftOccupied
                                                        ? `${sn} (Available — booked for ${shiftLabel(otherShift)})`
                                                        : `${sn} (${t('adminSeats.legend.available')})`}
                                                className={`w-8 h-8 rounded-lg text-xs font-medium border transition-all
                                                    ${seat.isOccupied
                                                        ? seat.studentGender === 'Female'
                                                            ? 'bg-fuchsia-500/30 border-fuchsia-500/50 text-fuchsia-300 hover:bg-fuchsia-500/50 cursor-pointer'
                                                            : 'bg-red-500/30 border-red-500/50 text-red-300 hover:bg-red-500/50 cursor-pointer'
                                                        : 'bg-emerald-500/10 border-emerald-500/20 text-emerald-600 cursor-default'}`}>
                                            {viewMode === 'expiry' ? '' : isFullDayOccupant ? '' : sn.substring(1)}
                                        </button>
                                        {isFullDayOccupant && (
                                            <svg className={`absolute inset-0 w-8 h-8 pointer-events-none ${seat.studentGender === 'Female' ? 'text-fuchsia-500/50' : 'text-red-500/50'}`} viewBox="0 0 32 32">
                                                <line x1="3" y1="3" x2="29" y2="29" stroke="currentColor" strokeWidth="1" />
                                                <line x1="29" y1="3" x2="3" y2="29" stroke="currentColor" strokeWidth="1" />
                                            </svg>
                                        )}
                                        {isOtherShiftOccupied && (
                                            <span className="absolute top-0.5 left-0.5 w-1.5 h-1.5 rounded-full bg-emerald-400" />
                                        )}
                                    </div>
                                )
                            }
                            const lTop    = rotated ? L_TOP_ROTATED    : L_TOP
                            const lBottom = rotated ? L_BOTTOM_ROTATED : L_BOTTOM
                            const rTop    = rotated ? R_TOP_ROTATED    : R_TOP
                            const rBottom = rotated ? R_BOTTOM_ROTATED : R_BOTTOM
                            return (
                                <div key={row} className="flex gap-2">
                                    <span className="text-primary-400 font-mono text-sm w-5 text-center self-start pt-2">{row}</span>
                                    <div>
                                        <div className="flex gap-1">{lTop.map(renderSeat)}</div>
                                        <div className="border-b border-primary-700/40 my-1" />
                                        <div className="flex gap-1">{lBottom.map(renderSeat)}</div>
                                    </div>
                                    <div className="w-6 flex-shrink-0 relative">
                                        <div className="absolute inset-y-0 left-1/2 w-px bg-primary-700/30 -translate-x-1/2" />
                                    </div>
                                    <div>
                                        <div className="flex gap-1">{rTop.map(renderSeat)}</div>
                                        <div className="border-b border-primary-700/40 my-1" />
                                        <div className="flex gap-1">{rBottom.map(renderSeat)}</div>
                                    </div>
                                </div>
                            )
                        })}
                    </div>
                    <div className="flex gap-2 mt-3 text-[10px] tracking-widest uppercase text-primary-600">
                        <div className="w-5 flex-shrink-0" />
                        {rotated ? (
                            <div className="w-6 flex-shrink-0 flex justify-center">
                                <span className="text-primary-400">ENTRY</span>
                            </div>
                        ) : (
                            <div className="flex gap-1">
                                <div className="px-2 py-1 rounded border border-primary-800/30 bg-primary-900/40">EXIT</div>
                                <div className="px-2 py-1 rounded border border-primary-800/30 bg-primary-900/40">RO / PANTRY</div>
                                <div className="px-2 py-1 rounded border border-primary-800/30 bg-primary-900/40">WASHROOM</div>
                            </div>
                        )}
                    </div>
                    </div>

                    {viewMode === 'expiry' ? (
                        <div className="flex flex-wrap gap-6 mt-6 text-xs text-primary-400">
                            <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-red-950/90 border border-red-800" />Overdue (grace, seat held)</div>
                            <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-red-500/60 border border-red-400/80" />{t('adminSeats.legend.expiry.critical')}</div>
                            <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-orange-500/50 border border-orange-400/70" />{t('adminSeats.legend.expiry.warning')}</div>
                            <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-yellow-500/40 border border-yellow-400/60" />{t('adminSeats.legend.expiry.soon')}</div>
                            <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-emerald-500/30 border border-emerald-400/50" />{t('adminSeats.legend.expiry.safe')}</div>
                            <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-emerald-500/10 border border-emerald-500/20" />{t('adminSeats.legend.available')}</div>
                        </div>
                    ) : (
                        <div className="flex flex-wrap gap-6 mt-6 text-xs text-primary-400">
                            <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-emerald-500/10 border border-emerald-500/20" />{t('adminSeats.legend.available')}</div>
                            <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-red-500/30 border border-red-500/50" />Male occupied</div>
                            <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-fuchsia-500/30 border border-fuchsia-500/50" />Female occupied</div>
                            {shift !== 'FULL_DAY' && (
                                <>
                                    <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-primary-900/40 border border-primary-700/40 flex items-center justify-center text-[10px] leading-none text-primary-300">✕</div>Full-day booking</div>
                                    <div className="flex items-center gap-2"><div className="w-2 h-2 rounded-full bg-emerald-400" />Other shift booked</div>
                                </>
                            )}
                        </div>
                    )}
                </div>
            ) : null}

            {selected && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setSelected(null)}>
                    <div className="card p-6 w-72 border-red-500/30" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-4">
                            <div className="flex items-center gap-2">
                                <h3 className="text-white font-semibold">{t('adminSeats.modal.seat', { seatNumber: selected.seatNumber })}</h3>
                                {selected.displayStatus && (
                                    <span className={`text-xs px-2 py-0.5 rounded-full border ${STATUS_BADGE_CLASSES[selected.displayStatus] || 'bg-primary-700/30 text-primary-400 border-primary-700/40'}`}>
                                        {t(`adminStudents.statusLabels.${selected.displayStatus}`)}
                                    </span>
                                )}
                                {selected.displayStatus === 'PENDING' && selected.pendingAmount > 0 && (
                                    <span className="text-xs text-red-400 font-medium">{formatCurrency(selected.pendingAmount)}</span>
                                )}
                                {(selected.displayStatus === 'GRACE' || selected.displayStatus === 'GRACE_OVERDUE') && (
                                    <span className="text-xs text-orange-400 font-medium">
                                        {Math.abs(daysToExpiry(selected.membershipEnd, date))}d overdue
                                    </span>
                                )}
                            </div>
                            <button onClick={() => setSelected(null)} className="text-primary-400 hover:text-white">✕</button>
                        </div>
                        <div className="space-y-2">
                            {[
                                { l: t('adminSeats.modal.student'), v: selected.studentName, link: selected.studentId ? `/admin/students/${selected.studentId}` : null },
                                { l: t('adminSeats.modal.mobile'),  v: selected.studentMobile || '—' },
                                { l: 'Gender',                       v: selected.studentGender || '—' },
                                { l: t('adminSeats.modal.shift'),   v: shiftLabel(selected.shift) },
                                { l: t('adminSeats.modal.expires'), v: selected.membershipEnd },
                                { l: t('adminSeats.modal.daysLeft'), v: t('adminSeats.modal.daysLeftValue', { days: daysToExpiry(selected.membershipEnd, date) }) },
                            ].map(({ l, v, link }) => (
                                <div key={l} className="flex justify-between py-2 border-b border-primary-700/30 last:border-0 text-sm">
                                    <span className="text-primary-400">{l}</span>
                                    {link ? (
                                        <Link to={link} className="text-amber-400 hover:text-amber-300 hover:underline">{v}</Link>
                                    ) : (
                                        <span className="text-white">{v}</span>
                                    )}
                                </div>
                            ))}
                        </div>

                        <div className="mt-5 pt-5 border-t border-primary-700/30">
                            <button onClick={toggleSeatHistory}
                                    className="w-full flex items-center justify-between text-white font-semibold text-sm">
                                {t('adminSeats.modal.seatHistory')}
                                <span className={`transition-transform ${historyOpen ? 'rotate-180' : ''}`}>▾</span>
                            </button>
                            {historyOpen && (
                                <div className="mt-3">
                                    {historyLoading ? (
                                        <div className="shimmer h-16 rounded-xl" />
                                    ) : seatHistory.length === 0 ? (
                                        <p className="text-primary-500 text-xs text-center py-3">{t('adminSeats.modal.noHistory')}</p>
                                    ) : (
                                        <div className="space-y-2 max-h-64 overflow-y-auto">
                                            {seatHistory.map(h => (
                                                <div key={h.membershipId}
                                                     className={`rounded-lg bg-primary-800/40 border px-3 py-2.5 text-xs ${
                                                         h.membershipId === selected.membershipId ? 'border-amber-400/50' : 'border-primary-700/30'}`}>
                                                    <div className="flex items-center justify-between mb-1.5">
                                                        <span className="text-white font-semibold">{h.studentName}</span>
                                                        {h.membershipId === selected.membershipId && (
                                                            <span className="px-2 py-0.5 rounded-full font-medium border bg-amber-500/20 text-amber-400 border-amber-500/30">
                                                                {t('adminSeats.modal.current')}
                                                            </span>
                                                        )}
                                                    </div>
                                                    <div className="text-primary-400 space-y-0.5">
                                                        <p>{h.startDate} → {h.endDate} · {shiftLabel(h.shift)}</p>
                                                        <p className="font-mono">{h.status}</p>
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
