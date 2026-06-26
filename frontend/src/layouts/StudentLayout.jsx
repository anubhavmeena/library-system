import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { logout } from '../store/slices/authSlice'
import LanguageSwitcher from '../components/LanguageSwitcher'

export default function StudentLayout() {
    const [sidebarOpen, setSidebarOpen] = useState(false)
    const { user } = useSelector(s => s.auth)
    const dispatch = useDispatch()
    const navigate = useNavigate()
    const { t } = useTranslation()
    const handleLogout = () => { dispatch(logout()); navigate('/') }

    const navItems = [
        { to: '/student/dashboard', icon: '▦', label: t('student.sidebar.nav.dashboard') },
        { to: '/student/membership', icon: '◈', label: t('student.sidebar.nav.membership') },
        { to: '/student/booking',   icon: '⊞', label: t('student.sidebar.nav.booking') },
        { to: '/student/facilities',icon: '⌂', label: t('student.sidebar.nav.facilities') },
        { to: '/student/profile',   icon: '◉', label: t('student.sidebar.nav.profile') },
        { to: '/student/feedback',      icon: '◎', label: t('student.sidebar.nav.feedback') },
        { to: '/student/gallery',       icon: '◼', label: t('student.sidebar.nav.gallery') },
        { to: '/student/contact-admin', icon: '☎', label: t('student.sidebar.nav.contactAdmin') },
    ]

    return (
        <div className="flex min-h-screen">
            <aside className={`fixed inset-y-0 left-0 z-50 w-64 flex flex-col bg-primary-900/95 border-r border-primary-700/30 backdrop-blur-xl transition-transform duration-300 ${sidebarOpen ? 'translate-x-0' : '-translate-x-full'} lg:translate-x-0 lg:static lg:z-auto`}>
                <div className="flex items-center gap-3 px-6 py-6 border-b border-primary-700/30">
                    <div className="w-10 h-10 bg-amber-500 rounded-xl flex items-center justify-center shadow-lg shadow-amber-500/30 text-primary-900 font-bold text-sm">TZ</div>
                    <div><p className="font-display font-bold text-white text-lg leading-none">{t('student.sidebar.brandMain')}</p><p className="text-primary-400 text-xs">{t('student.sidebar.brandSub')}</p></div>
                </div>
                <div className="mx-4 mt-4 p-3 rounded-xl bg-primary-800/50 border border-primary-700/30">
                    <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-full bg-gradient-to-br from-amber-400 to-primary-500 flex items-center justify-center text-sm font-bold text-white flex-shrink-0">
                            {user?.name?.[0]?.toUpperCase() || 'S'}
                        </div>
                        <div className="min-w-0">
                            <p className="text-white text-sm font-semibold truncate">{user?.name || t('student.sidebar.defaultName')}</p>
                            <p className="text-primary-400 text-xs truncate">{user?.mobile || user?.email}</p>
                        </div>
                    </div>
                </div>
                <nav className="flex-1 px-3 py-4 space-y-1">
                    {navItems.map(({ to, icon, label }) => (
                        <NavLink key={to} to={to} onClick={() => setSidebarOpen(false)}
                                 className={({ isActive }) => `flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 ${isActive ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30' : 'text-primary-300 hover:text-white hover:bg-primary-800/60'}`}>
                            <span className="text-base">{icon}</span>{label}
                        </NavLink>
                    ))}
                </nav>
                <div className="px-4 pb-2">
                    <LanguageSwitcher className="w-full justify-center" />
                </div>
                <div className="p-4 border-t border-primary-700/30">
                    <button onClick={handleLogout} className="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium text-primary-300 hover:text-red-400 hover:bg-red-500/10 transition-all duration-200">
                        <span>↩</span> {t('student.sidebar.logout')}
                    </button>
                </div>
            </aside>
            {sidebarOpen && <div className="fixed inset-0 z-40 bg-black/60 lg:hidden" onClick={() => setSidebarOpen(false)} />}
            <div className="flex-1 flex flex-col min-w-0">
                <header className="lg:hidden flex items-center justify-between px-4 py-4 border-b border-primary-700/30 bg-primary-900/80 backdrop-blur-xl sticky top-0 z-30">
                    <button onClick={() => setSidebarOpen(true)} className="text-primary-300 hover:text-white text-xl">☰</button>
                    <span className="font-display font-bold text-white">{t('student.sidebar.mobileTitle')}</span>
                    <div className="flex items-center gap-2">
                        <LanguageSwitcher />
                        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-amber-400 to-primary-500 flex items-center justify-center text-xs font-bold text-white">
                            {user?.name?.[0]?.toUpperCase() || 'S'}
                        </div>
                    </div>
                </header>
                <main className="flex-1 p-4 lg:p-8 overflow-auto"><Outlet /></main>
            </div>
        </div>
    )
}
