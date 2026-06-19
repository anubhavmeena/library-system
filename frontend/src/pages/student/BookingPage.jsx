import { useState, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { useTranslation } from 'react-i18next'
import { fetchSeatAvailability, selectSeat, bookSeat } from '../../store/slices/seatSlice'
import { fetchMyMembership, fetchPlans, createPaymentOrder, verifyPayment } from '../../store/slices/membershipSlice'

// Physical obstructions — not bookable seats
const INACTIVE_SEATS = new Set(['B8', 'B18', 'C18'])
// Display order within each section: left section reads right-to-left (13→1), right reads left-to-right (15→27)
const L_TOP    = [13, 11, 9, 7, 5, 3, 1]
const L_BOTTOM = [14, 12, 10, 8, 6, 4, 2]
const R_TOP    = [15, 17, 19, 21, 23, 25, 27]
const R_BOTTOM = [16, 18, 20, 22, 24, 26, 28]

function SeatGrid({ seats, selectedSeat, onSelect, t }) {
    const findSeat = (seatNumber) => seats.find(s => s.seatNumber === seatNumber)

    const getSeatStatus = (seatNumber) => {
        if (INACTIVE_SEATS.has(seatNumber)) return 'inactive'
        const seat = seats.find(s => s.seatNumber === seatNumber)
        if (!seat) return 'available'
        return seat.isBooked ? 'booked' : 'available'
    }

    const renderSeat = (sn) => {
        const status = getSeatStatus(sn)
        const isSelected = selectedSeat?.seatNumber === sn
        if (status === 'inactive') {
            return <div key={sn} className="w-8 h-8 rounded-lg bg-primary-900/50 border border-primary-800/20" title="Blocked" />
        }
        return (
            <button key={sn} disabled={status === 'booked'}
                    onClick={() => status === 'available' && onSelect(findSeat(sn) ?? { seatNumber: sn, row: sn[0] })}
                    title={`Seat ${sn}${status === 'booked' ? ' (Booked)' : ''}`}
                    className={`w-8 h-8 rounded-lg text-xs font-medium transition-all duration-150 border
                        ${status === 'booked'
                            ? 'bg-red-500/20 border-red-500/30 text-red-500/50 cursor-not-allowed'
                            : isSelected
                                ? 'bg-amber-500 border-amber-400 text-primary-900 font-bold shadow-lg shadow-amber-500/30 seat-selected'
                                : 'bg-primary-800/60 border-primary-700/40 text-primary-300 hover:bg-emerald-500/20 hover:border-emerald-500/40 hover:text-emerald-400 cursor-pointer'
                        }`}>
                {sn.substring(1)}
            </button>
        )
    }

    return (
        <div className="overflow-x-auto">
            <div className="flex items-center justify-center mb-6">
                <div className="px-12 py-2 rounded-lg bg-primary-700/40 border border-primary-600/30 text-primary-400 text-xs tracking-widest uppercase">
                    ← ENTRANCE / FRONT →
                </div>
            </div>
            <div className="space-y-4 min-w-[640px]">
                {['A', 'B', 'C', 'D'].map((row, rowIdx) => (
                    <div key={row}>
                        <div className="flex items-start gap-2">
                            <span className="text-primary-400 font-mono text-sm w-5 text-center pt-2">{row}</span>
                            <div>
                                <div className="flex gap-1.5">{L_TOP.map(n => renderSeat(`${row}${n}`))}</div>
                                <div className="border-b border-primary-700/40 my-1" />
                                <div className="flex gap-1.5">{L_BOTTOM.map(n => renderSeat(`${row}${n}`))}</div>
                            </div>
                            <div className="w-8 flex-shrink-0" />
                            <div>
                                <div className="flex gap-1.5">{R_TOP.map(n => renderSeat(`${row}${n}`))}</div>
                                <div className="border-b border-primary-700/40 my-1" />
                                <div className="flex gap-1.5">{R_BOTTOM.map(n => renderSeat(`${row}${n}`))}</div>
                            </div>
                        </div>
                        {rowIdx === 1 && (
                            <div className="flex items-center gap-2 mt-3">
                                <div className="h-px flex-1 bg-primary-800/40" />
                                <span className="text-primary-700 text-xs tracking-widest">WALKWAY</span>
                                <div className="h-px flex-1 bg-primary-800/40" />
                            </div>
                        )}
                    </div>
                ))}
            </div>
            <div className="flex items-center gap-6 mt-6 text-xs text-primary-400">
                <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-primary-800/60 border border-primary-700/40" />{t('booking.seat.legendAvailable')}</div>
                <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-amber-500 border border-amber-400" />{t('booking.seat.legendSelected')}</div>
                <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-red-500/20 border border-red-500/30" />{t('booking.seat.legendBooked')}</div>
            </div>
        </div>
    )
}

export default function BookingPage() {
    const dispatch = useDispatch()
    const navigate = useNavigate()
    const { t } = useTranslation()
    const { seats, selectedSeat, isLoading: seatsLoading } = useSelector(s => s.seat)
    const { plans, current, isLoading: planLoading } = useSelector(s => s.membership)

    const [step, setStep] = useState(1)
    const [selectedPlan, setSelectedPlan] = useState(null)
    const [selectedShift, setSelectedShift] = useState('MORNING')
    const [paying, setPaying] = useState(false)
    const [membershipChecked, setMembershipChecked] = useState(false)

    const steps = t('booking.steps', { returnObjects: true })

    useEffect(() => {
        dispatch(fetchMyMembership()).finally(() => setMembershipChecked(true))
        dispatch(fetchPlans())
    }, [])
    useEffect(() => {
        if (selectedPlan) dispatch(fetchSeatAvailability({ shift: selectedPlan.planType === 'HALF_DAY' ? selectedShift : 'FULL_DAY' }))
    }, [selectedPlan, selectedShift])

    const handlePlanSelect = (plan) => { setSelectedPlan(plan); dispatch(selectSeat(null)); setStep(2) }

    const shiftLabel = (planType, shift) => {
        if (planType === 'FULL_DAY') return t('booking.seat.shiftFullDay')
        return shift === 'MORNING' ? t('booking.seat.shiftMorning') : t('booking.seat.shiftEvening')
    }

    const summaryShiftLabel = (planType, shift) => {
        if (planType === 'FULL_DAY') return t('booking.summary.shiftFullDay')
        return shift === 'MORNING' ? t('booking.summary.shiftMorning') : t('booking.summary.shiftEvening')
    }

    const handlePayment = async () => {
        if (!selectedSeat) return toast.error(t('booking.toasts.selectSeat'))
        setPaying(true)
        try {
            const orderRes = await dispatch(createPaymentOrder({
                planId: selectedPlan.id,
                seatId: selectedSeat.id || null,
                seatNumber: selectedSeat.seatNumber,
                shift: selectedPlan.planType === 'HALF_DAY' ? selectedShift : 'FULL_DAY',
            }))
            if (!createPaymentOrder.fulfilled.match(orderRes)) throw new Error(orderRes.payload)

            const order = orderRes.payload
            const confirmBooking = async (activated) => {
                await dispatch(bookSeat({
                    seatNumber: activated.seatNumber,
                    membershipId: activated.id,
                    shift: activated.shift,
                    startDate: activated.startDate,
                    endDate: activated.endDate,
                }))
                toast.success(t('booking.toasts.confirmed'))
                navigate('/student/payment-success')
            }

            if (order.orderId?.startsWith('dev_')) {
                // Dev mode — no gateway credentials configured, skip checkout
                const verifyRes = await dispatch(verifyPayment({ gatewayOrderId: order.orderId, gatewayPaymentId: 'dev_pay_' + Date.now(), signature: 'dev_sig', membershipId: order.membershipId }))
                if (verifyPayment.fulfilled.match(verifyRes)) await confirmBooking(verifyRes.payload)
            } else if (order.gateway === 'CASHFREE') {
                await handleCashfreePayment(order, confirmBooking)
            } else {
                // RAZORPAY
                const options = {
                    key: order.razorpayKeyId,
                    amount: order.amount * 100,
                    currency: 'INR',
                    name: 'Target Zone Library',
                    description: `${selectedPlan.name} – Seat ${selectedSeat.seatNumber}`,
                    order_id: order.orderId,
                    handler: async (response) => {
                        const verifyRes = await dispatch(verifyPayment({ gatewayOrderId: response.razorpay_order_id, gatewayPaymentId: response.razorpay_payment_id, signature: response.razorpay_signature, membershipId: order.membershipId }))
                        if (verifyPayment.fulfilled.match(verifyRes)) await confirmBooking(verifyRes.payload)
                        else toast.error(t('booking.toasts.verifyFailed'))
                    },
                    theme: { color: '#f59e0b' },
                }
                const rzp = new window.Razorpay(options)
                rzp.open()
            }
        } catch (e) {
            toast.error(e.message || t('booking.toasts.paymentFailed'))
        } finally { setPaying(false) }
    }

    const handleCashfreePayment = async (order, confirmBooking) => {
        const { load } = await import('@cashfreepayments/cashfree-js')
        const cashfree = await load({
            mode: import.meta.env.VITE_CASHFREE_ENV || 'sandbox',
        })
        const result = await cashfree.checkout({
            paymentSessionId: order.paymentSessionId,
            redirectTarget: '_modal',
        })
        if (result.error) throw new Error(result.error.message || t('booking.toasts.paymentFailed'))

        // Backend verifies payment server-side via Cashfree GET /pg/orders/{id}
        const verifyRes = await dispatch(verifyPayment({
            gatewayOrderId:   order.orderId,
            gatewayPaymentId: order.orderId,
            signature:        null,
            membershipId:     order.membershipId,
        }))
        if (verifyPayment.fulfilled.match(verifyRes)) await confirmBooking(verifyRes.payload)
        else toast.error(t('booking.toasts.verifyFailed'))
    }

    const StepBar = () => (
        <div className="flex items-center gap-2 mb-8">
            {steps.map((s, i) => (
                <div key={s} className="flex items-center gap-2 flex-1 last:flex-none">
                    <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold cursor-pointer transition-colors ${i+1<=step?'bg-amber-500 text-primary-900':'bg-primary-800 text-primary-400'}`}
                         onClick={()=>i+1<step&&setStep(i+1)}>{i+1}</div>
                    <span className={`text-xs hidden sm:block ${i+1<=step?'text-amber-400':'text-primary-500'}`}>{s}</span>
                    {i<2&&<div className={`flex-1 h-px ${i+1<step?'bg-amber-500':'bg-primary-700'}`}/>}
                </div>
            ))}
        </div>
    )

    if (!membershipChecked) return (
        <div className="space-y-4">
            <div className="shimmer h-12 rounded-xl w-64" />
            <div className="shimmer h-48 rounded-2xl" />
        </div>
    )

    if (current?.status === 'ACTIVE') {
        const expiry = new Date(current.endDate).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' })
        return (
            <div>
                <div className="mb-6">
                    <h1 className="page-header">{t('booking.title')}</h1>
                    <p className="text-primary-400">{t('booking.subtitle')}</p>
                </div>
                <div className="card p-8 max-w-lg border-amber-500/30 bg-amber-500/5">
                    <div className="text-4xl mb-4">🔒</div>
                    <h2 className="section-title mb-2">{t('booking.activeMembership.title')}</h2>
                    <p className="text-primary-300 mb-4">
                        {t('booking.activeMembership.desc', { planName: current.plan?.name })}
                    </p>
                    <div className="p-4 rounded-xl bg-primary-800/60 border border-primary-700/40 space-y-2 text-sm mb-6">
                        <div className="flex justify-between"><span className="text-primary-400">{t('booking.activeMembership.seat')}</span><span className="text-white font-medium">{current.seatNumber}</span></div>
                        <div className="flex justify-between"><span className="text-primary-400">{t('booking.activeMembership.shift')}</span><span className="text-white font-medium">{current.shift}</span></div>
                        <div className="flex justify-between"><span className="text-primary-400">{t('booking.activeMembership.expiresOn')}</span><span className="text-amber-400 font-semibold">{expiry}</span></div>
                    </div>
                    <a href="/student/membership" className="btn-outline text-sm px-5 py-2.5 inline-block">{t('booking.activeMembership.viewMembership')}</a>
                </div>
            </div>
        )
    }

    return (
        <div>
            <div className="mb-6">
                <h1 className="page-header">{t('booking.title')}</h1>
                <p className="text-primary-400">{t('booking.subtitle')}</p>
            </div>
            <StepBar />

            {step === 1 && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 max-w-2xl">
                    {planLoading ? <div className="shimmer h-48 rounded-2xl" /> : plans.map(plan => (
                        <div key={plan.id} onClick={()=>handlePlanSelect(plan)}
                             className={`card-hover p-8 cursor-pointer ${selectedPlan?.id===plan.id?'border-amber-400/60':''}`}>
                            <div className="flex items-start justify-between mb-4">
                                <div>
                                    <h3 className="section-title">{plan.name}</h3>
                                    <p className="text-primary-400 text-sm mt-1">{plan.description}</p>
                                </div>
                                <div className="text-right">
                                    <p className="text-3xl font-bold text-amber-400">₹{plan.price}</p>
                                    <p className="text-primary-500 text-xs">{t('booking.plan.perMonth')}</p>
                                </div>
                            </div>
                            <div className="space-y-2">
                                {(t(plan.planType === 'HALF_DAY' ? 'booking.plan.halfDayFeatures' : 'booking.plan.fullDayFeatures', { returnObjects: true })).map(f=>(
                                    <div key={f} className="flex items-center gap-2 text-sm text-primary-300">
                                        <span className="text-amber-400">✓</span>{f}
                                    </div>
                                ))}
                            </div>
                            {plan.planType === 'FULL_DAY' && (
                                <div className="mt-4 px-3 py-1 rounded-full bg-amber-500/20 border border-amber-500/30 text-amber-400 text-xs inline-block">{t('booking.plan.popular')}</div>
                            )}
                        </div>
                    ))}
                </div>
            )}

            {step === 2 && selectedPlan && (
                <div>
                    {selectedPlan.planType === 'HALF_DAY' && (
                        <div className="flex gap-3 mb-6">
                            {[{v:'MORNING',k:'morning'},{v:'EVENING',k:'evening'}].map(({v,k})=>(
                                <button key={v} onClick={()=>setSelectedShift(v)}
                                        className={`px-5 py-2.5 rounded-xl text-sm font-medium border transition-all ${selectedShift===v?'bg-amber-500/20 border-amber-400/60 text-amber-400':'border-primary-700/40 text-primary-400 hover:text-white'}`}>
                                    {t(`booking.seat.${k}`)}
                                </button>
                            ))}
                        </div>
                    )}
                    <div className="card p-6 mb-6">
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="section-title">{t('booking.seat.selectTitle')}</h3>
                            <div className="text-sm text-primary-400">{t('booking.seat.availableCount', { count: seats.filter(s=>!s.isBooked).length })}</div>
                        </div>
                        {seatsLoading ? <div className="shimmer h-64 rounded-xl" /> : <SeatGrid seats={seats} selectedSeat={selectedSeat} onSelect={seat=>dispatch(selectSeat(seat))} t={t} />}
                    </div>
                    {selectedSeat && (
                        <div className="card p-4 mb-6 border-amber-500/30 bg-amber-500/5 flex items-center justify-between">
                            <div>
                                <p className="text-amber-400 font-semibold">{t('booking.seat.selectedSeat', { seatNumber: selectedSeat.seatNumber })}</p>
                                <p className="text-primary-400 text-sm">{t('booking.seat.selectedInfo', { row: selectedSeat.row, shift: shiftLabel(selectedPlan.planType, selectedShift) })}</p>
                            </div>
                            <button onClick={()=>setStep(3)} className="btn-primary px-5 py-2.5">{t('booking.seat.continue')}</button>
                        </div>
                    )}
                    <button onClick={()=>setStep(1)} className="text-primary-400 text-sm hover:text-white transition-colors">{t('booking.seat.backToPlans')}</button>
                </div>
            )}

            {step === 3 && selectedPlan && selectedSeat && (
                <div className="max-w-md">
                    <div className="card p-6 mb-6">
                        <h3 className="section-title mb-5">{t('booking.summary.title')}</h3>
                        <div className="space-y-4">
                            {[
                                { l: t('booking.summary.plan'),       v: selectedPlan.name },
                                { l: t('booking.summary.seatNumber'), v: selectedSeat.seatNumber },
                                { l: t('booking.summary.shift'),      v: summaryShiftLabel(selectedPlan.planType, selectedShift) },
                                { l: t('booking.summary.duration'),   v: t('booking.summary.duration30') },
                                { l: t('booking.summary.startDate'),  v: new Date().toLocaleDateString('en-IN') },
                            ].map(({l,v})=>(
                                <div key={l} className="flex justify-between items-center py-2 border-b border-primary-700/30 last:border-0">
                                    <span className="text-primary-400 text-sm">{l}</span>
                                    <span className="text-white font-medium">{v}</span>
                                </div>
                            ))}
                            <div className="flex justify-between items-center pt-2">
                                <span className="text-white font-semibold">{t('booking.summary.totalAmount')}</span>
                                <span className="text-2xl font-bold text-amber-400">₹{selectedPlan.price}</span>
                            </div>
                        </div>
                    </div>
                    <div className="card p-4 mb-6 bg-primary-800/30 text-primary-400 text-xs leading-relaxed">
                        {t('booking.summary.disclaimer')}
                    </div>
                    <button onClick={handlePayment} disabled={paying} className="btn-primary w-full py-4 text-base mb-3">
                        {paying ? t('booking.summary.processing') : t('booking.summary.payBtn', { amount: selectedPlan.price })}
                    </button>
                    <button onClick={()=>setStep(2)} className="w-full text-primary-400 text-sm hover:text-white transition-colors">{t('booking.summary.changeSeat')}</button>
                </div>
            )}
        </div>
    )
}
