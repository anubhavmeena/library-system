import { useState, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { useNavigate, Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import { useTranslation } from 'react-i18next'
import { sendOtp, loginUser, resetAuthState } from '../../store/slices/authSlice'

export default function LoginPage() {
    const dispatch = useDispatch()
    const navigate = useNavigate()
    const { t } = useTranslation()
    const { isLoading } = useSelector(s => s.auth)
    const [contactType, setContactType] = useState('MOBILE')
    const [contact, setContact] = useState('')
    const [otp, setOtp] = useState('')
    const [step, setStep] = useState(1)
    const [resendCooldown, setResendCooldown] = useState(0)

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
        if (sendOtp.fulfilled.match(res)) {
            toast.success(t('auth.login.toasts.otpSent'))
            setResendCooldown(30)
        } else toast.error(res.payload || t('auth.login.toasts.failedOtp'))
    }

    const handleLogin = async () => {
        if (otp.length !== 6) return toast.error(t('auth.login.toasts.enter6Otp'))
        const res = await dispatch(loginUser({ contact: contact.trim(), otp }))
        if (loginUser.fulfilled.match(res)) { toast.success(t('auth.login.toasts.welcomeBack')); navigate('/student/dashboard') }
        else toast.error(res.payload || 'Login failed')
    }

    return (
        <div className="min-h-screen flex items-center justify-center px-4" style={{background:'radial-gradient(ellipse at center, #1a2a68 0%, #060d2b 100%)'}}>
            <div className="w-full max-w-md">
                <div className="text-center mb-8">
                    <div className="w-14 h-14 bg-amber-500 rounded-2xl flex items-center justify-center font-bold text-primary-900 text-xl shadow-xl shadow-amber-500/30 mx-auto mb-4">TZ</div>
                    <h1 className="font-display text-3xl font-bold text-white">{t('auth.login.title')}</h1>
                    <p className="text-primary-400 mt-2">{t('auth.login.subtitle')}</p>
                </div>
                <div className="card p-8">
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
                                <input className="input" placeholder={contactType==='MOBILE' ? t('auth.login.mobilePlaceholder') : t('auth.login.emailPlaceholder')}
                                       value={contact} onChange={e => setContact(e.target.value)}
                                       onKeyDown={e => e.key === 'Enter' && handleSendOtp()} />
                            </div>
                            <button onClick={handleSendOtp} disabled={isLoading} className="btn-primary w-full">
                                {isLoading ? t('auth.login.sendingOtp') : t('auth.login.sendOtp')}
                            </button>
                            <p className="text-center text-primary-400 text-sm">
                                {t('auth.login.newStudent')} <Link to="/register" className="text-amber-400 hover:text-amber-300">{t('auth.login.registerHere')}</Link>
                            </p>
                        </div>
                    )}
                    {step === 2 && (
                        <div className="space-y-5">
                            <div className="text-center p-4 rounded-xl bg-amber-500/10 border border-amber-500/20">
                                <p className="text-amber-400 text-sm">{t('auth.login.otpSentTo')} <strong>{contact}</strong></p>
                            </div>
                            <div>
                                <label className="label">{t('auth.login.enterOtp')}</label>
                                <input className="input text-center text-2xl tracking-widest font-mono" maxLength={6}
                                       placeholder="● ● ● ● ● ●" value={otp} onChange={e => setOtp(e.target.value.replace(/\D/g,''))}
                                       onKeyDown={e => e.key === 'Enter' && handleLogin()} />
                                <p className="text-primary-500 text-xs mt-2">{t('auth.login.otpValid')}</p>
                            </div>
                            <button onClick={handleLogin} disabled={isLoading} className="btn-primary w-full">
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
                </div>
                <p className="text-center mt-6 text-primary-500 text-sm">
                    <Link to="/" className="hover:text-primary-300">{t('auth.login.backHome')}</Link>
                </p>
            </div>
        </div>
    )
}
