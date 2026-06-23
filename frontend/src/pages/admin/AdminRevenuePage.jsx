import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import * as XLSX from 'xlsx'
import api from '../../services/api'
import toast from 'react-hot-toast'

const MONTHS = [
    'January','February','March','April','May','June',
    'July','August','September','October','November','December'
]

function fmtCompact(n) {
    if (!n || Number(n) === 0) return '—'
    const v = Number(n)
    if (v >= 100000) return `₹${(v / 100000).toFixed(1)}L`
    if (v >= 1000)   return `₹${(v / 1000 % 1 === 0 ? (v / 1000).toFixed(0) : (v / 1000).toFixed(1))}k`
    return `₹${v}`
}

function heatColor(amount, maxAmount) {
    if (!amount || Number(amount) === 0 || !maxAmount) return 'bg-primary-800 text-primary-500'
    const ratio = Number(amount) / Number(maxAmount)
    if (ratio > 0.75) return 'bg-emerald-500 text-white'
    if (ratio > 0.50) return 'bg-emerald-700 text-emerald-100'
    if (ratio > 0.25) return 'bg-emerald-800 text-emerald-200'
    return 'bg-emerald-900 text-emerald-300'
}

export default function AdminRevenuePage() {
    const { t }     = useTranslation()
    const today     = new Date()
    const rowRefs   = useRef({})

    const [year, setYear]               = useState(today.getFullYear())
    const [month, setMonth]             = useState(today.getMonth() + 1)
    const [report, setReport]           = useState(null)
    const [loading, setLoading]         = useState(false)
    const [selectedDay, setSelectedDay] = useState(null)
    const [dayPayments, setDayPayments] = useState({})
    const [dayLoading, setDayLoading]   = useState(false)

    const currency = (n) => `₹${Number(n ?? 0).toLocaleString('en-IN')}`
    const fmt      = (n) => (n ?? 0).toLocaleString('en-IN')

    const exportToExcel = () => {
        const monthName = MONTHS[month - 1]
        const rows = [
            [t('adminRevenue.date'), t('adminRevenue.amount'), t('adminRevenue.transactions')],
            ...(report?.dailyBreakdown ?? []).map(r => [r.date, Number(r.amount), Number(r.count)]),
            [],
            [t('adminRevenue.total'), Number(report?.totalRevenue ?? 0), Number(report?.totalTransactions ?? 0)],
        ]
        const ws = XLSX.utils.aoa_to_sheet(rows)
        const wb = XLSX.utils.book_new()
        XLSX.utils.book_append_sheet(wb, ws, `${monthName} ${year}`)
        XLSX.writeFile(wb, `revenue-${monthName}-${year}.xlsx`)
    }

    const lastDay = new Date(year, month, 0).getDate()

    const fetchReport = async (y, m) => {
        setLoading(true)
        setSelectedDay(null)
        const from = `${y}-${String(m).padStart(2, '0')}-01`
        const to   = `${y}-${String(m).padStart(2, '0')}-${String(new Date(y, m, 0).getDate()).padStart(2, '0')}`
        try {
            const res = await api.get(`/admin/reports/revenue?from=${from}&to=${to}`)
            setReport(res.data.data)
        } catch {
            toast.error(t('adminRevenue.loadFailed'))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => { fetchReport(year, month) }, [year, month])

    // Build lookup: date string → DailyRevenueDto
    const revenueByDate = {}
    if (report?.dailyBreakdown) {
        report.dailyBreakdown.forEach(d => { revenueByDate[d.date] = d })
    }

    const maxDayRevenue = report?.dailyBreakdown?.length
        ? Math.max(...report.dailyBreakdown.map(d => Number(d.amount)))
        : 0

    const handleDayClick = async (dateStr) => {
        if (selectedDay === dateStr) {
            setSelectedDay(null)
            return
        }
        setSelectedDay(dateStr)
        // Scroll the table row into view
        setTimeout(() => {
            rowRefs.current[dateStr]?.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }, 50)
        if (dayPayments[dateStr]) return   // already cached
        setDayLoading(true)
        try {
            const res = await api.get(`/admin/reports/payments/daily?date=${dateStr}`)
            setDayPayments(prev => ({ ...prev, [dateStr]: res.data.data }))
        } catch {
            toast.error(t('adminRevenue.paymentLoadFailed'))
        } finally {
            setDayLoading(false)
        }
    }

    // Heatmap grid: Mon–Sun columns, offset first day correctly
    const firstDayOfWeek = (new Date(year, month - 1, 1).getDay() + 6) % 7  // 0=Mon … 6=Sun
    const totalDays      = lastDay
    const gridCells      = []
    for (let i = 0; i < firstDayOfWeek; i++) gridCells.push(null)
    for (let d = 1; d <= totalDays; d++) gridCells.push(d)

    const dateStr = (d) => `${year}-${String(month).padStart(2,'0')}-${String(d).padStart(2,'0')}`
    const isFuture = (d) => {
        const cell = new Date(year, month - 1, d)
        cell.setHours(23, 59, 59)
        return cell > today
    }

    const yearOptions = []
    for (let y = today.getFullYear() - 3; y <= today.getFullYear(); y++) yearOptions.push(y)

    return (
        <div>
            {/* Header */}
            <div className="flex flex-wrap items-center justify-between gap-4 mb-6">
                <div>
                    <h1 className="page-header">{t('adminRevenue.title')}</h1>
                    <p className="text-primary-400">{t('adminRevenue.subtitle')}</p>
                </div>
                <div className="flex items-center gap-3">
                    <select
                        value={month}
                        onChange={e => setMonth(Number(e.target.value))}
                        className="input text-sm py-2 px-3"
                    >
                        {MONTHS.map((name, i) => (
                            <option key={i+1} value={i+1}>{name}</option>
                        ))}
                    </select>
                    <select
                        value={year}
                        onChange={e => setYear(Number(e.target.value))}
                        className="input text-sm py-2 px-3"
                    >
                        {yearOptions.map(y => <option key={y} value={y}>{y}</option>)}
                    </select>
                    <button
                        onClick={() => fetchReport(year, month)}
                        className="btn-ghost border border-primary-700/40 text-sm px-4 py-2 rounded-xl"
                    >
                        ↻ {t('adminRevenue.refresh')}
                    </button>
                    <button
                        onClick={exportToExcel}
                        disabled={!report?.dailyBreakdown?.length}
                        className="btn-ghost border border-primary-700/40 text-sm px-4 py-2 rounded-xl disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                        ↓ {t('adminRevenue.export')}
                    </button>
                </div>
            </div>

            {/* Summary */}
            <div className="grid grid-cols-2 gap-4 mb-8">
                <div className="card bg-gradient-to-br from-emerald-500/20 to-transparent border-emerald-500/20 p-5">
                    <p className="text-primary-400 text-sm mb-1">{t('adminRevenue.totalRevenue')}</p>
                    <p className="text-3xl font-bold text-emerald-400">{currency(report?.totalRevenue)}</p>
                </div>
                <div className="card bg-gradient-to-br from-amber-500/20 to-transparent border-amber-500/20 p-5">
                    <p className="text-primary-400 text-sm mb-1">{t('adminRevenue.totalTransactions')}</p>
                    <p className="text-3xl font-bold text-amber-400">{fmt(report?.totalTransactions)}</p>
                </div>
            </div>

            {/* Revenue Heatmap */}
            <p className="text-primary-500 text-xs uppercase tracking-widest mb-3">{t('adminRevenue.revenueMap')}</p>
            <div className="card p-4 mb-8">
                {/* Weekday headers */}
                <div className="grid grid-cols-7 gap-1.5 mb-1.5">
                    {['Mon','Tue','Wed','Thu','Fri','Sat','Sun'].map(d => (
                        <div key={d} className="text-center text-primary-500 text-xs py-1">{d}</div>
                    ))}
                </div>
                <div className="grid grid-cols-7 gap-1.5">
                    {gridCells.map((day, i) => {
                        if (!day) return <div key={`empty-${i}`} />
                        const ds   = dateStr(day)
                        const rev  = revenueByDate[ds]
                        const amt  = rev?.amount ?? 0
                        const future = isFuture(day)
                        return (
                            <button
                                key={ds}
                                onClick={() => !future && handleDayClick(ds)}
                                disabled={future}
                                className={`
                                    relative rounded-xl p-1.5 text-center transition-all
                                    ${future
                                        ? 'bg-primary-800/40 opacity-40 cursor-not-allowed'
                                        : `${heatColor(amt, maxDayRevenue)} hover:opacity-80 hover:-translate-y-0.5 cursor-pointer`
                                    }
                                    ${selectedDay === ds ? 'ring-2 ring-amber-400' : ''}
                                `}
                            >
                                <div className="text-[10px] font-mono opacity-70 leading-none mb-0.5">{day}</div>
                                <div className="text-[11px] font-semibold leading-tight">{fmtCompact(amt)}</div>
                            </button>
                        )
                    })}
                </div>
                {/* Legend */}
                <div className="flex items-center gap-3 mt-4 justify-end">
                    <span className="text-primary-500 text-xs">{t('adminRevenue.legend.low')}</span>
                    {['bg-primary-800','bg-emerald-900','bg-emerald-800','bg-emerald-700','bg-emerald-500'].map((c,i) => (
                        <div key={i} className={`w-5 h-3 rounded ${c}`} />
                    ))}
                    <span className="text-primary-500 text-xs">{t('adminRevenue.legend.high')}</span>
                </div>
            </div>

            {/* Day-wise Breakdown Table */}
            <p className="text-primary-500 text-xs uppercase tracking-widest mb-3">{t('adminRevenue.dayBreakdown')}</p>
            <div className="card overflow-hidden mb-8">
                {loading ? (
                    <div className="p-6 space-y-3">
                        {[1,2,3].map(i => <div key={i} className="shimmer h-10 rounded-xl" />)}
                    </div>
                ) : !report?.dailyBreakdown?.length ? (
                    <p className="text-primary-500 text-sm text-center py-10">{t('adminRevenue.noData')}</p>
                ) : (
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="text-primary-500 text-xs uppercase tracking-wider border-b border-primary-700/40">
                                <th className="text-left px-6 py-3">{t('adminRevenue.date')}</th>
                                <th className="text-right px-6 py-3">{t('adminRevenue.amount')}</th>
                                <th className="text-right px-6 py-3">{t('adminRevenue.transactions')}</th>
                                <th className="w-8" />
                            </tr>
                        </thead>
                        <tbody>
                            {report.dailyBreakdown.map(row => {
                                const isOpen = selectedDay === row.date
                                const payments = dayPayments[row.date]
                                return [
                                    <tr
                                        key={row.date}
                                        ref={el => rowRefs.current[row.date] = el}
                                        onClick={() => handleDayClick(row.date)}
                                        className={`border-b border-primary-800/60 cursor-pointer transition-colors ${isOpen ? 'bg-primary-800/50' : 'hover:bg-primary-800/30'}`}
                                    >
                                        <td className="px-6 py-3 text-primary-300 font-mono">{row.date}</td>
                                        <td className="px-6 py-3 text-amber-400 font-semibold text-right">{currency(row.amount)}</td>
                                        <td className="px-6 py-3 text-primary-400 text-right">{fmt(row.count)}</td>
                                        <td className="pr-4 text-primary-500 text-xs text-right">{isOpen ? '▼' : '→'}</td>
                                    </tr>,
                                    isOpen && (
                                        <tr key={`${row.date}-detail`} className="bg-primary-900/60">
                                            <td colSpan={4} className="px-4 pb-4 pt-2">
                                                {dayLoading && !payments ? (
                                                    <div className="flex items-center gap-2 py-4 px-2">
                                                        <div className="w-4 h-4 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
                                                        <span className="text-primary-400 text-xs">Loading…</span>
                                                    </div>
                                                ) : !payments?.length ? (
                                                    <p className="text-primary-500 text-xs py-4 px-2">{t('adminRevenue.noPayments')}</p>
                                                ) : (
                                                    <div className="rounded-xl overflow-hidden border border-primary-700/30">
                                                        <table className="w-full text-xs">
                                                            <thead>
                                                                <tr className="text-primary-500 uppercase tracking-wider border-b border-primary-700/30 bg-primary-800/40">
                                                                    <th className="text-left px-4 py-2">{t('adminRevenue.student')}</th>
                                                                    <th className="text-left px-4 py-2">{t('adminRevenue.mobile')}</th>
                                                                    <th className="text-right px-4 py-2">{t('adminRevenue.amount')}</th>
                                                                    <th className="text-left px-4 py-2">{t('adminRevenue.mode')}</th>
                                                                    <th className="text-left px-4 py-2">{t('adminRevenue.referenceId')}</th>
                                                                </tr>
                                                            </thead>
                                                            <tbody>
                                                                {payments.map((p, idx) => (
                                                                    <tr key={idx} className={idx < payments.length - 1 ? 'border-b border-primary-700/20' : ''}>
                                                                        <td className="px-4 py-2 text-white font-medium">{p.studentName}</td>
                                                                        <td className="px-4 py-2 text-primary-300 font-mono">{p.studentMobile}</td>
                                                                        <td className="px-4 py-2 text-amber-400 font-semibold text-right">{currency(p.amount)}</td>
                                                                        <td className="px-4 py-2 text-primary-300">{p.paymentGateway ?? '—'}</td>
                                                                        <td className="px-4 py-2 text-primary-400 font-mono truncate max-w-[160px]">{p.referenceId ?? '—'}</td>
                                                                    </tr>
                                                                ))}
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                )}
                                            </td>
                                        </tr>
                                    )
                                ]
                            })}
                        </tbody>
                    </table>
                )}
            </div>
        </div>
    )
}
