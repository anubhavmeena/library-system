import { useId } from 'react'

// Corner seal "NEW" tag: straight top/left edges meeting in a rounded corner,
// with a scalloped quarter-circle edge sweeping around the bottom-right.
// Absolutely positioned over the top-left corner of its (relative) parent.
// Lobed edge follows a superellipse (fuller than a quarter circle, like a
// rounded square corner) with SCALLOPS rounded lobes pushed outward.
const R = 31, BUMP = 2.6, SCALLOPS = 5, SQUARENESS = 2.6, LOBE = 0.7, STEPS = 160
const EDGE = Array.from({ length: STEPS + 1 }, (_, i) => {
    const t = i / STEPS, a = (Math.PI / 2) * t
    const c = Math.cos(a), s = Math.sin(a)
    const k = 1 / (c ** SQUARENESS + s ** SQUARENESS) ** (1 / SQUARENESS)
    const r = (R + BUMP * Math.abs(Math.sin(Math.PI * SCALLOPS * t)) ** LOBE) * k
    return `L${(r * c).toFixed(2)} ${(r * s).toFixed(2)}`
}).join(' ')
const SHAPE = `M0 4 Q0 0 4 0 ${EDGE} Z`

export default function NewBadge({ label, title, className = '' }) {
    const id = useId().replace(/:/g, '')
    return (
        <svg viewBox="0 0 40 40" aria-label={title} role="img"
            className={`absolute pointer-events-none drop-shadow-[1px_1.5px_1.5px_rgba(0,0,0,0.45)] ${className}`}>
            {title && <title>{title}</title>}
            <defs>
                <linearGradient id={`fill-${id}`} x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0%"  stopColor="#6080f0" />
                    <stop offset="35%" stopColor="#3d5fe0" />
                    <stop offset="100%" stopColor="#2b46c8" />
                </linearGradient>
                <linearGradient id={`shine-${id}`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%"  stopColor="white" stopOpacity="0.35" />
                    <stop offset="22%" stopColor="white" stopOpacity="0" />
                </linearGradient>
            </defs>
            <path d={SHAPE} fill={`url(#fill-${id})`} />
            <path d={SHAPE} fill={`url(#shine-${id})`} />
            <text x="15" y="15" transform="rotate(-45 15 15)" textAnchor="middle" dominantBaseline="central"
                fill="white" fontSize="9" fontWeight="700" letterSpacing="0.4">
                {label}
            </text>
        </svg>
    )
}
