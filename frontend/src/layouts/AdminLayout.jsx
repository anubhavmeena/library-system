import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { logout } from '../store/slices/authSlice'
import LanguageSwitcher from '../components/LanguageSwitcher'

export default function AdminLayout() {
    const [sidebarOpen, setSidebarOpen] = useState(false)
    const { user } = useSelector(s => s.auth)
    const dispatch = useDispatch()
    const navigate = useNavigate()
    const { t } = useTranslation()
    const handleLogout = () => { dispatch(logout()); navigate('/admin/login') }

    const navItems = [
        { to: '/admin/dashboard', icon: '▦', label: t('admin.sidebar.nav.dashboard') },
        { to: '/admin/students',  icon: '◉', label: t('admin.sidebar.nav.students') },
        { to: '/admin/seats',     icon: '⊞', label: t('admin.sidebar.nav.seats') },
        { to: '/admin/reminders', icon: '◈', label: t('admin.sidebar.nav.reminders') },
        { to: '/admin/feedback',         icon: '◎', label: t('admin.sidebar.nav.feedback') },
        { to: '/admin/memberships/new', icon: '⊕', label: t('admin.sidebar.nav.newMembership') },
        { to: '/admin/broadcast',       icon: '◷', label: t('admin.sidebar.nav.broadcast') },
        { to: '/admin/expenses',        icon: '₹', label: t('admin.sidebar.nav.expenses') },
        { to: '/admin/import',          icon: '⬆', label: t('admin.sidebar.nav.importStudents') },
    ]

    return (
        <div className="flex min-h-screen">
            <aside className={`fixed inset-y-0 left-0 z-50 w-64 flex flex-col bg-primary-900/95 border-r border-red-900/30 backdrop-blur-xl transition-transform duration-300 ${sidebarOpen ? 'translate-x-0' : '-translate-x-full'} lg:translate-x-0 lg:static lg:z-auto`}>
                <div className="flex items-center gap-3 px-6 py-6 border-b border-red-900/30">
                    <div className="w-10 h-10 bg-red-500 rounded-xl flex items-center justify-center shadow-lg shadow-red-500/30 text-white font-bold text-lg">A</div>
                    <div><p className="font-display font-bold text-white text-lg leading-none">{t('admin.sidebar.brandMain')}</p><p className="text-red-400 text-xs">{t('admin.sidebar.brandSub')}</p></div>
                </div>
                <div className="mx-4 mt-4 p-3 rounded-xl bg-primary-800/50 border border-red-900/30">
                    <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-full bg-gradient-to-br from-red-400 to-primary-600 flex items-center justify-center text-sm font-bold text-white flex-shrink-0">
                            {user?.name?.[0]?.toUpperCase() || 'A'}
                        </div>
                        <div className="min-w-0">
                            <p className="text-white text-sm font-semibold truncate">{user?.name || t('admin.sidebar.defaultName')}</p>
                            <p className="text-red-400 text-xs">{t('admin.sidebar.role')}</p>
                        </div>
                    </div>
                </div>
                <nav className="flex-1 px-3 py-4 space-y-1">
                    {navItems.map(({ to, icon, label }) => (
                        <NavLink key={to} to={to} onClick={() => setSidebarOpen(false)}
                                 className={({ isActive }) => `flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 ${isActive ? 'bg-red-500/20 text-red-400 border border-red-500/30' : 'text-primary-300 hover:text-white hover:bg-primary-800/60'}`}>
                            <span className="text-base">{icon}</span>{label}
                        </NavLink>
                    ))}
                </nav>
                <div className="px-4 pb-2">
                    <LanguageSwitcher className="w-full justify-center" />
                </div>
                <div className="p-4 border-t border-red-900/30">
                    <button onClick={handleLogout} className="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium text-primary-300 hover:text-red-400 hover:bg-red-500/10 transition-all duration-200">
                        <span>↩</span> {t('admin.sidebar.logout')}
                    </button>
                </div>
            </aside>
            {sidebarOpen && <div className="fixed inset-0 z-40 bg-black/60 lg:hidden" onClick={() => setSidebarOpen(false)} />}
            <div className="flex-1 flex flex-col min-w-0">
                <header className="lg:hidden flex items-center justify-between px-4 py-4 border-b border-red-900/30 bg-primary-900/80 backdrop-blur-xl sticky top-0 z-30">
                    <button onClick={() => setSidebarOpen(true)} className="text-primary-300 hover:text-white text-xl">☰</button>
                    <span className="font-display font-bold text-white">{t('admin.sidebar.mobileTitle')}</span>
                    <div className="flex items-center gap-2">
                        <LanguageSwitcher />
                        <span className="bg-red-500/20 text-red-400 border border-red-500/30 px-2 py-0.5 rounded-full text-xs">{t('admin.sidebar.adminBadge')}</span>
                    </div>
                </header>
                <main className="flex-1 p-4 lg:p-8 overflow-auto"><Outlet /></main>
            </div>
        </div>
    )
}
