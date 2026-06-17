import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

export default function StudentGalleryPage() {
    const { t } = useTranslation()
    const [photos, setPhotos]   = useState([])
    const [loading, setLoading] = useState(true)
    const [preview, setPreview] = useState(null)

    useEffect(() => {
        const load = async () => {
            setLoading(true)
            try {
                const res = await api.get('/gallery')
                setPhotos(res.data.data || [])
            } catch { toast.error(t('studentGallery.loadFailed')) }
            finally { setLoading(false) }
        }
        load()
    }, [])

    return (
        <div>
            <div className="mb-6">
                <h1 className="page-header">{t('studentGallery.title')}</h1>
                <p className="text-primary-400">{t('studentGallery.subtitle')}</p>
            </div>

            {loading ? (
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
                    {[...Array(8)].map((_, i) => (
                        <div key={i} className="shimmer aspect-square rounded-xl" />
                    ))}
                </div>
            ) : photos.length === 0 ? (
                <div className="text-center py-20 text-primary-500">
                    <p className="text-5xl mb-4">📷</p>
                    <p>{t('studentGallery.empty')}</p>
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
                        <div className="flex justify-center mt-4">
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
