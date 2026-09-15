import { useState, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { useNavigate, Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import { useTranslation } from 'react-i18next'
import { sendOtp, adminLogin } from '../../store/slices/authSlice'

export default function AdminLoginPage() {
    const dispatch = useDispatch()
    const navigate = useNavigate()
    const { t } = useTranslation()
    const { isLoading } = useSelector(s => s.auth)
    const [contact, setContact] = useState('')
    const [otp, setOtp] = useState('')
    const [step, setStep] = useState(1)
    const isMobile = !contact.includes('@')

    // Mirrors LoginPage.jsx's resend/SMS-fallback timing (backend's 10s resend
    // cooldown). otpSendCount tracks how many times an OTP has been (re)sent on
    // this screen; the "Send via SMS instead" option appears once that's >= 2
    // (i.e. after one resend) and 10s have passed since the last send.
    const [otpSendCount, setOtpSendCount] = useState(0)
    const [secondsSinceSend, setSecondsSinceSend] = useState(0)
    const [smsOptionUsed, setSmsOptionUsed] = useState(false)

    useEffect(() => {
        if (step !== 2 || otpSendCount === 0) return
        setSecondsSinceSend(0)
        const timer = setInterval(() => setSecondsSinceSend(s => s + 1), 1000)
        return () => clearInterval(timer)
    }, [step, otpSendCount])

    const canResend = secondsSinceSend >= 10
    const secondsLeft = Math.max(0, 10 - secondsSinceSend)
    const showSmsOption = isMobile && !smsOptionUsed && otpSendCount >= 2 && canResend

    const handleSendOtp = async () => {
        if (!contact.trim()) return toast.error(t('auth.adminLogin.toasts.enterContact'))
        const res = await dispatch(sendOtp({ contact: contact.trim(), contactType: isMobile ? 'MOBILE' : 'EMAIL' }))
        if (sendOtp.fulfilled.match(res)) { toast.success(t('auth.adminLogin.toasts.otpSent')); setStep(2); setOtpSendCount(1) }
        else toast.error(res.payload || t('auth.adminLogin.toasts.failed'))
    }

    const handleResendOtp = async () => {
        const res = await dispatch(sendOtp({ contact: contact.trim(), contactType: isMobile ? 'MOBILE' : 'EMAIL' }))
        if (sendOtp.fulfilled.match(res)) { toast.success(t('auth.adminLogin.toasts.otpSent')); setOtpSendCount(c => c + 1) }
        else toast.error(res.payload || t('auth.adminLogin.toasts.failed'))
    }

    const handleSendViaSms = async () => {
        const res = await dispatch(sendOtp({ contact: contact.trim(), contactType: 'MOBILE', channel: 'SMS' }))
        if (sendOtp.fulfilled.match(res)) {
            toast.success(t('auth.adminLogin.toasts.otpSentSms'))
            setSmsOptionUsed(true)
            setOtpSendCount(c => c + 1)
        } else toast.error(res.payload || t('auth.adminLogin.toasts.failed'))
    }

    const handleLogin = async () => {
        if (otp.length !== 6) return toast.error(t('auth.adminLogin.toasts.enter6Otp'))
        const res = await dispatch(adminLogin({ contact: contact.trim(), otp }))
        if (adminLogin.fulfilled.match(res)) { toast.success(t('auth.adminLogin.toasts.loginSuccess')); navigate('/admin/dashboard') }
        else toast.error(res.payload || t('auth.adminLogin.toasts.loginFailed'))
    }

    return (
        <div className="min-h-screen flex items-center justify-center px-4" style={{background:'radial-gradient(ellipse at center, #1a0a0a 0%, #060d2b 100%)'}}>
            <div className="w-full max-w-md">
                <div className="text-center mb-8">
                    <div className="w-14 h-14 bg-red-600 rounded-2xl flex items-center justify-center font-bold text-white text-2xl shadow-xl shadow-red-600/30 mx-auto mb-4">A</div>
                    <h1 className="font-display text-3xl font-bold text-white">{t('auth.adminLogin.title')}</h1>
                    <p className="text-primary-400 mt-2">{t('auth.adminLogin.subtitle')}</p>
                </div>
                <div className="card border-red-900/30 p-8">
                    {step === 1 ? (
                        <div className="space-y-5">
                            <div>
                                <label className="label">{t('auth.adminLogin.label')}</label>
                                <input className="input" placeholder={t('auth.adminLogin.placeholder')}
                                       value={contact} onChange={e=>setContact(e.target.value)} onKeyDown={e=>e.key==='Enter'&&handleSendOtp()} />
                            </div>
                            <button onClick={handleSendOtp} disabled={isLoading} className="w-full bg-red-600 hover:bg-red-500 text-white font-semibold px-6 py-3 rounded-xl transition-all duration-200">
                                {isLoading ? t('auth.adminLogin.sendingOtp') : t('auth.adminLogin.sendOtp')}
                            </button>
                        </div>
                    ) : (
                        <div className="space-y-5">
                            <div className="p-4 rounded-xl bg-red-500/10 border border-red-500/20 text-center">
                                <p className="text-red-400 text-sm">{t('auth.adminLogin.otpSentTo')} <strong>{contact}</strong></p>
                            </div>
                            <div>
                                <label className="label">{t('auth.adminLogin.enterOtp')}</label>
                                <input className="input text-center text-2xl tracking-widest font-mono" maxLength={6}
                                       placeholder="● ● ● ● ● ●" value={otp} onChange={e=>setOtp(e.target.value.replace(/\D/g,''))} />
                            </div>
                            <button onClick={handleLogin} disabled={isLoading} className="w-full bg-red-600 hover:bg-red-500 text-white font-semibold px-6 py-3 rounded-xl transition-all">
                                {isLoading ? t('auth.adminLogin.verifying') : t('auth.adminLogin.loginBtn')}
                            </button>
                            <div className="flex items-center justify-between">
                                <button onClick={()=>{ setStep(1); setOtp(''); setOtpSendCount(0); setSmsOptionUsed(false) }}
                                        className="text-primary-400 text-sm hover:text-white transition-colors">
                                    {t('auth.adminLogin.back')}
                                </button>
                                <button onClick={handleResendOtp} disabled={!canResend || isLoading}
                                        className="text-sm transition-colors disabled:opacity-40 disabled:cursor-not-allowed text-red-400 hover:text-red-300 disabled:text-primary-500">
                                    {canResend ? t('auth.adminLogin.resendOtp') : t('auth.adminLogin.resendIn', { seconds: secondsLeft })}
                                </button>
                            </div>
                            {showSmsOption && (
                                <button onClick={handleSendViaSms} disabled={isLoading}
                                        className="w-full text-sm text-center text-blue-400 hover:text-blue-300 transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
                                    {t('auth.adminLogin.sendViaSms')}
                                </button>
                            )}
                        </div>
                    )}
                </div>
                <p className="text-center mt-6 text-primary-500 text-sm"><Link to="/" className="hover:text-primary-300">{t('auth.adminLogin.backHome')}</Link></p>
            </div>
        </div>
    )
}
