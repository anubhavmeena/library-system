// Starburst "NEW" seal, absolutely positioned over its (relative) parent.
const POINTS = 24
const STAR = Array.from({ length: POINTS * 2 }, (_, i) => {
    const r = i % 2 === 0 ? 20 : 16.5
    const a = (Math.PI * i) / POINTS - Math.PI / 2
    return `${(20 + r * Math.cos(a)).toFixed(2)},${(20 + r * Math.sin(a)).toFixed(2)}`
}).join(' ')

export default function NewBadge({ label, title, className = '' }) {
    return (
        <svg viewBox="0 0 40 40" aria-label={title} role="img"
            className={`absolute pointer-events-none drop-shadow-[0_1px_1.5px_rgba(0,0,0,0.45)] ${className}`}>
            {title && <title>{title}</title>}
            <polygon points={STAR} fill="#3d5fe0" stroke="#3d5fe0" strokeWidth="1" strokeLinejoin="round" />
            <text x="20" y="20" transform="rotate(-40 20 20)" textAnchor="middle" dominantBaseline="central"
                fill="white" fontSize="12" fontWeight="800">
                {label}
            </text>
        </svg>
    )
}
