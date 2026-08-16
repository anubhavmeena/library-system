import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'

const PAGE_SIZE_OPTIONS = [25, 50, 100, 'all']

export default function AdminActivityLogsPage() {
    const { t } = useTranslation()
    const [logs, setLogs]         = useState([])
    const [total, setTotal]       = useState(0)
    const [loading, setLoading]   = useState(true)
    const [page, setPage]         = useState(0)
    const [pageSize, setPageSize] = useState(100)

    const fetchLogs = () => {
        setLoading(true)
        api.get(`/admin/activity-logs?page=${page}&size=${pageSize}`)
            .then(res => {
                setLogs(res.data.data.logs || [])
                setTotal(res.data.data.total || 0)
            })
            .catch(() => toast.error(t('adminActivityLogs.toasts.loadFailed')))
            .finally(() => setLoading(false))
    }

    useEffect(() => { fetchLogs() }, [page, pageSize])

    const handlePageSizeChange = (value) => {
        setPageSize(value === 'all' ? 'all' : Number(value))
        setPage(0)
    }

    return (
        <div>
            <div className="flex items-start justify-between mb-6 gap-4">
                <div>
                    <h1 className="page-header">{t('adminActivityLogs.title')}</h1>
                    <p className="text-primary-400 mt-1">{t('adminActivityLogs.subtitle', { count: total })}</p>
                </div>
                <button onClick={fetchLogs}
                    className="btn-ghost border border-primary-700/40 text-sm px-4 py-2 rounded-xl flex-shrink-0">
                    ↻ {t('adminActivityLogs.refresh')}
                </button>
            </div>

            {loading ? (
                <div className="space-y-3">
                    {[1, 2, 3, 4, 5].map(i => <div key={i} className="shimmer h-14 rounded-xl" />)}
                </div>
            ) : logs.length === 0 ? (
                <div className="card p-12 text-center">
                    <p className="text-4xl mb-3">📋</p>
                    <p className="text-white font-semibold">{t('adminActivityLogs.empty.title')}</p>
                    <p className="text-primary-400 text-sm mt-1">{t('adminActivityLogs.empty.desc')}</p>
                </div>
            ) : (
                <div className="card overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-primary-700/40">
                                    {[
                                        t('adminActivityLogs.table.date'),
                                        t('adminActivityLogs.table.admin'),
                                        t('adminActivityLogs.table.action'),
                                        t('adminActivityLogs.table.description'),
                                    ].map(h => (
                                        <th key={h} className="p-4 text-left text-primary-400 font-medium whitespace-nowrap">{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-primary-700/20">
                                {logs.map(entry => (
                                    <tr key={entry.id} className="hover:bg-primary-800/30 transition-colors">
                                        <td className="p-4 text-primary-400 text-xs whitespace-nowrap align-top">
                                            {entry.createdAt?.replace('T', ' ').slice(0, 19)}
                                        </td>
                                        <td className="p-4 text-white font-medium whitespace-nowrap align-top">
                                            {entry.adminName}
                                        </td>
                                        <td className="p-4 align-top">
                                            <span className="text-xs px-2 py-1 rounded-full border font-medium whitespace-nowrap bg-red-500/20 text-red-400 border-red-500/30">
                                                {entry.action}
                                            </span>
                                        </td>
                                        <td className="p-4 text-primary-200">
                                            {entry.description}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <div className="flex items-center justify-between p-4 border-t border-primary-700/30">
                        <div className="flex items-center gap-3">
                            <span className="text-primary-400 text-sm">{t('adminActivityLogs.page', { page: page + 1 })}</span>
                            <span className="text-primary-500 text-xs">{t('adminActivityLogs.perPage')}</span>
                            <select value={pageSize} onChange={e => handlePageSizeChange(e.target.value)}
                                    className="input text-sm py-1 w-24">
                                {PAGE_SIZE_OPTIONS.map(n => (
                                    <option key={n} value={n}>{n === 'all' ? t('adminActivityLogs.all') : n}</option>
                                ))}
                            </select>
                        </div>
                        <div className="flex gap-2">
                            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={pageSize === 'all' || page === 0}
                                    className="btn-ghost disabled:opacity-40 text-sm px-3 py-1.5 border border-primary-700/40 rounded-lg">← {t('adminActivityLogs.prev')}</button>
                            <button onClick={() => setPage(p => p + 1)} disabled={pageSize === 'all' || (page + 1) * pageSize >= total}
                                    className="btn-ghost disabled:opacity-40 text-sm px-3 py-1.5 border border-primary-700/40 rounded-lg">{t('adminActivityLogs.next')} →</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
