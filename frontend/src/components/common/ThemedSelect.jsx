import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Check, ChevronDown } from 'lucide-react'

// Drop-in replacement for a native <select> whose option list matches the
// app's dark card theme — the browser/OS draws native <option> popups and
// ignores most styling. The menu is portaled to <body> with fixed
// positioning so it isn't clipped by overflow-hidden/-auto ancestors
// (e.g. a table's horizontal scroll wrapper).
//
// options: [{ value, label }]
export default function ThemedSelect({ value, onChange, options, className = '', ariaLabel }) {
    const [open, setOpen]       = useState(false)
    const [active, setActive]   = useState(0)
    const [pos, setPos]         = useState(null)
    const buttonRef = useRef(null)
    const menuRef   = useRef(null)

    const selectedIndex = Math.max(0, options.findIndex(o => o.value === value))
    const selected = options[selectedIndex]

    const place = () => {
        const r = buttonRef.current?.getBoundingClientRect()
        if (!r) return
        const menuHeight = Math.min(288, options.length * 40 + 12)
        const openUp = r.bottom + menuHeight + 8 > window.innerHeight && r.top > menuHeight + 8
        setPos({
            left: r.left,
            minWidth: r.width,
            top: openUp ? undefined : r.bottom + 6,
            bottom: openUp ? window.innerHeight - r.top + 6 : undefined,
        })
    }

    useLayoutEffect(() => { if (open) place() }, [open])

    useEffect(() => {
        if (!open) return
        const onDown = (e) => {
            if (buttonRef.current?.contains(e.target) || menuRef.current?.contains(e.target)) return
            setOpen(false)
        }
        // Close rather than chase the button on page scroll; scrolling inside
        // the menu itself is fine.
        const onScroll = (e) => { if (!menuRef.current?.contains(e.target)) setOpen(false) }
        document.addEventListener('mousedown', onDown)
        window.addEventListener('scroll', onScroll, true)
        window.addEventListener('resize', place)
        return () => {
            document.removeEventListener('mousedown', onDown)
            window.removeEventListener('scroll', onScroll, true)
            window.removeEventListener('resize', place)
        }
    }, [open])

    useEffect(() => {
        if (open) menuRef.current?.querySelector(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' })
    }, [open, active])

    const choose = (i) => {
        onChange(options[i].value)
        setOpen(false)
        buttonRef.current?.focus()
    }

    const onKeyDown = (e) => {
        if (!open) {
            if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(e.key)) {
                e.preventDefault()
                setActive(selectedIndex)
                setOpen(true)
            }
            return
        }
        if (e.key === 'ArrowDown') { e.preventDefault(); setActive(i => Math.min(options.length - 1, i + 1)) }
        else if (e.key === 'ArrowUp') { e.preventDefault(); setActive(i => Math.max(0, i - 1)) }
        else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); choose(active) }
        else if (e.key === 'Escape' || e.key === 'Tab') setOpen(false)
    }

    return (
        <>
            <button type="button" ref={buttonRef}
                    onClick={() => { setActive(selectedIndex); setOpen(o => !o) }}
                    onKeyDown={onKeyDown}
                    aria-haspopup="listbox" aria-expanded={open} aria-label={ariaLabel}
                    className={`flex items-center justify-between gap-2 w-full bg-primary-900/60 border rounded-xl px-3 py-1.5 text-xs text-white font-normal text-left transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-amber-400/10 ${
                        open ? 'border-amber-400/60' : 'border-primary-700/40 hover:border-primary-500/60'
                    } ${className}`}>
                <span className="truncate">{selected?.label}</span>
                <ChevronDown size={14} className={`flex-shrink-0 text-primary-400 transition-transform duration-200 ${open ? 'rotate-180' : ''}`} />
            </button>
            {open && pos && createPortal(
                <ul ref={menuRef} role="listbox"
                    style={{ position: 'fixed', left: pos.left, top: pos.top, bottom: pos.bottom, minWidth: pos.minWidth }}
                    className="z-[60] max-h-72 overflow-y-auto p-1.5 rounded-2xl border border-primary-700/40 bg-primary-900/95 backdrop-blur-md shadow-2xl shadow-black/40">
                    {options.map((o, i) => {
                        const isSelected = i === selectedIndex
                        return (
                            <li key={o.value} data-index={i} role="option" aria-selected={isSelected}
                                onMouseEnter={() => setActive(i)}
                                onMouseDown={e => e.preventDefault()}
                                onClick={() => choose(i)}
                                className={`flex items-center justify-between gap-3 px-3 py-2 rounded-xl text-sm whitespace-nowrap cursor-pointer transition-colors ${
                                    i === active ? 'bg-primary-800/70' : ''
                                } ${isSelected ? 'text-amber-400 font-medium' : 'text-primary-200'}`}>
                                <span>{o.label}</span>
                                {isSelected && <Check size={14} className="flex-shrink-0" />}
                            </li>
                        )
                    })}
                </ul>,
                document.body,
            )}
        </>
    )
}
