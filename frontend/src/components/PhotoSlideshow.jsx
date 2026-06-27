import { useState, useEffect, useCallback } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'

const TRANSPARENT = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7'

export default function PhotoSlideshow({ photos }) {
    const [current, setCurrent] = useState(0)
    const [hovered, setHovered] = useState(false)

    const prev = useCallback(() => setCurrent(i => (i - 1 + photos.length) % photos.length), [photos.length])
    const next = useCallback(() => setCurrent(i => (i + 1) % photos.length), [photos.length])

    useEffect(() => {
        if (hovered || photos.length <= 1) return
        const id = setInterval(next, 4000)
        return () => clearInterval(id)
    }, [hovered, next, photos.length])

    if (!photos.length) return null

    return (
        <>
        <div
            className="relative overflow-hidden select-none"
            onMouseEnter={() => setHovered(true)}
            onMouseLeave={() => setHovered(false)}
        >
            {/* Slide images */}
            <div className="relative aspect-video bg-primary-800">
                <img
                    key={current}
                    src={photos[current].src}
                    alt={photos[current].alt}
                    onError={e => { e.currentTarget.src = TRANSPARENT }}
                    className="absolute inset-0 w-full h-full object-cover"
                    style={{ animation: 'slideshowFade 0.6s ease-in-out' }}
                />
                {/* Preload next photo */}
                <img src={photos[(current + 1) % photos.length].src} alt="" aria-hidden="true" className="hidden" />
                {/* Side vignettes for arrow contrast */}
                <div className="absolute inset-0 bg-gradient-to-r from-primary-900/30 via-transparent to-primary-900/30 pointer-events-none" />
            </div>

            {/* Prev / Next arrows */}
            {photos.length > 1 && (
                <>
                    <button
                        onClick={prev}
                        aria-label="Previous photo"
                        className="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-primary-900/70 backdrop-blur-sm border border-primary-600/40 flex items-center justify-center text-white hover:bg-primary-800 hover:border-primary-400/40 transition-all"
                    >
                        <ChevronLeft size={18} />
                    </button>
                    <button
                        onClick={next}
                        aria-label="Next photo"
                        className="absolute right-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-primary-900/70 backdrop-blur-sm border border-primary-600/40 flex items-center justify-center text-white hover:bg-primary-800 hover:border-primary-400/40 transition-all"
                    >
                        <ChevronRight size={18} />
                    </button>
                </>
            )}

        </div>

        {/* Dot indicators */}
        {photos.length > 1 && (
            <div className="flex justify-center gap-1.5 pt-4">
                {photos.map((_, i) => (
                    <button
                        key={i}
                        onClick={() => setCurrent(i)}
                        aria-label={`Go to photo ${i + 1}`}
                        className={`rounded-full transition-all duration-300 ${
                            i === current
                                ? 'w-5 h-2 bg-amber-400'
                                : 'w-2 h-2 bg-white/50 hover:bg-white/80'
                        }`}
                    />
                ))}
            </div>
        )}
        </>
    )
}
