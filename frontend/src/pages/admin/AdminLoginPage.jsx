import { useState } from 'react'
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

    const handleSendOtp = async () => {
        if (!contact.trim()) return toast.error(t('auth.adminLogin.toasts.enterContact'))
        const res = await dispatch(sendOtp({ contact: contact.trim(), contactType: contact.includes('@') ? 'EMAIL' : 'MOBILE' }))
        if (sendOtp.fulfilled.match(res)) { toast.success(t('auth.adminLogin.toasts.otpSent')); setStep(2) }
        else toast.error(res.payload || t('auth.adminLogin.toasts.failed'))
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
                            <button onClick={()=>setStep(1)} className="w-full text-primary-400 text-sm hover:text-white transition-colors">{t('auth.adminLogin.back')}</button>
                        </div>
                    )}
                </div>
                <p className="text-center mt-6 text-primary-500 text-sm"><Link to="/" className="hover:text-primary-300">{t('auth.adminLogin.backHome')}</Link></p>
            </div>
        </div>
    )
}
