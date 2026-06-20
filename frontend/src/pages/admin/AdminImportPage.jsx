import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

export default function AdminImportPage() {
    const { t } = useTranslation()
    const inputRef = useRef(null)

    const [file,      setFile]      = useState(null)
    const [dragging,  setDragging]  = useState(false)
    const [uploading, setUploading] = useState(false)
    const [result,    setResult]    = useState(null)
    const [error,     setError]     = useState(null)

    const accept = '.csv,.xlsx,.xls'

    const pickFile = f => {
        if (!f) return
        setFile(f)
        setResult(null)
        setError(null)
    }

    const onDrop = e => {
        e.preventDefault()
        setDragging(false)
        pickFile(e.dataTransfer.files[0])
    }

    const onSubmit = async () => {
        if (!file) return
        setUploading(true)
        setError(null)
        try {
            const fd = new FormData()
            fd.append('file', file)
            const res = await api.post('/admin/students/import', fd, {
                headers: { 'Content-Type': 'multipart/form-data' },
            })
            const data = res.data.data
            setResult(data)
            if (data.skipped === 0) {
                toast.success(t('adminImport.successAll', { count: data.imported }))
            } else {
                toast(t('adminImport.partialSuccess', { imported: data.imported, total: data.totalRows }))
            }
        } catch (e) {
            const msg = e.response?.data?.message || t('adminImport.uploadFailed')
            setError(msg)
            toast.error(msg)
        } finally {
            setUploading(false)
        }
    }

    return (
        <div className="max-w-3xl mx-auto">
            <div className="mb-6">
                <h1 className="page-header">{t('adminImport.title')}</h1>
                <p className="text-primary-400">{t('adminImport.subtitle')}</p>
            </div>

            {/* Format hint */}
            <div className="card p-4 mb-6 border-blue-500/20 bg-blue-500/5">
                <p className="text-blue-300 text-sm">
                    <span className="font-semibold">📋 Format: </span>
                    {t('adminImport.format')}
                </p>
            </div>

            {/* Drop zone */}
            <div
                className={`card p-10 mb-4 text-center cursor-pointer border-2 border-dashed transition-all
                    ${dragging ? 'border-amber-500/60 bg-amber-500/5' : 'border-primary-600/40 hover:border-amber-500/30 hover:bg-amber-500/3'}`}
                onClick={() => inputRef.current?.click()}
                onDragOver={e => { e.preventDefault(); setDragging(true) }}
                onDragLeave={() => setDragging(false)}
                onDrop={onDrop}
            >
                <input
                    ref={inputRef}
                    type="file"
                    accept={accept}
                    className="hidden"
                    onChange={e => pickFile(e.target.files[0])}
                />
                <div className="text-4xl mb-3">{file ? '📄' : '⬆️'}</div>
                {file ? (
                    <>
                        <p className="text-white font-medium">{file.name}</p>
                        <p className="text-primary-400 text-sm mt-1">{(file.size / 1024).toFixed(1)} KB</p>
                        <button
                            onClick={e => { e.stopPropagation(); setFile(null); setResult(null) }}
                            className="mt-2 text-xs text-primary-500 hover:text-red-400 transition-colors"
                        >
                            ✕ Remove
                        </button>
                    </>
                ) : (
                    <>
                        <p className="text-primary-300">{t('adminImport.dropzone')}</p>
                        <p className="text-primary-500 text-sm mt-1">{t('adminImport.accepts')}</p>
                    </>
                )}
            </div>

            <button
                onClick={onSubmit}
                disabled={!file || uploading}
                className="btn-primary w-full py-3 text-base mb-8 disabled:opacity-40 disabled:cursor-not-allowed"
            >
                {uploading ? t('adminImport.uploading') : t('adminImport.upload')}
            </button>

            {error && (
                <div className="card p-4 mb-6 border-red-500/30 bg-red-500/10">
                    <p className="text-red-400 text-sm font-medium">Import failed: {error}</p>
                </div>
            )}

            {/* Results */}
            {result && (
                <div className="space-y-4">
                    <h2 className="section-title">{t('adminImport.results')}</h2>

                    <div className="grid grid-cols-3 gap-4">
                        <div className="card p-4 text-center bg-gradient-to-br from-blue-500/10 to-transparent border-blue-500/20">
                            <p className="text-blue-400 text-xs uppercase tracking-widest mb-1">{t('adminImport.total')}</p>
                            <p className="text-3xl font-bold text-white">{result.totalRows}</p>
                        </div>
                        <div className="card p-4 text-center bg-gradient-to-br from-emerald-500/10 to-transparent border-emerald-500/20">
                            <p className="text-emerald-400 text-xs uppercase tracking-widest mb-1">{t('adminImport.imported')}</p>
                            <p className="text-3xl font-bold text-emerald-400">{result.imported}</p>
                        </div>
                        <div className={`card p-4 text-center bg-gradient-to-br to-transparent
                            ${result.skipped > 0 ? 'from-red-500/10 border-red-500/20' : 'from-primary-700/20 border-primary-600/20'}`}>
                            <p className={`text-xs uppercase tracking-widest mb-1 ${result.skipped > 0 ? 'text-red-400' : 'text-primary-400'}`}>
                                {t('adminImport.skipped')}
                            </p>
                            <p className={`text-3xl font-bold ${result.skipped > 0 ? 'text-red-400' : 'text-primary-500'}`}>
                                {result.skipped}
                            </p>
                        </div>
                    </div>

                    {result.errors?.length > 0 && (
                        <div className="card p-0 overflow-hidden">
                            <div className="p-4 border-b border-primary-700/30">
                                <h3 className="text-white font-semibold">{t('adminImport.errorsTitle')}</h3>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="border-b border-primary-700/30">
                                            <th className="text-left p-3 text-primary-400 font-medium">{t('adminImport.row')}</th>
                                            <th className="text-left p-3 text-primary-400 font-medium">{t('adminImport.name')}</th>
                                            <th className="text-left p-3 text-primary-400 font-medium">{t('adminImport.phone')}</th>
                                            <th className="text-left p-3 text-primary-400 font-medium">{t('adminImport.reason')}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {result.errors.map((err, i) => (
                                            <tr key={i} className="border-b border-primary-700/20 last:border-0">
                                                <td className="p-3 text-primary-300 font-mono">{err.row}</td>
                                                <td className="p-3 text-white">{err.name || '—'}</td>
                                                <td className="p-3 text-primary-300 font-mono">{err.phone || '—'}</td>
                                                <td className="p-3 text-red-400">{err.reason}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    )
}
