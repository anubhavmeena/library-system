import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

export default function AdminGalleryPage() {
    const { t } = useTranslation()
    const [photos, setPhotos]       = useState([])
    const [loading, setLoading]     = useState(true)
    const [uploading, setUploading] = useState(false)
    const [caption, setCaption]     = useState('')
    const [preview, setPreview]     = useState(null)   // lightbox
    const fileRef = useRef()

    const load = async () => {
        setLoading(true)
        try {
            const res = await api.get('/gallery')
            setPhotos(res.data.data || [])
        } catch { toast.error(t('adminGallery.loadFailed')) }
        finally { setLoading(false) }
    }

    useEffect(() => { load() }, [])

    const handleFileChange = async (e) => {
        const file = e.target.files?.[0]
        if (!file) return
        const allowed = ['image/jpeg', 'image/png', 'image/webp']
        if (!allowed.includes(file.type)) {
            toast.error('Only JPEG, PNG, or WebP images allowed')
            return
        }
        if (file.size > 5 * 1024 * 1024) {
            toast.error('File must not exceed 5MB')
            return
        }
        setUploading(true)
        try {
            const formData = new FormData()
            formData.append('file', file)
            if (caption.trim()) formData.append('caption', caption.trim())
            await api.post('/gallery', formData)
            toast.success(t('adminGallery.uploadSuccess'))
            setCaption('')
            if (fileRef.current) fileRef.current.value = ''
            await load()
        } catch { toast.error(t('adminGallery.uploadFailed')) }
        finally { setUploading(false) }
    }

    const handleDelete = async (photo) => {
        if (!window.confirm(t('adminGallery.deleteConfirm'))) return
        try {
            await api.delete(`/gallery/${photo.id}`)
            toast.success(t('adminGallery.deleteSuccess'))
            setPhotos(prev => prev.filter(p => p.id !== photo.id))
            if (preview?.id === photo.id) setPreview(null)
        } catch { toast.error(t('adminGallery.deleteFailed')) }
    }

    return (
        <div>
            <div className="mb-6">
                <h1 className="page-header">{t('adminGallery.title')}</h1>
                <p className="text-primary-400">{t('adminGallery.subtitle')}</p>
            </div>

            {/* Upload section */}
            <div className="card p-5 mb-6 flex flex-wrap gap-3 items-end">
                <div className="flex-1 min-w-48">
                    <label className="label">{t('adminGallery.captionPlaceholder')}</label>
                    <input
                        type="text"
                        value={caption}
                        onChange={e => setCaption(e.target.value)}
                        placeholder={t('adminGallery.captionPlaceholder')}
                        className="input w-full"
                        disabled={uploading}
                    />
                </div>
                <div>
                    <input
                        ref={fileRef}
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        className="hidden"
                        onChange={handleFileChange}
                        disabled={uploading}
                    />
                    <button
                        onClick={() => fileRef.current?.click()}
                        disabled={uploading}
                        className="btn-primary px-5 py-2.5 whitespace-nowrap"
                    >
                        {uploading ? t('adminGallery.uploading') : `↑ ${t('adminGallery.uploadBtn')}`}
                    </button>
                </div>
            </div>

            {/* Grid */}
            {loading ? (
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
                    {[...Array(8)].map((_, i) => (
                        <div key={i} className="shimmer aspect-square rounded-xl" />
                    ))}
                </div>
            ) : photos.length === 0 ? (
                <div className="text-center py-20 text-primary-500">
                    <p className="text-5xl mb-4">📷</p>
                    <p>{t('adminGallery.empty')}</p>
                </div>
            ) : (
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
                    {photos.map(photo => (
                        <div
                            key={photo.id}
                            className="group relative aspect-square rounded-xl overflow-hidden bg-primary-800 cursor-pointer"
                            onClick={() => setPreview(photo)}
                        >
                            <img
                                src={photo.url}
                                alt={photo.caption || ''}
                                className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                            />
                            {photo.caption && (
                                <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/80 to-transparent px-3 py-2">
                                    <p className="text-white text-xs truncate">{photo.caption}</p>
                                </div>
                            )}
                            <button
                                onClick={e => { e.stopPropagation(); handleDelete(photo) }}
                                className="absolute top-2 right-2 w-7 h-7 rounded-full bg-black/60 hover:bg-red-500/80 text-white text-xs flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all"
                                title="Delete"
                            >
                                ✕
                            </button>
                        </div>
                    ))}
                </div>
            )}

            {/* Lightbox */}
            {preview && (
                <div
                    className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center p-4"
                    onClick={() => setPreview(null)}
                >
                    <div className="relative max-w-4xl w-full" onClick={e => e.stopPropagation()}>
                        <img
                            src={preview.url}
                            alt={preview.caption || ''}
                            className="w-full max-h-[80vh] object-contain rounded-xl"
                        />
                        {preview.caption && (
                            <p className="text-center text-white mt-3 text-sm">{preview.caption}</p>
                        )}
                        <div className="flex gap-3 justify-center mt-4">
                            <button
                                onClick={() => handleDelete(preview)}
                                className="btn-outline border-red-500/50 text-red-400 hover:bg-red-500/10 px-4 py-2 text-sm"
                            >
                                🗑 Delete
                            </button>
                            <button
                                onClick={() => setPreview(null)}
                                className="btn-outline px-4 py-2 text-sm"
                            >
                                ✕ Close
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
