import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

const VIEW = 280   // crop viewport size, css px (square)
const OUTPUT = 600 // output image size, px (square)

function clampOffset(offset, scale, natural) {
    const dispW = natural.w * scale
    const dispH = natural.h * scale
    const minX = VIEW - dispW, maxX = 0
    const minY = VIEW - dispH, maxY = 0
    return {
        x: Math.min(maxX, Math.max(minX, offset.x)),
        y: Math.min(maxY, Math.max(minY, offset.y)),
    }
}

export default function CropPhotoModal({ imageSrc, onConfirm, onCancel, onRetake }) {
    const { t } = useTranslation()
    const [natural, setNatural] = useState({ w: 0, h: 0 })
    const [baseScale, setBaseScale] = useState(1)
    const [zoom, setZoom] = useState(1)
    const [offset, setOffset] = useState({ x: 0, y: 0 })

    const dragRef = useRef(null) // { startX, startY, startOffset }

    const onImageLoad = e => {
        const w = e.target.naturalWidth, h = e.target.naturalHeight
        const bs = Math.max(VIEW / w, VIEW / h)
        setNatural({ w, h })
        setBaseScale(bs)
        setZoom(1)
        setOffset({ x: (VIEW - w * bs) / 2, y: (VIEW - h * bs) / 2 })
    }

    const onZoomChange = z => {
        const scale = baseScale * z
        setZoom(z)
        setOffset(o => clampOffset(o, scale, natural))
    }

    const onPointerDown = e => {
        e.currentTarget.setPointerCapture(e.pointerId)
        dragRef.current = { startX: e.clientX, startY: e.clientY, startOffset: offset }
    }

    const onPointerMove = e => {
        if (!dragRef.current) return
        const { startX, startY, startOffset } = dragRef.current
        const scale = baseScale * zoom
        const next = { x: startOffset.x + (e.clientX - startX), y: startOffset.y + (e.clientY - startY) }
        setOffset(clampOffset(next, scale, natural))
    }

    const onPointerUp = () => { dragRef.current = null }

    const confirm = () => {
        const scale = baseScale * zoom
        const srcX = -offset.x / scale
        const srcY = -offset.y / scale
        const srcSize = VIEW / scale

        const img = new Image()
        img.onload = () => {
            const canvas = document.createElement('canvas')
            canvas.width = OUTPUT
            canvas.height = OUTPUT
            canvas.getContext('2d').drawImage(img, srcX, srcY, srcSize, srcSize, 0, 0, OUTPUT, OUTPUT)
            canvas.toBlob(blob => { if (blob) onConfirm(blob) }, 'image/jpeg', 0.9)
        }
        img.src = imageSrc
    }

    return (
        <div className="fixed inset-0 z-50 bg-primary-950/80 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="card p-6 w-full max-w-md">
                <h3 className="section-title mb-4">{t('adminImport.manual.photo.cropTitle')}</h3>
                <div
                    className="relative mx-auto rounded-xl overflow-hidden bg-black touch-none select-none"
                    style={{ width: VIEW, height: VIEW }}
                    onPointerDown={onPointerDown}
                    onPointerMove={onPointerMove}
                    onPointerUp={onPointerUp}
                    onPointerCancel={onPointerUp}
                >
                    <img
                        src={imageSrc}
                        onLoad={onImageLoad}
                        draggable={false}
                        alt=""
                        style={{
                            position: 'absolute',
                            left: offset.x,
                            top: offset.y,
                            width: natural.w * baseScale * zoom,
                            height: natural.h * baseScale * zoom,
                            maxWidth: 'none',
                        }}
                    />
                </div>

                <div className="flex items-center gap-3 mt-4">
                    <span className="text-primary-400 text-xs">{t('adminImport.manual.photo.zoom')}</span>
                    <input
                        type="range" min="1" max="3" step="0.05" value={zoom}
                        onChange={e => onZoomChange(Number(e.target.value))}
                        className="flex-1"
                    />
                </div>

                <div className="flex gap-2 mt-4">
                    <button onClick={onCancel} className="btn-ghost flex-1 py-2">{t('adminImport.manual.photo.cancel')}</button>
                    <button onClick={onRetake} className="btn-outline flex-1 py-2">{t('adminImport.manual.photo.retake')}</button>
                    <button onClick={confirm} className="btn-primary flex-1 py-2">{t('adminImport.manual.photo.usePhoto')}</button>
                </div>
            </div>
        </div>
    )
}
