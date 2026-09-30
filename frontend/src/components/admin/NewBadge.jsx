import { useId } from 'react'

// Corner ribbon "NEW" tag that wraps the top-left corner of its (relative)
// parent: a translucent gradient band with the label on the diagonal, plus
// two darker tails folding back along the top and left edges.
export default function NewBadge({ label, title, className = '' }) {
    const id = useId().replace(/:/g, '')
    return (
        <svg viewBox="0 0 40 40" aria-label={title} role="img"
            className={`absolute pointer-events-none drop-shadow-[0_1px_1.5px_rgba(0,0,0,0.45)] ${className}`}>
            {title && <title>{title}</title>}
            <defs>
                <linearGradient id={`band-${id}`} x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0%"   stopColor="#7dd3fc" />
                    <stop offset="35%"  stopColor="#38bdf8" />
                    <stop offset="100%" stopColor="#0284c7" />
                </linearGradient>
            </defs>
            <g opacity="0.85">
                {/* folded-back tails */}
                <path d="M29 0 Q33 0 35.5 1.8 L40 5 L27 5 Z" fill="#075985" />
                <path d="M0 29 Q0 33 1.8 35.5 L5 40 L5 27 Z" fill="#075985" />
                {/* main band */}
                <path d="M0 9 Q0 0 9 0 L33 0 L0 33 Z" fill={`url(#band-${id})`} />
            </g>
            <text x="12" y="12" transform="rotate(-45 12 12)" textAnchor="middle" dominantBaseline="central"
                fill="white" fontSize="8" fontWeight="700" letterSpacing="0.6">
                {label}
            </text>
        </svg>
    )
}
