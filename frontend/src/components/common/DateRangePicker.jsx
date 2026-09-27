import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { ChevronLeft, ChevronRight } from 'lucide-react'

// Themed single-date / date-range calendar popover, anchored under
// `anchorRef` and portaled to <body> (same reasoning as ThemedSelect — it
// must escape the table's overflow wrapper). Dates are "YYYY-MM-DD" strings
// throughout; all arithmetic is done on UTC midnights so the browser's own
// timezone never shifts a day.
//
// Click a start date, then an end date; clicking the same day twice picks a
// single date. Dates after `maxDate` are disabled.

const toKey = (d) => d.toISOString().slice(0, 10)
const fromKey = (k) => new Date(`${k}T00:00:00Z`)
const addDays = (k, n) => { const d = fromKey(k); d.setUTCDate(d.getUTCDate() + n); return toKey(d) }

export function formatRangeLabel(from, to, locale = 'en-IN') {
    const fmt = (k, withYear) => fromKey(k).toLocaleDateString(locale, {
        timeZone: 'UTC', day: 'numeric', month: 'short', ...(withYear ? { year: 'numeric' } : {}),
    })
    if (!from) return ''
    if (!to || from === to) return fmt(from, true)
    return from.slice(0, 4) === to.slice(0, 4)
        ? `${fmt(from, false)} – ${fmt(to, true)}`
        : `${fmt(from, true)} – ${fmt(to, true)}`
}

export default function DateRangePicker({ anchorRef, open, onClose, from, to, maxDate, onApply, labels, locale = 'en-IN' }) {
    const panelRef = useRef(null)
    const [pos, setPos]         = useState(null)
    const [start, setStart]     = useState(from || null)
    const [end, setEnd]         = useState(to || null)
    const [hover, setHover]     = useState(null)
    const [month, setMonth]     = useState(() => (from || maxDate).slice(0, 7))

    // Reset the draft selection each time the popover opens.
    useEffect(() => {
        if (!open) return
        setStart(from || null)
        setEnd(to || null)
        setHover(null)
        setMonth((from || maxDate).slice(0, 7))
    }, [open])

    const place = () => {
        const r = anchorRef.current?.getBoundingClientRect()
        if (!r) return
        const width = 288
        const left = Math.max(8, Math.min(r.left, window.innerWidth - width - 8))
        setPos({ left, top: r.bottom + 6, width })
    }

    useLayoutEffect(() => { if (open) place() }, [open])

    useEffect(() => {
        if (!open) return
        const onDown = (e) => {
            if (panelRef.current?.contains(e.target) || anchorRef.current?.contains(e.target)) return
            onClose()
        }
        const onScroll = (e) => { if (!panelRef.current?.contains(e.target)) onClose() }
        const onKey = (e) => { if (e.key === 'Escape') onClose() }
        document.addEventListener('mousedown', onDown)
        document.addEventListener('keydown', onKey)
        window.addEventListener('scroll', onScroll, true)
        window.addEventListener('resize', place)
        return () => {
            document.removeEventListener('mousedown', onDown)
            document.removeEventListener('keydown', onKey)
            window.removeEventListener('scroll', onScroll, true)
            window.removeEventListener('resize', place)
        }
    }, [open])

    if (!open || !pos) return null

    const firstOfMonth = `${month}-01`
    const gridStart = addDays(firstOfMonth, -fromKey(firstOfMonth).getUTCDay())
    const days = Array.from({ length: 42 }, (_, i) => addDays(gridStart, i))
    const shiftMonth = (n) => {
        const d = fromKey(firstOfMonth)
        d.setUTCMonth(d.getUTCMonth() + n)
        setMonth(toKey(d).slice(0, 7))
    }
    const canGoNext = month < maxDate.slice(0, 7)

    const weekdays = days.slice(0, 7).map(k =>
        fromKey(k).toLocaleDateString(locale, { timeZone: 'UTC', weekday: 'narrow' }))
    const monthLabel = fromKey(firstOfMonth).toLocaleDateString(locale, { timeZone: 'UTC', month: 'long', year: 'numeric' })

    // While only the start is chosen, preview the range up to the hovered day.
    const rangeEnd = end || (start && hover) || start
    const lo = start && rangeEnd ? (start < rangeEnd ? start : rangeEnd) : null
    const hi = start && rangeEnd ? (start < rangeEnd ? rangeEnd : start) : null

    const pick = (k) => {
        if (!start || end) {
            setStart(k)
            setEnd(null)
        } else if (k < start) {
            setEnd(start)
            setStart(k)
        } else {
            setEnd(k)
        }
    }

    const apply = () => {
        if (!start) return
        onApply({ from: start, to: end || start })
    }

    return createPortal(
        <div ref={panelRef}
             style={{ position: 'fixed', left: pos.left, top: pos.top, width: pos.width }}
             className="z-[60] p-4 rounded-2xl border border-primary-700/40 bg-primary-900/95 backdrop-blur-md shadow-2xl shadow-black/40 text-sm">
            <div className="flex items-center justify-between mb-3">
                <button type="button" onClick={() => shiftMonth(-1)} aria-label="Previous month"
                        className="p-1.5 rounded-lg text-primary-300 hover:text-white hover:bg-primary-800/70 transition-colors">
                    <ChevronLeft size={16} />
                </button>
                <span className="text-white font-semibold">{monthLabel}</span>
                <button type="button" onClick={() => shiftMonth(1)} disabled={!canGoNext} aria-label="Next month"
                        className="p-1.5 rounded-lg text-primary-300 hover:text-white hover:bg-primary-800/70 transition-colors disabled:opacity-30 disabled:hover:bg-transparent">
                    <ChevronRight size={16} />
                </button>
            </div>

            <div className="grid grid-cols-7 mb-1">
                {weekdays.map((w, i) => (
                    <span key={i} className="text-center text-[11px] text-primary-500 font-medium py-1">{w}</span>
                ))}
            </div>
            <div className="grid grid-cols-7 gap-y-1" onMouseLeave={() => setHover(null)}>
                {days.map(k => {
                    const inMonth = k.slice(0, 7) === month
                    const disabled = k > maxDate
                    const isEdge = k === lo || k === hi
                    const inRange = lo && hi && k > lo && k < hi
                    const isToday = k === maxDate
                    return (
                        <div key={k} className={`flex justify-center ${
                            inRange ? 'bg-amber-400/10' : ''
                        } ${lo !== hi && k === lo ? 'bg-gradient-to-r from-transparent from-50% to-amber-400/10 to-50%' : ''} ${
                            lo !== hi && k === hi ? 'bg-gradient-to-l from-transparent from-50% to-amber-400/10 to-50%' : ''
                        }`}>
                            <button type="button" disabled={disabled}
                                    onClick={() => pick(k)}
                                    onMouseEnter={() => setHover(k)}
                                    className={`w-8 h-8 rounded-full text-xs transition-colors ${
                                        isEdge
                                            ? 'bg-amber-500 text-primary-900 font-semibold'
                                            : disabled
                                                ? 'text-primary-700 cursor-not-allowed'
                                                : inRange
                                                    ? 'text-amber-300 hover:bg-primary-800/70'
                                                    : inMonth
                                                        ? 'text-primary-100 hover:bg-primary-800/70'
                                                        : 'text-primary-600 hover:bg-primary-800/70'
                                    } ${isToday && !isEdge ? 'ring-1 ring-amber-400/50' : ''}`}>
                                {Number(k.slice(8))}
                            </button>
                        </div>
                    )
                })}
            </div>

            <p className="mt-3 text-xs text-primary-400 min-h-[1rem]">
                {lo ? formatRangeLabel(lo, hi, locale) : labels.hint}
            </p>

            <div className="flex justify-end gap-2 mt-3 pt-3 border-t border-primary-700/30">
                <button type="button" onClick={onClose}
                        className="btn-ghost text-xs px-3 py-1.5 border border-primary-700/40 rounded-lg">
                    {labels.cancel}
                </button>
                <button type="button" onClick={apply} disabled={!start}
                        className="bg-amber-500 hover:bg-amber-400 text-primary-900 font-semibold text-xs px-4 py-1.5 rounded-lg transition-colors disabled:opacity-40">
                    {labels.apply}
                </button>
            </div>
        </div>,
        document.body,
    )
}
