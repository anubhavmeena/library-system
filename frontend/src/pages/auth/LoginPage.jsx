import { useState, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { useNavigate, Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import { useTranslation } from 'react-i18next'
import { sendOtp, verifyOtp, loginUser, registerUser, resetAuthState } from '../../store/slices/authSlice'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { DatePicker } from '@mui/x-date-pickers/DatePicker'
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFnsV3'
import { parseISO, format, isValid } from 'date-fns'

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

export default function LoginPage() {
    const dispatch = useDispatch()
    const navigate = useNavigate()
    const { t } = useTranslation()
    const { isLoading, sessionToken } = useSelector(s => s.auth)

    const [step, setStep]               = useState(1) // 1=contact, 2=otp, 3=profile
    const [contactType, setContactType] = useState('MOBILE')
    const [contact, setContact]         = useState('')
    const [otp, setOtp]                 = useState('')
    const [resendCooldown, setResendCooldown] = useState(0)
    const [form, setForm]               = useState({ name:'', email:'', dateOfBirth:'', gender:'', address:'' })

    useEffect(() => {
        if (step !== 2) return
        setResendCooldown(30)
        const timer = setInterval(() => {
            setResendCooldown(prev => { if (prev <= 1) { clearInterval(timer); return 0 } return prev - 1 })
        }, 1000)
        return () => clearInterval(timer)
    }, [step])

    const handleSendOtp = async () => {
        if (!contact.trim()) return toast.error(t('auth.login.toasts.enterContact'))
        const res = await dispatch(sendOtp({ contact: contact.trim(), contactType }))
        if (sendOtp.fulfilled.match(res)) { toast.success(t('auth.login.toasts.otpSent')); setStep(2) }
        else toast.error(res.payload || t('auth.login.toasts.failedOtp'))
    }

    const handleResendOtp = async () => {
        const res = await dispatch(sendOtp({ contact: contact.trim(), contactType }))
        if (sendOtp.fulfilled.match(res)) { toast.success(t('auth.login.toasts.otpSent')); setResendCooldown(30) }
        else toast.error(res.payload || t('auth.login.toasts.failedOtp'))
    }

    const handleVerifyOtp = async () => {
        if (otp.length !== 6) return toast.error(t('auth.login.toasts.enter6Otp'))
        const verifyRes = await dispatch(verifyOtp({ contact: contact.trim(), otp }))
        if (!verifyOtp.fulfilled.match(verifyRes)) return toast.error(verifyRes.payload || 'Invalid OTP')

        if (verifyRes.payload.newUser) {
            setStep(3)
        } else {
            const loginRes = await dispatch(loginUser({ sessionToken: verifyRes.payload.sessionToken }))
            if (loginUser.fulfilled.match(loginRes)) { toast.success(t('auth.login.toasts.welcomeBack')); navigate('/student/dashboard') }
            else toast.error(loginRes.payload || 'Login failed')
        }
    }

    const handleRegister = async () => {
        if (!form.name.trim()) return toast.error(t('auth.register.toasts.nameRequired'))
        const payload = { ...form, sessionToken, [contactType === 'MOBILE' ? 'mobile' : 'email']: contact }
        const res = await dispatch(registerUser(payload))
        if (registerUser.fulfilled.match(res)) { toast.success(t('auth.register.toasts.registered')); navigate('/student/dashboard') }
        else toast.error(res.payload || 'Registration failed')
    }

    const pageTitle    = step === 3 ? t('auth.register.title')    : t('auth.login.title')
    const pageSubtitle = step === 3 ? t('auth.register.subtitle') : t('auth.login.subtitle')

    return (
        <div className="min-h-screen flex items-center justify-center px-4 py-12" style={{background:'radial-gradient(ellipse at center, #1a2a68 0%, #060d2b 100%)'}}>
            <div className="w-full max-w-md">
                <div className="text-center mb-8">
                    <div className="w-14 h-14 bg-amber-500 rounded-2xl flex items-center justify-center font-bold text-primary-900 text-xl shadow-xl shadow-amber-500/30 mx-auto mb-4">TZ</div>
                    <h1 className="font-display text-3xl font-bold text-white">{pageTitle}</h1>
                    <p className="text-primary-400 mt-2">{pageSubtitle}</p>
                </div>

                <div className="card p-8">
                    {/* ── Step 1: Contact Entry ─────────────────────────────────── */}
                    {step === 1 && (
                        <div className="space-y-5">
                            <div className="flex rounded-xl overflow-hidden border border-primary-700/40">
                                {['MOBILE','EMAIL'].map(tp => (
                                    <button key={tp} onClick={() => setContactType(tp)}
                                            className={`flex-1 py-2.5 text-sm font-medium transition-colors ${contactType===tp ? 'bg-amber-500 text-primary-900' : 'text-primary-400 hover:text-white'}`}>
                                        {tp === 'MOBILE' ? t('auth.login.mobile') : t('auth.login.email')}
                                    </button>
                                ))}
                            </div>
                            <div>
                                <label className="label">{contactType === 'MOBILE' ? t('auth.login.mobileLabel') : t('auth.login.emailLabel')}</label>
                                <input className="input"
                                       placeholder={contactType === 'MOBILE' ? t('auth.login.mobilePlaceholder') : t('auth.login.emailPlaceholder')}
                                       value={contact} onChange={e => setContact(e.target.value)}
                                       onKeyDown={e => e.key === 'Enter' && handleSendOtp()} />
                            </div>
                            <button onClick={handleSendOtp} disabled={isLoading} className="btn-primary w-full">
                                {isLoading ? t('auth.login.sendingOtp') : t('auth.login.sendOtp')}
                            </button>
                        </div>
                    )}

                    {/* ── Step 2: OTP Verification ──────────────────────────────── */}
                    {step === 2 && (
                        <div className="space-y-5">
                            <div className="text-center p-4 rounded-xl bg-amber-500/10 border border-amber-500/20">
                                <p className="text-amber-400 text-sm">{t('auth.login.otpSentTo')} <strong>{contact}</strong></p>
                            </div>
                            <div>
                                <label className="label">{t('auth.login.enterOtp')}</label>
                                <input className="input text-center text-2xl tracking-widest font-mono" maxLength={6}
                                       placeholder="● ● ● ● ● ●" value={otp}
                                       onChange={e => setOtp(e.target.value.replace(/\D/g,''))}
                                       onKeyDown={e => e.key === 'Enter' && handleVerifyOtp()} />
                                <p className="text-primary-500 text-xs mt-2">{t('auth.login.otpValid')}</p>
                            </div>
                            <button onClick={handleVerifyOtp} disabled={isLoading} className="btn-primary w-full">
                                {isLoading ? t('auth.login.verifying') : t('auth.login.signIn')}
                            </button>
                            <div className="flex items-center justify-between">
                                <button onClick={() => { setStep(1); setOtp(''); dispatch(resetAuthState()) }}
                                        className="text-primary-400 text-sm hover:text-white transition-colors">
                                    {t('auth.login.changeContact')}
                                </button>
                                <button onClick={handleResendOtp} disabled={resendCooldown > 0 || isLoading}
                                        className="text-sm transition-colors disabled:opacity-40 disabled:cursor-not-allowed text-amber-400 hover:text-amber-300 disabled:text-primary-500">
                                    {resendCooldown > 0 ? t('auth.login.resendIn', { seconds: resendCooldown }) : t('auth.login.resendOtp')}
                                </button>
                            </div>
                        </div>
                    )}

                    {/* ── Step 3: Profile Form (new users only) ─────────────────── */}
                    {step === 3 && (
                        <div className="space-y-4">
                            <div>
                                <label className="label">{t('auth.register.fullName')}</label>
                                <input className="input" placeholder={t('auth.register.fullNamePlaceholder')}
                                       value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
                            </div>
                            {contactType === 'MOBILE' && (
                                <div>
                                    <label className="label">{t('auth.register.emailOptional')}</label>
                                    <input className="input" type="email" placeholder={t('auth.login.emailPlaceholder')}
                                           value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} />
                                </div>
                            )}
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="label">{t('auth.register.dob')}</label>
                                    <LocalizationProvider dateAdapter={AdapterDateFns}>
                                        <DatePicker
                                            value={form.dateOfBirth ? parseISO(form.dateOfBirth) : null}
                                            onChange={d => setForm(f => ({ ...f, dateOfBirth: d && isValid(d) ? format(d, 'yyyy-MM-dd') : '' }))}
                                            onError={() => {}}
                                            sx={{ width: '100%', ...DATE_PICKER_SX }}
                                            slotProps={{ textField: { size: 'small' }, popper: { sx: DATE_PICKER_POPPER_SX } }}
                                        />
                                    </LocalizationProvider>
                                </div>
                                <div>
                                    <label className="label">{t('auth.register.gender')}</label>
                                    <select className="input" value={form.gender} onChange={e => setForm(f => ({ ...f, gender: e.target.value }))}>
                                        <option value="">{t('auth.register.genderSelect')}</option>
                                        <option value="Male">{t('profile.gender.male')}</option>
                                        <option value="Female">{t('profile.gender.female')}</option>
                                        <option value="Other">{t('profile.gender.other')}</option>
                                    </select>
                                </div>
                            </div>
                            <div>
                                <label className="label">{t('auth.register.address')}</label>
                                <textarea className="input resize-none" rows={2}
                                          placeholder={t('auth.register.addressPlaceholder')}
                                          value={form.address} onChange={e => setForm(f => ({ ...f, address: e.target.value }))} />
                            </div>
                            <button onClick={handleRegister} disabled={isLoading} className="btn-primary w-full mt-2">
                                {isLoading ? t('auth.register.creatingAccount') : t('auth.register.completeReg')}
                            </button>
                        </div>
                    )}
                </div>

                <p className="text-center mt-6 text-primary-500 text-sm">
                    <Link to="/" className="hover:text-primary-300">{t('auth.login.backHome')}</Link>
                </p>
            </div>
        </div>
    )
}
