import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { DatePicker } from '@mui/x-date-pickers/DatePicker'
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFnsV3'
import { parseISO, format } from 'date-fns'

const DATE_PICKER_SX = {
    '& .MuiOutlinedInput-root': {
        backgroundColor: 'rgba(13,27,75,0.6)', borderRadius: '12px', color: '#f0f4ff',
        fontFamily: '"DM Sans", system-ui, sans-serif', fontSize: '0.875rem',
        '& fieldset': { borderColor: 'rgba(32,53,163,0.4)' },
        '&:hover fieldset': { borderColor: 'rgba(251,191,36,0.4)' },
        '&.Mui-focused fieldset': { borderColor: 'rgba(245,158,11,0.6)', boxShadow: '0 0 0 2px rgba(245,158,11,0.1)' },
    },
    '& .MuiInputAdornment-root .MuiIconButton-root': { color: '#6080f0', '&:hover': { color: '#fbbf24' } },
    '& .MuiInputBase-input': { padding: '12px 16px', color: '#f0f4ff' },
}

const DATE_PICKER_POPPER_SX = {
    '& .MuiPaper-root': { backgroundColor: '#1c2e84', border: '1px solid rgba(32,53,163,0.3)', borderRadius: '12px', color: '#f0f4ff' },
    '& .MuiPickersDay-root': {
        color: '#8aa6f8', backgroundColor: 'transparent',
        '&:hover': { backgroundColor: 'rgba(245,158,11,0.15)' },
        '&.Mui-selected': { backgroundColor: '#f59e0b', color: '#1a2a68', '&:hover': { backgroundColor: '#fbbf24' } },
    },
    '& .MuiPickersCalendarHeader-root': { color: '#f0f4ff' },
    '& .MuiPickersArrowSwitcher-button': { color: '#6080f0', '&:hover': { color: '#fbbf24' } },
    '& .MuiDayCalendar-weekDayLabel': { color: '#6080f0' },
    '& .MuiPickersYear-yearButton.Mui-selected': { backgroundColor: '#f59e0b', color: '#1a2a68' },
}

const ROWS = ['A', 'B', 'C', 'D']

export default function AdminSeatsPage() {
    const [seatMap, setSeatMap]   = useState(null)
    const [loading, setLoading]   = useState(true)
    const [shift, setShift]       = useState('FULL_DAY')
    const [date, setDate]         = useState(new Date().toISOString().split('T')[0])
    const [selected, setSelected] = useState(null)
    const { t } = useTranslation()

    const fetchMap = async () => {
        setLoading(true)
        try { const res = await api.get(`/admin/seats/map?shift=${shift}&date=${date}`); setSeatMap(res.data.data) }
        catch { toast.error(t('adminSeats.loadFailed')) }
        finally { setLoading(false) }
    }

    useEffect(() => { fetchMap() }, [shift, date])

    const occupied = seatMap?.occupiedSeats ?? 0
    const total    = seatMap?.totalSeats ?? 110
    const pct      = Math.round((occupied / total) * 100)

    const shiftLabel = (s) => {
        if (s === 'MORNING')  return t('adminSeats.shifts.MORNING')
        if (s === 'EVENING')  return t('adminSeats.shifts.EVENING')
        return t('adminSeats.shifts.FULL_DAY')
    }

    return (
        <div>
            <div className="mb-6">
                <h1 className="page-header">{t('adminSeats.title')}</h1>
                <p className="text-primary-400">{t('adminSeats.subtitle')}</p>
            </div>

            <div className="flex flex-wrap gap-3 mb-6">
                {['MORNING','EVENING','FULL_DAY'].map(s => (
                    <button key={s} onClick={() => setShift(s)}
                            className={`px-4 py-2 rounded-xl text-sm font-medium border transition-all
              ${shift === s ? 'bg-red-500/20 border-red-400/60 text-red-400' : 'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                        {s === 'MORNING' ? `🌅 ${t('adminSeats.shifts.MORNING')}` : s === 'EVENING' ? `🌆 ${t('adminSeats.shifts.EVENING')}` : `🌟 ${t('adminSeats.shifts.FULL_DAY')}`}
                    </button>
                ))}
                <LocalizationProvider dateAdapter={AdapterDateFns}>
                    <DatePicker
                        value={date ? parseISO(date) : null}
                        onChange={(d) => setDate(d ? format(d, 'yyyy-MM-dd') : date)}
                        sx={{ width: 180, ...DATE_PICKER_SX }}
                        slotProps={{ textField: { size: 'small' }, popper: { sx: DATE_PICKER_POPPER_SX } }}
                    />
                </LocalizationProvider>
                <button onClick={fetchMap} className="px-4 py-2 rounded-xl text-sm bg-primary-700/50 text-primary-300 hover:text-white border border-primary-700/40 transition-all">↻ {t('adminSeats.refresh')}</button>
            </div>

            <div className="grid grid-cols-3 gap-4 mb-6">
                {[
                    { l: t('adminSeats.stats.total'),     v: total,            color: 'text-white' },
                    { l: t('adminSeats.stats.occupied'),  v: occupied,         color: 'text-red-400' },
                    { l: t('adminSeats.stats.available'), v: total - occupied, color: 'text-emerald-400' },
                ].map(({ l, v, color }) => (
                    <div key={l} className="card p-4 text-center">
                        <p className={`text-2xl font-bold ${color}`}>{v}</p>
                        <p className="text-primary-400 text-sm">{l}</p>
                    </div>
                ))}
            </div>

            <div className="card p-4 mb-6">
                <div className="flex justify-between text-sm mb-2">
                    <span className="text-primary-400">{t('adminSeats.occupancy')}</span>
                    <span className="text-white font-semibold">{pct}%</span>
                </div>
                <div className="h-3 bg-primary-800 rounded-full overflow-hidden">
                    <div className="h-full bg-gradient-to-r from-emerald-500 to-red-500 rounded-full transition-all duration-500" style={{ width: `${pct}%` }} />
                </div>
            </div>

            {loading ? (
                <div className="card p-8"><div className="shimmer w-full h-64 rounded-xl" /></div>
            ) : seatMap ? (
                <div className="card p-6 overflow-x-auto">
                    <div className="flex justify-center mb-6">
                        <div className="px-8 py-2 rounded-lg bg-primary-700/40 border border-primary-600/30 text-primary-400 text-xs tracking-widest uppercase">
                            {t('adminSeats.entrance')}
                        </div>
                    </div>
                    <div className="space-y-3 min-w-[580px]">
                        {ROWS.map(row => {
                            const seats = seatMap.seatsByRow?.[row] || []
                            const half  = Math.ceil(seats.length / 2)
                            const left  = seats.slice(0, half)
                            const right = seats.slice(half)
                            return (
                                <div key={row} className="flex items-center gap-3">
                                    <span className="text-primary-400 font-mono text-sm w-5 text-center">{row}</span>
                                    <div className="flex gap-1">
                                        {left.map(seat => (
                                            <button key={seat.seatNumber}
                                                    onClick={() => setSelected(seat.isOccupied ? seat : null)}
                                                    title={seat.isOccupied ? `${seat.studentName} — ${shiftLabel(seat.shift)}` : `${seat.seatNumber} (${t('adminSeats.legend.available')})`}
                                                    className={`w-8 h-8 rounded-lg text-xs font-medium border transition-all
                          ${seat.isOccupied
                                                        ? seat.studentGender === 'Female'
                                                            ? 'bg-fuchsia-500/30 border-fuchsia-500/50 text-fuchsia-300 hover:bg-fuchsia-500/50 cursor-pointer'
                                                            : 'bg-red-500/30 border-red-500/50 text-red-300 hover:bg-red-500/50 cursor-pointer'
                                                        : 'bg-emerald-500/10 border-emerald-500/20 text-emerald-600 cursor-default'}`}>
                                                {seat.seatNumber.substring(1)}
                                            </button>
                                        ))}
                                    </div>
                                    <div className="w-6 flex-shrink-0 flex justify-center"><div className="w-px h-6 bg-primary-700/50" /></div>
                                    <div className="flex gap-1">
                                        {right.map(seat => (
                                            <button key={seat.seatNumber}
                                                    onClick={() => setSelected(seat.isOccupied ? seat : null)}
                                                    title={seat.isOccupied ? `${seat.studentName} — ${shiftLabel(seat.shift)}` : `${seat.seatNumber} (${t('adminSeats.legend.available')})`}
                                                    className={`w-8 h-8 rounded-lg text-xs font-medium border transition-all
                          ${seat.isOccupied
                                                        ? seat.studentGender === 'Female'
                                                            ? 'bg-fuchsia-500/30 border-fuchsia-500/50 text-fuchsia-300 hover:bg-fuchsia-500/50 cursor-pointer'
                                                            : 'bg-red-500/30 border-red-500/50 text-red-300 hover:bg-red-500/50 cursor-pointer'
                                                        : 'bg-emerald-500/10 border-emerald-500/20 text-emerald-600 cursor-default'}`}>
                                                {seat.seatNumber.substring(1)}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            )
                        })}
                    </div>
                    <div className="flex flex-wrap gap-6 mt-6 text-xs text-primary-400">
                        <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-emerald-500/10 border border-emerald-500/20" />{t('adminSeats.legend.available')}</div>
                        <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-red-500/30 border border-red-500/50" />Male occupied</div>
                        <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-fuchsia-500/30 border border-fuchsia-500/50" />Female occupied</div>
                    </div>
                </div>
            ) : null}

            {selected && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setSelected(null)}>
                    <div className="card p-6 w-72 border-red-500/30" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="text-white font-semibold">{t('adminSeats.modal.seat', { seatNumber: selected.seatNumber })}</h3>
                            <button onClick={() => setSelected(null)} className="text-primary-400 hover:text-white">✕</button>
                        </div>
                        <div className="space-y-2">
                            {[
                                { l: t('adminSeats.modal.student'), v: selected.studentName },
                                { l: t('adminSeats.modal.mobile'),  v: selected.studentMobile || '—' },
                                { l: 'Gender',                       v: selected.studentGender || '—' },
                                { l: t('adminSeats.modal.shift'),   v: shiftLabel(selected.shift) },
                                { l: t('adminSeats.modal.expires'), v: selected.membershipEnd },
                            ].map(({ l, v }) => (
                                <div key={l} className="flex justify-between py-2 border-b border-primary-700/30 last:border-0 text-sm">
                                    <span className="text-primary-400">{l}</span>
                                    <span className="text-white">{v}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
