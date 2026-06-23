import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import api from '../../services/api'
import toast from 'react-hot-toast'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { DatePicker } from '@mui/x-date-pickers/DatePicker'
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFnsV3'
import { parseISO, format, addDays } from 'date-fns'

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

const ROWS          = ['A', 'B', 'C', 'D']
const INACTIVE_SEATS = new Set(['B8', 'B18'])
const L_TOP    = [13, 11, 9, 7, 5, 3, 1]
const L_BOTTOM = [14, 12, 10, 8, 6, 4, 2]
const R_TOP    = [15, 17, 19, 21, 23, 25, 27]
const R_BOTTOM = [16, 18, 20, 22, 24, 26, 28]
const TODAY = new Date().toISOString().split('T')[0]

export default function AdminCreateMembershipPage() {
    const { t } = useTranslation()
    const navigate = useNavigate()

    const [step, setStep] = useState(1)

    // step 1
    const [students, setStudents]             = useState([])
    const [studentsLoading, setStudentsLoading] = useState(true)
    const [search, setSearch]                 = useState('')
    const [selectedStudent, setSelectedStudent] = useState(null)

    // step 2
    const [plans, setPlans]             = useState([])
    const [plansLoading, setPlansLoading] = useState(true)
    const [selectedPlan, setSelectedPlan] = useState(null)
    const [selectedShift, setSelectedShift] = useState('')
    const [startDate, setStartDate]     = useState(TODAY)

    // step 3
    const [seatData, setSeatData]   = useState(null)
    const [seatsLoading, setSeatsLoading] = useState(false)
    const [selectedSeat, setSelectedSeat] = useState(null)

    // step 4
    const [paidAmount,    setPaidAmount]    = useState('')
    const [pendingAmount, setPendingAmount] = useState('0')
    const [cashConfirmed, setCashConfirmed] = useState(false)
    const [submitting, setSubmitting]       = useState(false)

    useEffect(() => {
        api.get('/admin/students?page=0&size=200')
            .then(r => setStudents(r.data.data?.students || []))
            .catch(() => toast.error(t('adminNewMembership.toasts.loadStudentsFailed')))
            .finally(() => setStudentsLoading(false))
    }, [])

    useEffect(() => {
        api.get('/plans')
            .then(r => setPlans(r.data.data || []))
            .catch(() => toast.error(t('adminNewMembership.toasts.loadPlansFailed')))
            .finally(() => setPlansLoading(false))
    }, [])

    useEffect(() => {
        if (selectedPlan) { setPaidAmount(String(selectedPlan.price)); setPendingAmount('0') }
    }, [selectedPlan])

    useEffect(() => {
        if (step !== 3 || !selectedPlan) return
        const shift = selectedPlan.planType === 'FULL_DAY' ? 'FULL_DAY' : selectedShift
        if (!shift) return
        setSeatsLoading(true)
        setSelectedSeat(null)
        api.get(`/seats/availability?shift=${shift}&date=${startDate}`)
            .then(r => setSeatData(r.data.data))
            .catch(() => toast.error(t('adminNewMembership.toasts.loadSeatsFailed')))
            .finally(() => setSeatsLoading(false))
    }, [step, selectedPlan, selectedShift, startDate])

    const endDate = selectedPlan && startDate
        ? format(addDays(parseISO(startDate), selectedPlan.durationDays), 'yyyy-MM-dd')
        : '—'

    const resolvedShift = selectedPlan?.planType === 'FULL_DAY' ? 'FULL_DAY' : selectedShift

    const filteredStudents = students.filter(s =>
        !search ||
        s.name?.toLowerCase().includes(search.toLowerCase()) ||
        s.mobile?.includes(search) ||
        s.email?.toLowerCase().includes(search.toLowerCase())
    )

    const canGoStep3 = selectedPlan && startDate &&
        (selectedPlan.planType === 'FULL_DAY' || selectedShift)

    const handleSubmit = async () => {
        setSubmitting(true)
        try {
            await api.post('/admin/memberships/cash', {
                studentId:     selectedStudent.id,
                planId:        selectedPlan.id,
                shift:         resolvedShift,
                seatNumber:    selectedSeat.seatNumber,
                startDate,
                paidAmount:    parseFloat(paidAmount)    || selectedPlan.price,
                pendingAmount: parseFloat(pendingAmount) || 0,
            })
            toast.success(t('adminNewMembership.toasts.created'))
            navigate('/admin/students')
        } catch (e) {
            toast.error(e.response?.data?.message || t('adminNewMembership.toasts.createFailed'))
        } finally {
            setSubmitting(false)
        }
    }

    const STEPS = [
        t('adminNewMembership.steps.student'),
        t('adminNewMembership.steps.plan'),
        t('adminNewMembership.steps.seat'),
        t('adminNewMembership.steps.confirm'),
    ]

    return (
        <div>
            <div className="mb-6">
                <h1 className="page-header">{t('adminNewMembership.title')}</h1>
                <p className="text-primary-400">{t('adminNewMembership.subtitle')}</p>
            </div>

            {/* Step indicator */}
            <div className="flex items-center gap-2 mb-8">
                {STEPS.map((label, i) => {
                    const n = i + 1
                    const done    = step > n
                    const current = step === n
                    return (
                        <div key={n} className="flex items-center gap-2">
                            <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold border transition-all
                                ${done    ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-400'
                                : current ? 'bg-red-500/20 border-red-500/40 text-red-400'
                                          : 'bg-primary-800/60 border-primary-700/30 text-primary-500'}`}>
                                {done ? '✓' : n}
                            </div>
                            <span className={`text-sm font-medium hidden sm:block
                                ${done    ? 'text-emerald-400'
                                : current ? 'text-red-400'
                                          : 'text-primary-500'}`}>{label}</span>
                            {i < STEPS.length - 1 && (
                                <div className={`w-8 h-px ${done ? 'bg-emerald-500/40' : 'bg-primary-700/30'}`} />
                            )}
                        </div>
                    )
                })}
            </div>

            {/* ─── Step 1: Find Student ─── */}
            {step === 1 && (
                <div className="card p-6">
                    <h2 className="section-title mb-4">{t('adminNewMembership.step1.title')}</h2>
                    <input
                        className="input w-full mb-4 text-sm"
                        placeholder={t('adminNewMembership.step1.searchPlaceholder')}
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                    />
                    {studentsLoading ? (
                        <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="shimmer h-16 rounded-xl" />)}</div>
                    ) : filteredStudents.length === 0 ? (
                        <p className="text-primary-500 text-center py-8">{t('adminNewMembership.step1.empty')}</p>
                    ) : (
                        <div className="space-y-2 max-h-96 overflow-y-auto pr-1">
                            {filteredStudents.map(s => (
                                <button
                                    key={s.id}
                                    onClick={() => { setSelectedStudent(s); setStep(2) }}
                                    className="w-full flex items-center gap-4 p-4 rounded-xl border border-primary-700/30 hover:border-red-500/40 hover:bg-red-500/5 transition-all text-left">
                                    <div className="w-10 h-10 rounded-full bg-gradient-to-br from-red-400 to-primary-600 flex items-center justify-center text-sm font-bold text-white flex-shrink-0">
                                        {s.name?.[0]?.toUpperCase() || '?'}
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-white font-medium truncate">{s.name}</p>
                                        <p className="text-primary-400 text-xs">{s.mobile || s.email || '—'}</p>
                                    </div>
                                    <span className={`text-xs px-2 py-1 rounded-full border flex-shrink-0
                                        ${s.membershipEnd
                                            ? 'bg-amber-500/20 text-amber-400 border-amber-500/30'
                                            : 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30'}`}>
                                        {s.membershipEnd ? t('adminNewMembership.hasMembership') : t('adminNewMembership.noPlan')}
                                    </span>
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* ─── Step 2: Plan + Shift + Date ─── */}
            {step === 2 && (
                <div className="space-y-6">
                    <div className="card p-4 flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-gradient-to-br from-red-400 to-primary-600 flex items-center justify-center text-sm font-bold text-white">
                            {selectedStudent?.name?.[0]?.toUpperCase()}
                        </div>
                        <div>
                            <p className="text-white font-semibold">{selectedStudent?.name}</p>
                            <p className="text-primary-400 text-xs">{selectedStudent?.mobile || selectedStudent?.email}</p>
                        </div>
                        <button onClick={() => { setStep(1); setSelectedStudent(null) }}
                                className="ml-auto text-primary-500 text-xs hover:text-primary-300">
                            {t('adminNewMembership.change')}
                        </button>
                    </div>

                    <div className="card p-6">
                        <h2 className="section-title mb-4">{t('adminNewMembership.step2.selectPlan')}</h2>
                        {plansLoading ? (
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                {[1,2].map(i => <div key={i} className="shimmer h-28 rounded-xl" />)}
                            </div>
                        ) : (
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                                {plans.map(p => (
                                    <button
                                        key={p.id}
                                        onClick={() => { setSelectedPlan(p); setSelectedShift('') }}
                                        className={`p-5 rounded-xl border text-left transition-all
                                            ${selectedPlan?.id === p.id
                                                ? 'bg-red-500/15 border-red-500/50 text-white'
                                                : 'border-primary-700/30 text-primary-300 hover:border-red-500/30 hover:bg-red-500/5'}`}>
                                        <p className="font-semibold text-base mb-1">{p.name}</p>
                                        <p className="text-amber-400 text-lg font-bold">₹{p.price}</p>
                                        <p className="text-xs text-primary-500 mt-1">
                                            {p.durationDays} {t('adminNewMembership.step2.days')} · {p.planType === 'FULL_DAY' ? t('adminNewMembership.step2.fullDay') : t('adminNewMembership.step2.halfDay')}
                                        </p>
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>

                    {selectedPlan?.planType === 'HALF_DAY' && (
                        <div className="card p-6">
                            <h2 className="section-title mb-4">{t('adminNewMembership.step2.selectShift')}</h2>
                            <div className="flex gap-3">
                                {['MORNING', 'EVENING'].map(s => (
                                    <button key={s}
                                        onClick={() => setSelectedShift(s)}
                                        className={`flex-1 py-3 rounded-xl border font-medium text-sm transition-all
                                            ${selectedShift === s
                                                ? 'bg-red-500/20 border-red-500/50 text-red-400'
                                                : 'border-primary-700/30 text-primary-400 hover:border-red-500/30'}`}>
                                        {s === 'MORNING' ? `🌅 ${t('adminNewMembership.shifts.MORNING')}` : `🌆 ${t('adminNewMembership.shifts.EVENING')}`}
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}

                    <div className="card p-6">
                        <h2 className="section-title mb-4">{t('adminNewMembership.step2.startDate')}</h2>
                        <div className="flex flex-wrap items-center gap-4">
                            <LocalizationProvider dateAdapter={AdapterDateFns}>
                                <DatePicker
                                    value={startDate ? parseISO(startDate) : null}
                                    onChange={d => d && setStartDate(format(d, 'yyyy-MM-dd'))}
                                    minDate={parseISO(TODAY)}
                                    sx={{ width: 200, ...DATE_PICKER_SX }}
                                    slotProps={{ textField: { size: 'small' }, popper: { sx: DATE_PICKER_POPPER_SX } }}
                                />
                            </LocalizationProvider>
                            {selectedPlan && (
                                <div className="text-sm text-primary-400">
                                    {t('adminNewMembership.step2.endDateLabel')}
                                    <span className="text-white font-medium ml-1">{endDate}</span>
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="flex gap-3">
                        <button onClick={() => setStep(1)} className="btn-ghost border border-primary-700/40 px-6 py-2.5 rounded-xl text-sm">
                            ← {t('adminNewMembership.back')}
                        </button>
                        <button
                            onClick={() => setStep(3)}
                            disabled={!canGoStep3}
                            className="btn-primary px-6 py-2.5 text-sm disabled:opacity-40 disabled:cursor-not-allowed">
                            {t('adminNewMembership.next')} →
                        </button>
                    </div>
                </div>
            )}

            {/* ─── Step 3: Seat Selection ─── */}
            {step === 3 && (
                <div className="space-y-6">
                    <div className="card p-4 grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
                        <div><p className="text-primary-500 text-xs">{t('adminNewMembership.summary.student')}</p><p className="text-white font-medium truncate">{selectedStudent?.name}</p></div>
                        <div><p className="text-primary-500 text-xs">{t('adminNewMembership.summary.plan')}</p><p className="text-white font-medium">{selectedPlan?.name}</p></div>
                        <div><p className="text-primary-500 text-xs">{t('adminNewMembership.summary.shift')}</p><p className="text-white font-medium">{resolvedShift}</p></div>
                        <div><p className="text-primary-500 text-xs">{t('adminNewMembership.summary.dates')}</p><p className="text-white font-medium">{startDate} → {endDate}</p></div>
                    </div>

                    <div className="card p-6">
                        <h2 className="section-title mb-4">{t('adminNewMembership.step3.title')}</h2>
                        {seatsLoading ? (
                            <div className="shimmer h-64 rounded-xl" />
                        ) : seatData ? (
                            <div className="overflow-x-auto">
                                <div className="min-w-[640px]">
                                    <div className="flex gap-2 mb-1">
                                        <div className="w-5 flex-shrink-0" />
                                        <div className="invisible pointer-events-none flex gap-1">
                                            {L_TOP.map(n => <div key={n} className="w-8 h-0" />)}
                                        </div>
                                        <div className="w-6 flex-shrink-0 flex justify-center">
                                            <span className="text-primary-400 text-[10px] tracking-widest uppercase">ENTRY</span>
                                        </div>
                                    </div>
                                    <div className="space-y-7">
                                        {ROWS.map(row => {
                                            const rowSeats = seatData.seatsByRow?.[row] || []
                                            const find = sn => rowSeats.find(s => s.seatNumber === sn)
                                            const renderSeat = n => {
                                                const sn = `${row}${n}`
                                                if (INACTIVE_SEATS.has(sn)) {
                                                    return <div key={sn} className="w-8 h-8 rounded-lg bg-primary-900/50 border border-primary-800/20" title="Blocked" />
                                                }
                                                const s = find(sn)
                                                if (!s) return <div key={sn} className="w-8 h-8 rounded-lg bg-primary-900/40 border border-primary-800/20" />
                                                const isSelected = selectedSeat?.seatNumber === s.seatNumber
                                                return (
                                                    <button key={sn}
                                                        disabled={s.isBooked}
                                                        onClick={() => setSelectedSeat(s)}
                                                        title={s.isBooked ? `${sn} (${t('adminNewMembership.step3.booked')})` : sn}
                                                        className={`w-8 h-8 rounded-lg text-xs font-medium border transition-all
                                                            ${isSelected
                                                                ? 'seat-selected bg-amber-400/30 border-amber-400/70 text-amber-300'
                                                                : s.isBooked
                                                                    ? 'bg-red-500/30 border-red-500/50 text-red-400 cursor-not-allowed opacity-60'
                                                                    : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/25 cursor-pointer'}`}>
                                                        {sn.substring(1)}
                                                    </button>
                                                )
                                            }
                                            return (
                                                <div key={row} className="flex gap-2">
                                                    <span className="text-primary-400 font-mono text-sm w-5 text-center self-start pt-2">{row}</span>
                                                    <div>
                                                        <div className="flex gap-1">{L_TOP.map(renderSeat)}</div>
                                                        <div className="border-b border-primary-700/40 my-1" />
                                                        <div className="flex gap-1">{L_BOTTOM.map(renderSeat)}</div>
                                                    </div>
                                                    <div className="w-6 flex-shrink-0 relative">
                                                        <div className="absolute inset-y-0 left-1/2 w-px bg-primary-700/30 -translate-x-1/2" />
                                                    </div>
                                                    <div>
                                                        <div className="flex gap-1">{R_TOP.map(renderSeat)}</div>
                                                        <div className="border-b border-primary-700/40 my-1" />
                                                        <div className="flex gap-1">{R_BOTTOM.map(renderSeat)}</div>
                                                    </div>
                                                </div>
                                            )
                                        })}
                                    </div>
                                    <div className="flex gap-2 mt-3 text-[10px] tracking-widest uppercase text-primary-600">
                                        <div className="w-5 flex-shrink-0" />
                                        <div className="flex gap-1">
                                            <div className="px-2 py-1 rounded border border-primary-800/30 bg-primary-900/40">EXIT</div>
                                            <div className="px-2 py-1 rounded border border-primary-800/30 bg-primary-900/40">RO / PANTRY</div>
                                            <div className="px-2 py-1 rounded border border-primary-800/30 bg-primary-900/40">WASHROOM</div>
                                        </div>
                                    </div>
                                </div>
                                <div className="flex gap-6 mt-4 text-xs text-primary-400">
                                    <div className="flex items-center gap-2"><div className="w-3 h-3 rounded bg-emerald-500/10 border border-emerald-500/30" />{t('adminNewMembership.step3.available')}</div>
                                    <div className="flex items-center gap-2"><div className="w-3 h-3 rounded bg-red-500/30 border border-red-500/50" />{t('adminNewMembership.step3.booked')}</div>
                                    <div className="flex items-center gap-2"><div className="w-3 h-3 rounded bg-amber-400/30 border border-amber-400/70" />{t('adminNewMembership.step3.selected')}</div>
                                </div>
                                {selectedSeat && (
                                    <p className="mt-3 text-sm text-amber-400 font-medium">
                                        {t('adminNewMembership.step3.selectedSeat', { seat: selectedSeat.seatNumber })}
                                    </p>
                                )}
                            </div>
                        ) : null}
                    </div>

                    <div className="flex gap-3">
                        <button onClick={() => { setStep(2); setSelectedSeat(null) }} className="btn-ghost border border-primary-700/40 px-6 py-2.5 rounded-xl text-sm">
                            ← {t('adminNewMembership.back')}
                        </button>
                        <button
                            onClick={() => setStep(4)}
                            disabled={!selectedSeat}
                            className="btn-primary px-6 py-2.5 text-sm disabled:opacity-40 disabled:cursor-not-allowed">
                            {t('adminNewMembership.next')} →
                        </button>
                    </div>
                </div>
            )}

            {/* ─── Step 4: Review & Confirm ─── */}
            {step === 4 && (
                <div className="space-y-6">
                    <div className="card p-6">
                        <h2 className="section-title mb-5">{t('adminNewMembership.step4.title')}</h2>
                        <div className="space-y-3">
                            {[
                                { l: t('adminNewMembership.summary.student'),  v: `${selectedStudent?.name} (${selectedStudent?.mobile || selectedStudent?.email})` },
                                { l: t('adminNewMembership.summary.plan'),     v: selectedPlan?.name },
                                { l: t('adminNewMembership.summary.shift'),    v: resolvedShift },
                                { l: t('adminNewMembership.summary.seat'),     v: selectedSeat?.seatNumber },
                                { l: t('adminNewMembership.summary.start'),    v: startDate },
                                { l: t('adminNewMembership.summary.end'),      v: endDate },
                                { l: t('adminNewMembership.summary.amount'),   v: `₹${selectedPlan?.price}` },
                            ].map(({ l, v }) => (
                                <div key={l} className="flex justify-between py-2 border-b border-primary-700/20 last:border-0 text-sm">
                                    <span className="text-primary-400">{l}</span>
                                    <span className="text-white font-medium">{v}</span>
                                </div>
                            ))}

                            {/* Paid / Pending amount — editable */}
                            <div className="flex justify-between items-center py-2 border-b border-primary-700/20 text-sm gap-4">
                                <span className="text-primary-400 shrink-0">Paid Amount</span>
                                <div className="flex items-center gap-1">
                                    <span className="text-primary-400">₹</span>
                                    <input
                                        type="number" min="0" step="1"
                                        className="input text-sm py-0.5 w-28 text-right"
                                        value={paidAmount}
                                        onChange={e => {
                                            setPaidAmount(e.target.value)
                                            const paid    = parseFloat(e.target.value) || 0
                                            const pending = Math.max(0, (selectedPlan?.price || 0) - paid)
                                            setPendingAmount(String(pending))
                                        }}
                                    />
                                </div>
                            </div>
                            <div className="flex justify-between items-center py-2 border-b border-primary-700/20 text-sm gap-4">
                                <span className="text-primary-400 shrink-0">Pending Amount</span>
                                <div className="flex items-center gap-1">
                                    <span className="text-primary-400">₹</span>
                                    <input
                                        type="number" min="0" step="1"
                                        className={`input text-sm py-0.5 w-28 text-right ${parseFloat(pendingAmount) > 0 ? 'text-red-400' : ''}`}
                                        value={pendingAmount}
                                        onChange={e => setPendingAmount(e.target.value)}
                                    />
                                </div>
                            </div>

                            <div className="flex justify-between py-2 text-sm">
                                <span className="text-primary-400">{t('adminNewMembership.summary.payment')}</span>
                                <span className="px-3 py-0.5 rounded-full bg-amber-500/20 border border-amber-500/30 text-amber-400 text-xs font-medium">
                                    💵 {t('adminNewMembership.summary.cash')}
                                </span>
                            </div>
                        </div>
                    </div>

                    <div className="card p-5 border-amber-500/20">
                        <label className="flex items-start gap-3 cursor-pointer select-none">
                            <div className="relative mt-0.5">
                                <input
                                    type="checkbox"
                                    checked={cashConfirmed}
                                    onChange={e => setCashConfirmed(e.target.checked)}
                                    className="sr-only"
                                />
                                <div className={`w-5 h-5 rounded border-2 flex items-center justify-center transition-all
                                    ${cashConfirmed
                                        ? 'bg-amber-500 border-amber-500'
                                        : 'bg-primary-800 border-primary-600 hover:border-amber-500/50'}`}>
                                    {cashConfirmed && <span className="text-white text-xs font-bold">✓</span>}
                                </div>
                            </div>
                            <p className="text-primary-200 text-sm leading-relaxed">
                                {t('adminNewMembership.step4.cashConfirmation', {
                                    amount: selectedPlan?.price,
                                    name: selectedStudent?.name,
                                })}
                            </p>
                        </label>
                    </div>

                    <div className="flex gap-3">
                        <button onClick={() => setStep(3)} className="btn-ghost border border-primary-700/40 px-6 py-2.5 rounded-xl text-sm">
                            ← {t('adminNewMembership.back')}
                        </button>
                        <button
                            onClick={handleSubmit}
                            disabled={!cashConfirmed || submitting}
                            className="btn-primary px-8 py-2.5 text-sm disabled:opacity-40 disabled:cursor-not-allowed">
                            {submitting ? t('adminNewMembership.step4.creating') : t('adminNewMembership.step4.create')}
                        </button>
                    </div>
                </div>
            )}
        </div>
    )
}
