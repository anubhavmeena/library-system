import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

export default function AdminCronJobsPage() {
    const { t } = useTranslation()
    const [running, setRunning]       = useState(false)
    const [lastResult, setLastResult] = useState(null)

    const handleRun = async () => {
        setRunning(true)
        try {
            const res = await api.post('/admin/memberships/run-expiry-check')
            setLastResult(res.data.data)
            toast.success(res.data.data)
        } catch {
            toast.error(t('adminCronJobs.toasts.failed'))
        } finally {
            setRunning(false)
        }
    }

    return (
        <div className="max-w-2xl">
            <div className="mb-6">
                <h1 className="page-header">{t('adminCronJobs.title')}</h1>
                <p className="text-primary-400">{t('adminCronJobs.subtitle')}</p>
            </div>

            <div className="card p-6 space-y-5">
                <div>
                    <p className="text-white text-sm font-semibold">{t('adminCronJobs.expiryCheck.title')}</p>
                    <p className="text-primary-400 text-xs mt-1">{t('adminCronJobs.expiryCheck.desc')}</p>
                </div>

                <button
                    onClick={handleRun}
                    disabled={running}
                    className="btn-primary px-6 py-2.5 text-sm disabled:opacity-40 disabled:cursor-not-allowed">
                    {running ? t('adminCronJobs.running') : t('adminCronJobs.runNow')}
                </button>

                {lastResult !== null && (
                    <div className="flex items-center gap-2 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-sm">
                        <span className="text-emerald-400">✓</span>
                        <span className="text-emerald-300">{lastResult}</span>
                    </div>
                )}
            </div>
        </div>
    )
}
