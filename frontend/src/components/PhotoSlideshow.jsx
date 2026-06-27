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
        <div
            className="relative rounded-2xl overflow-hidden select-none shadow-2xl shadow-primary-900/50"
            onMouseEnter={() => setHovered(true)}
            onMouseLeave={() => setHovered(false)}
        >
            {/* Slide images */}
            <div className="relative aspect-video bg-primary-800">
                {photos.map((photo, i) => (
                    <img
                        key={photo.src}
                        src={photo.src}
                        alt={photo.alt}
                        onError={e => { e.currentTarget.src = TRANSPARENT }}
                        className={`absolute inset-0 w-full h-full object-cover transition-opacity duration-700 ${
                            i === current ? 'opacity-100' : 'opacity-0'
                        }`}
                    />
                ))}
                {/* Side vignettes for arrow contrast */}
                <div className="absolute inset-0 bg-gradient-to-r from-primary-900/30 via-transparent to-primary-900/30 pointer-events-none" />
                {/* Bottom vignette for dots contrast */}
                <div className="absolute bottom-0 left-0 right-0 h-16 bg-gradient-to-t from-primary-900/60 to-transparent pointer-events-none" />
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

            {/* Dot indicators */}
            {photos.length > 1 && (
                <div className="absolute bottom-3 left-1/2 -translate-x-1/2 flex gap-1.5">
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
        </div>
    )
}
