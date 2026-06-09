import { useState } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { useNavigate, Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import { useTranslation } from 'react-i18next'
import { sendOtp, verifyOtp, registerUser, resetAuthState } from '../../store/slices/authSlice'
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

export default function RegisterPage() {
    const dispatch = useDispatch()
    const navigate = useNavigate()
    const { t } = useTranslation()
    const { isLoading, sessionToken } = useSelector(s => s.auth)
    const [step, setStep] = useState(0)
    const [contactType, setContactType] = useState('MOBILE')
    const [contact, setContact] = useState('')
    const [otp, setOtp] = useState('')
    const [form, setForm] = useState({ name:'', email:'', dateOfBirth:'', gender:'', address:'' })

    const steps = t('auth.register.steps', { returnObjects: true })

    const handleSendOtp = async () => {
        if (!contact.trim()) return toast.error(t('auth.register.toasts.enterContact'))
        const res = await dispatch(sendOtp({ contact: contact.trim(), contactType }))
        if (sendOtp.fulfilled.match(res)) { toast.success(t('auth.register.toasts.otpSent')); setStep(1) }
        else toast.error(res.payload)
    }

    const handleVerifyOtp = async () => {
        if (otp.length !== 6) return toast.error(t('auth.register.toasts.enter6Otp'))
        const res = await dispatch(verifyOtp({ contact: contact.trim(), otp }))
        if (verifyOtp.fulfilled.match(res)) {
            if (!res.payload.newUser) { toast.error(t('auth.register.toasts.accountExists')); navigate('/login'); return }
            toast.success(t('auth.register.toasts.otpVerified')); setStep(2)
        } else toast.error(res.payload)
    }

    const handleRegister = async () => {
        if (!form.name.trim()) return toast.error(t('auth.register.toasts.nameRequired'))
        const payload = { ...form, sessionToken, [contactType==='MOBILE'?'mobile':'email']: contact }
        const res = await dispatch(registerUser(payload))
        if (registerUser.fulfilled.match(res)) { toast.success(t('auth.register.toasts.registered')); navigate('/student/dashboard') }
        else toast.error(res.payload)
    }

    const StepBar = () => (
        <div className="flex items-center gap-2 mb-8">
            {steps.map((s, i) => (
                <div key={s} className="flex items-center gap-2 flex-1">
                    <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold transition-colors ${i<=step?'bg-amber-500 text-primary-900':'bg-primary-800 text-primary-400'}`}>{i+1}</div>
                    <span className={`text-xs ${i<=step?'text-amber-400':'text-primary-500'}`}>{s}</span>
                    {i<steps.length-1 && <div className={`flex-1 h-px ${i<step?'bg-amber-500':'bg-primary-700'}`} />}
                </div>
            ))}
        </div>
    )

    return (
        <div className="min-h-screen flex items-center justify-center px-4 py-12" style={{background:'radial-gradient(ellipse at center, #1a2a68 0%, #060d2b 100%)'}}>
            <div className="w-full max-w-md">
                <div className="text-center mb-8">
                    <div className="w-14 h-14 bg-amber-500 rounded-2xl flex items-center justify-center font-bold text-primary-900 text-xl shadow-xl shadow-amber-500/30 mx-auto mb-4">TZ</div>
                    <h1 className="font-display text-3xl font-bold text-white">{t('auth.register.title')}</h1>
                    <p className="text-primary-400 mt-2">{t('auth.register.subtitle')}</p>
                </div>
                <div className="card p-8">
                    <StepBar />
                    {step === 0 && (
                        <div className="space-y-5">
                            <div className="flex rounded-xl overflow-hidden border border-primary-700/40">
                                {['MOBILE','EMAIL'].map(tp => (
                                    <button key={tp} onClick={() => setContactType(tp)}
                                            className={`flex-1 py-2.5 text-sm font-medium transition-colors ${contactType===tp?'bg-amber-500 text-primary-900':'text-primary-400 hover:text-white'}`}>
                                        {tp==='MOBILE'? t('auth.login.mobile') : t('auth.login.email')}
                                    </button>
                                ))}
                            </div>
                            <div>
                                <label className="label">{contactType==='MOBILE'? t('auth.register.mobileLabel') : t('auth.register.emailLabel')}</label>
                                <input className="input" placeholder={contactType==='MOBILE'? t('auth.login.mobilePlaceholder') : t('auth.login.emailPlaceholder')}
                                       value={contact} onChange={e=>setContact(e.target.value)} />
                            </div>
                            <button onClick={handleSendOtp} disabled={isLoading} className="btn-primary w-full">
                                {isLoading? t('auth.register.sending') : t('auth.register.sendOtp')}
                            </button>
                            <p className="text-center text-primary-400 text-sm">{t('auth.register.alreadyRegistered')} <Link to="/login" className="text-amber-400 hover:text-amber-300">{t('auth.register.signIn')}</Link></p>
                        </div>
                    )}
                    {step === 1 && (
                        <div className="space-y-5">
                            <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/20 text-center">
                                <p className="text-amber-400 text-sm">{t('auth.register.otpSentTo')} <strong>{contact}</strong></p>
                            </div>
                            <div>
                                <label className="label">{t('auth.register.enterOtp')}</label>
                                <input className="input text-center text-2xl tracking-widest font-mono" maxLength={6}
                                       placeholder="● ● ● ● ● ●" value={otp} onChange={e=>setOtp(e.target.value.replace(/\D/g,''))} />
                            </div>
                            <button onClick={handleVerifyOtp} disabled={isLoading} className="btn-primary w-full">
                                {isLoading? t('auth.register.verifying') : t('auth.register.verifyOtp')}
                            </button>
                            <button onClick={()=>{setStep(0);setOtp('');dispatch(resetAuthState())}} className="w-full text-primary-400 text-sm hover:text-white transition-colors">{t('auth.register.changeContact')}</button>
                        </div>
                    )}
                    {step === 2 && (
                        <div className="space-y-4">
                            <div>
                                <label className="label">{t('auth.register.fullName')}</label>
                                <input className="input" placeholder={t('auth.register.fullNamePlaceholder')} value={form.name} onChange={e=>setForm(f=>({...f,name:e.target.value}))} />
                            </div>
                            {contactType==='MOBILE' && (
                                <div>
                                    <label className="label">{t('auth.register.emailOptional')}</label>
                                    <input className="input" type="email" placeholder={t('auth.login.emailPlaceholder')} value={form.email} onChange={e=>setForm(f=>({...f,email:e.target.value}))} />
                                </div>
                            )}
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="label">{t('auth.register.dob')}</label>
                                    <LocalizationProvider dateAdapter={AdapterDateFns}>
                                        <DatePicker
                                            value={form.dateOfBirth ? parseISO(form.dateOfBirth) : null}
                                            onChange={(d) => setForm(f => ({ ...f, dateOfBirth: d ? format(d, 'yyyy-MM-dd') : '' }))}
                                            sx={{ width: '100%', ...DATE_PICKER_SX }}
                                            slotProps={{ textField: { size: 'small' }, popper: { sx: DATE_PICKER_POPPER_SX } }}
                                        />
                                    </LocalizationProvider>
                                </div>
                                <div>
                                    <label className="label">{t('auth.register.gender')}</label>
                                    <select className="input" value={form.gender} onChange={e=>setForm(f=>({...f,gender:e.target.value}))}>
                                        <option value="">{t('auth.register.genderSelect')}</option>
                                        <option value="Male">{t('profile.gender.male')}</option>
                                        <option value="Female">{t('profile.gender.female')}</option>
                                        <option value="Other">{t('profile.gender.other')}</option>
                                    </select>
                                </div>
                            </div>
                            <div>
                                <label className="label">{t('auth.register.address')}</label>
                                <textarea className="input resize-none" rows={2} placeholder={t('auth.register.addressPlaceholder')} value={form.address} onChange={e=>setForm(f=>({...f,address:e.target.value}))} />
                            </div>
                            <button onClick={handleRegister} disabled={isLoading} className="btn-primary w-full mt-2">
                                {isLoading? t('auth.register.creatingAccount') : t('auth.register.completeReg')}
                            </button>
                        </div>
                    )}
                </div>
                <p className="text-center mt-6 text-primary-500 text-sm"><Link to="/" className="hover:text-primary-300">{t('auth.register.backHome')}</Link></p>
            </div>
        </div>
    )
}
