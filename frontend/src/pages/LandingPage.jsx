import { Link } from 'react-router-dom'
import { BookOpen, Clock, Wifi, Coffee, Users, Star, ArrowRight, CheckCircle2, Wind, Droplets, Newspaper, Car } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Helmet } from 'react-helmet-async'
import LanguageSwitcher from '../components/LanguageSwitcher'
import PhotoSlideshow from '../components/PhotoSlideshow'

const FEATURE_ICONS = [Clock, Wifi, Coffee, Users, Wind, Droplets, Newspaper, Car]
const FEATURE_KEYS  = ['flexibleShifts', 'wifi', 'refreshment', 'seats', 'ac', 'roCooler', 'newspaper', 'parking']

// Add your library photos to frontend/public/gallery/ and list them here.
// Filenames are served directly from the /gallery/ path at runtime.
const GALLERY_PHOTOS = [
    { src: '/gallery/photo1.jpg',  alt: 'Target Zone Library' },
    { src: '/gallery/photo2.jpg',  alt: 'Target Zone Library' },
    { src: '/gallery/photo3.jpg',  alt: 'Target Zone Library' },
    { src: '/gallery/photo4.jpg',  alt: 'Target Zone Library' },
    { src: '/gallery/photo5.jpg',  alt: 'Target Zone Library' },
    { src: '/gallery/photo6.jpg',  alt: 'Target Zone Library' },
    { src: '/gallery/photo7.jpg',  alt: 'Target Zone Library' },
    { src: '/gallery/photo8.jpg',  alt: 'Target Zone Library' },
    { src: '/gallery/photo9.jpg',  alt: 'Target Zone Library' },
    { src: '/gallery/photo10.jpg', alt: 'Target Zone Library' },
    { src: '/gallery/photo11.jpg', alt: 'Target Zone Library' },
    { src: '/gallery/photo12.jpg', alt: 'Target Zone Library' },
]

export default function LandingPage() {
    const { t } = useTranslation()

    const features = FEATURE_KEYS.map((key, i) => ({
        icon: FEATURE_ICONS[i],
        title: t(`landing.features.${key}.title`),
        desc:  t(`landing.features.${key}.desc`),
    }))

    const halfDayFeatures = ['f1','f2','f3','f4'].map(f => t(`landing.plans.halfDay.${f}`))
    const fullDayFeatures = ['f1','f2','f3','f4'].map(f => t(`landing.plans.fullDay.${f}`))

    const halfDayPlans = [
        { key: 'halfDay',        price: '₹400',   period: t('landing.plans.perMonth'),      color: 'from-primary-600 to-primary-700', features: halfDayFeatures },
        { key: 'halfDaySix',     price: '₹2,400', period: t('landing.plans.perSixMonths'),  color: 'from-primary-600 to-primary-700', badge: t('landing.plans.bestValue'), features: halfDayFeatures },
        { key: 'halfDayAnnual',  price: '₹4,800', period: t('landing.plans.perYear'),       color: 'from-primary-600 to-primary-700', features: halfDayFeatures },
    ]

    const fullDayPlans = [
        { key: 'fullDay',        price: '₹600',   period: t('landing.plans.perMonth'),      color: 'from-amber-500 to-amber-600', popular: true, features: fullDayFeatures },
        { key: 'fullDaySix',     price: '₹3,600', period: t('landing.plans.perSixMonths'),  color: 'from-amber-500 to-amber-600', badge: t('landing.plans.bestValue'), features: fullDayFeatures },
        { key: 'fullDayAnnual',  price: '₹7,200', period: t('landing.plans.perYear'),       color: 'from-amber-500 to-amber-600', features: fullDayFeatures },
    ]

    return (
        <div className="min-h-screen bg-[#0d1b4b] overflow-x-hidden">
            <Helmet>
                <title>Target Zone Library Alwar — Study Seats in Malviya Nagar | Book Online</title>
                <meta name="description" content="Reserve your dedicated study seat at Target Zone Library, Alwar. Morning, evening &amp; full-day plans. 110 seats, open 6 AM–11 PM. Book online instantly." />
            </Helmet>
            {/* Nav */}
            <nav className="fixed top-0 left-0 right-0 z-50 flex items-center justify-between px-6 md:px-12 py-4 bg-primary-900/80 backdrop-blur-xl border-b border-primary-700/20">
                <div className="flex items-center gap-3">
                    <div className="w-9 h-9 bg-amber-500 rounded-xl flex items-center justify-center shadow-lg shadow-amber-500/30">
                        <BookOpen size={18} className="text-primary-900" />
                    </div>
                    <span className="font-display font-bold text-white text-xl">Target Zone Library</span>
                </div>
                <div className="flex items-center gap-3">
                    <LanguageSwitcher />
                    <Link to="/admin/login" className="text-primary-400 hover:text-white text-sm transition-colors">{t('nav.admin')}</Link>
                    <Link to="/login" className="btn-primary text-sm py-2 px-5">{t('nav.getStarted')}</Link>
                </div>
            </nav>

            {/* Hero */}
            <section className="relative min-h-screen flex items-center justify-center px-6 pt-20">
                <div className="absolute inset-0 overflow-hidden pointer-events-none">
                    <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-primary-600/10 rounded-full blur-3xl" />
                    <div className="absolute bottom-1/4 right-1/4 w-80 h-80 bg-amber-500/8 rounded-full blur-3xl" />
                </div>
                <div className="relative z-10 text-center max-w-4xl mx-auto">
                    <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-amber-500/10 border border-amber-400/20 text-amber-400 text-sm font-medium mb-8">
                        <Star size={14} fill="currentColor" /> {t('landing.badge')}
                    </div>
                    <h1 className="font-display text-5xl md:text-7xl font-bold text-white leading-tight mb-6">
                        {t('landing.heroTitle')}<br />
                        <span className="text-transparent bg-clip-text bg-gradient-to-r from-amber-400 to-amber-200">{t('landing.heroHighlight')}</span>
                    </h1>
                    <p className="text-primary-300 text-lg md:text-xl max-w-2xl mx-auto mb-10 leading-relaxed">
                        {t('landing.heroDesc')}
                    </p>
                    <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
                        <Link to="/register" className="btn-primary flex items-center gap-2 text-base py-3.5 px-8">
                            {t('landing.registerNow')} <ArrowRight size={18} />
                        </Link>
                        <a href="#plans" className="btn-outline flex items-center gap-2 text-base py-3.5 px-8">{t('landing.viewPlans')}</a>
                    </div>
                    <div className="flex items-center justify-center gap-8 md:gap-16 mt-14 pt-10 border-t border-primary-700/30">
                        {[['110', t('landing.stats.seats')], ['2', t('landing.stats.shifts')], ['₹400', t('landing.stats.startingFrom')]].map(([val, label]) => (
                            <div key={label} className="text-center">
                                <div className="font-display text-3xl font-bold text-amber-400">{val}</div>
                                <div className="text-primary-400 text-sm mt-1">{label}</div>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* Features */}
            <section className="py-20 px-6 md:px-12">
                <div className="max-w-5xl mx-auto">
                    <div className="text-center mb-14">
                        <h2 className="font-display text-4xl font-bold text-white mb-3">{t('landing.features.title')}</h2>
                        <p className="text-primary-400">{t('landing.features.subtitle')}</p>
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
                        {features.map(({ icon: Icon, title, desc }) => (
                            <div key={title} className="card p-6 hover:border-amber-400/30 transition-all duration-300 group">
                                <div className="w-12 h-12 rounded-xl bg-amber-500/10 border border-amber-400/20 flex items-center justify-center mb-4 group-hover:bg-amber-500/20 transition-all">
                                    <Icon size={22} className="text-amber-400" />
                                </div>
                                <h3 className="text-white font-semibold mb-2">{title}</h3>
                                <p className="text-primary-400 text-sm leading-relaxed">{desc}</p>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* Gallery slideshow */}
            {GALLERY_PHOTOS.length > 0 && (
                <section className="py-20">
                    <div className="max-w-5xl mx-auto px-6 md:px-12 mb-10 text-center">
                        <h2 className="font-display text-4xl font-bold text-white mb-3">{t('landing.gallery.title')}</h2>
                        <p className="text-primary-400">{t('landing.gallery.subtitle')}</p>
                    </div>
                    <PhotoSlideshow photos={GALLERY_PHOTOS} />
                </section>
            )}

            {/* Our Location */}
            <section className="py-20 px-6 md:px-12">
                <div className="max-w-4xl mx-auto text-center mb-10">
                    <h2 className="font-display text-4xl font-bold text-white mb-3">{t('landing.location.title')}</h2>
                    <p className="text-primary-400">{t('landing.location.subtitle')}</p>
                </div>
                <div className="max-w-4xl mx-auto rounded-2xl overflow-hidden border border-primary-700/50 shadow-xl">
                    <iframe
                        title="Library Location"
                        src="https://maps.google.com/maps?q=B-199+Malviya+Nagar+Alwar+Rajasthan&output=embed&z=16"
                        width="100%"
                        height="400"
                        style={{ border: 0, display: 'block' }}
                        allowFullScreen
                        loading="lazy"
                        referrerPolicy="no-referrer-when-downgrade"
                    />
                </div>
                <p className="text-center text-primary-400 mt-4 text-sm">B-199, Malviya Nagar, Alwar</p>
            </section>

            {/* Plans */}
            <section id="plans" className="py-20 px-6 md:px-12 bg-primary-900/30">
                <div className="max-w-5xl mx-auto">
                    <div className="text-center mb-14">
                        <h2 className="font-display text-4xl font-bold text-white mb-3">{t('landing.plans.title')}</h2>
                        <p className="text-primary-400">{t('landing.plans.subtitle')}</p>
                    </div>

                    {/* Half Day Plans */}
                    <div className="mb-14">
                        <p className="text-amber-400 font-semibold text-center mb-6 uppercase tracking-widest text-xs">{t('landing.plans.halfDaySection')}</p>
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                            {halfDayPlans.map((plan) => (
                                <div key={plan.key} className={`card p-6 ${plan.badge ? 'border-primary-500/60 relative' : ''}`}>
                                    {plan.badge && (
                                        <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-4 py-1 bg-primary-600 text-white text-xs font-bold rounded-full">{plan.badge}</div>
                                    )}
                                    <div className={`inline-flex px-3 py-1 rounded-lg bg-gradient-to-r ${plan.color} text-white text-xs font-semibold mb-4`}>{t(`landing.plans.${plan.key}.name`)}</div>
                                    <div className="flex items-end gap-1 mb-5">
                                        <span className="font-display text-4xl font-bold text-white">{plan.price}</span>
                                        <span className="text-primary-400 mb-1 text-sm">{plan.period}</span>
                                    </div>
                                    <ul className="space-y-2.5 mb-6">
                                        {plan.features.map((f) => (
                                            <li key={f} className="flex items-start gap-2.5 text-sm text-primary-300">
                                                <CheckCircle2 size={15} className="text-amber-400 mt-0.5 shrink-0" />{f}
                                            </li>
                                        ))}
                                    </ul>
                                    <Link to="/login" className="w-full block text-center py-2.5 rounded-xl font-semibold transition-all text-sm border border-primary-600/50 hover:border-primary-400 text-white">
                                        {t('landing.plans.getStarted')}
                                    </Link>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Full Day Plans */}
                    <div>
                        <p className="text-amber-400 font-semibold text-center mb-6 uppercase tracking-widest text-xs">{t('landing.plans.fullDaySection')}</p>
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                            {fullDayPlans.map((plan) => (
                                <div key={plan.key} className={`card p-6 ${plan.popular ? 'border-amber-400/50 relative' : plan.badge ? 'border-amber-400/30 relative' : ''}`}>
                                    {plan.popular && (
                                        <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-4 py-1 bg-amber-500 text-primary-900 text-xs font-bold rounded-full">{t('landing.plans.popular')}</div>
                                    )}
                                    {plan.badge && (
                                        <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-4 py-1 bg-amber-600 text-white text-xs font-bold rounded-full">{plan.badge}</div>
                                    )}
                                    <div className={`inline-flex px-3 py-1 rounded-lg bg-gradient-to-r ${plan.color} text-white text-xs font-semibold mb-4`}>{t(`landing.plans.${plan.key}.name`)}</div>
                                    <div className="flex items-end gap-1 mb-5">
                                        <span className="font-display text-4xl font-bold text-white">{plan.price}</span>
                                        <span className="text-primary-400 mb-1 text-sm">{plan.period}</span>
                                    </div>
                                    <ul className="space-y-2.5 mb-6">
                                        {plan.features.map((f) => (
                                            <li key={f} className="flex items-start gap-2.5 text-sm text-primary-300">
                                                <CheckCircle2 size={15} className="text-amber-400 mt-0.5 shrink-0" />{f}
                                            </li>
                                        ))}
                                    </ul>
                                    <Link to="/login" className={`w-full block text-center py-2.5 rounded-xl font-semibold transition-all text-sm
                                        ${plan.popular ? 'bg-amber-500 hover:bg-amber-400 text-primary-900' : 'border border-amber-600/50 hover:border-amber-400 text-white'}`}>
                                        {t('landing.plans.getStarted')}
                                    </Link>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </section>

            <footer className="border-t border-primary-700/30 bg-primary-900/50 px-6 py-12">
                <div className="max-w-5xl mx-auto">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mb-8">
                        <div>
                            <div className="flex items-center gap-2 mb-3">
                                <div className="w-8 h-8 bg-amber-500 rounded-lg flex items-center justify-center">
                                    <BookOpen size={15} className="text-primary-900" />
                                </div>
                                <span className="font-display font-bold text-white">Target Zone Library</span>
                            </div>
                            <p className="text-primary-500 text-sm">{t('landing.footer.tagline')}</p>
                        </div>

                        <div>
                            <h4 className="text-white font-semibold text-xs uppercase tracking-wider mb-3">Company</h4>
                            <ul className="space-y-2">
                                <li><Link to="/about" className="text-primary-400 hover:text-amber-400 text-sm transition-colors">About Us</Link></li>
                            </ul>
                        </div>

                        <div>
                            <h4 className="text-white font-semibold text-xs uppercase tracking-wider mb-3">Legal</h4>
                            <ul className="space-y-2">
                                <li><Link to="/terms" className="text-primary-400 hover:text-amber-400 text-sm transition-colors">Terms & Conditions</Link></li>
                                <li><Link to="/privacy-policy" className="text-primary-400 hover:text-amber-400 text-sm transition-colors">Privacy Policy</Link></li>
                                <li><Link to="/refund-policy" className="text-primary-400 hover:text-amber-400 text-sm transition-colors">Return & Refund Policy</Link></li>
                                <li><Link to="/cancellation-policy" className="text-primary-400 hover:text-amber-400 text-sm transition-colors">Cancellation Policy</Link></li>
                            </ul>
                        </div>
                    </div>

                    <div className="border-t border-primary-700/30 pt-6 text-center">
                        <p className="text-primary-500 text-xs">© 2025 TARGET ZONE. All rights reserved.</p>
                    </div>
                </div>
            </footer>
        </div>
    )
}
