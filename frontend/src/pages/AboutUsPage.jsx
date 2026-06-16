import { Link } from 'react-router-dom'
import { BookOpen, MapPin, Phone, Clock, Users } from 'lucide-react'

export default function AboutUsPage() {
    return (
        <div className="min-h-screen bg-[#0d1b4b] text-white">
            <nav className="fixed top-0 left-0 right-0 z-50 flex items-center justify-between px-6 md:px-12 py-4 bg-primary-900/80 backdrop-blur-xl border-b border-primary-700/20">
                <div className="flex items-center gap-3">
                    <div className="w-9 h-9 bg-amber-500 rounded-xl flex items-center justify-center shadow-lg shadow-amber-500/30">
                        <BookOpen size={18} className="text-primary-900" />
                    </div>
                    <span className="font-display font-bold text-white text-xl">Target Zone Library</span>
                </div>
                <Link to="/" className="text-primary-400 hover:text-white text-sm transition-colors">← Back to Home</Link>
            </nav>

            <div className="max-w-3xl mx-auto px-6 pt-28 pb-16">
                <h1 className="font-display text-4xl font-bold text-white mb-2">About Us</h1>
                <p className="text-primary-400 text-sm mb-8">Your dedicated space for learning and growth</p>

                {/* Mission card */}
                <div className="card p-8 mb-6">
                    <div className="flex items-center gap-3 mb-4">
                        <div className="w-10 h-10 bg-amber-500/20 rounded-xl flex items-center justify-center">
                            <BookOpen size={20} className="text-amber-400" />
                        </div>
                        <h2 className="font-display text-xl font-bold text-white">Who We Are</h2>
                    </div>
                    <p className="text-primary-300 text-sm leading-relaxed mb-4">
                        Target Zone Library is a premium study space in Malviya Nagar, Alwar, designed for students, competitive exam aspirants, and self-learners who need a focused, distraction-free environment to achieve their goals.
                    </p>
                    <p className="text-primary-300 text-sm leading-relaxed">
                        We provide 110 dedicated seats across 4 comfortable rows, high-speed Wi-Fi, air conditioning, power outlets at every seat, a refreshment zone, secure lockers, and much more — all under one roof, seven days a week, 365 days a year.
                    </p>
                </div>

                {/* Owner card */}
                <div className="card p-8 mb-6">
                    <div className="flex items-center gap-3 mb-4">
                        <div className="w-10 h-10 bg-amber-500/20 rounded-xl flex items-center justify-center">
                            <Users size={20} className="text-amber-400" />
                        </div>
                        <h2 className="font-display text-xl font-bold text-white">Our Team</h2>
                    </div>
                    <div className="flex items-start gap-4">
                        <div className="w-14 h-14 bg-primary-700 rounded-full flex items-center justify-center flex-shrink-0">
                            <span className="font-display text-xl font-bold text-amber-400">MM</span>
                        </div>
                        <div>
                            <p className="text-white font-semibold text-base">Malooki Meena</p>
                            <p className="text-amber-400 text-sm mb-2">Founder & Owner</p>
                            <p className="text-primary-300 text-sm leading-relaxed">
                                Malooki Meena founded Target Zone Library with a mission to provide every student in Alwar with access to a world-class study environment at an affordable price. The library reflects a commitment to education and community growth.
                            </p>
                        </div>
                    </div>
                </div>

                {/* Contact & Hours */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div className="card p-6">
                        <div className="flex items-center gap-3 mb-4">
                            <div className="w-9 h-9 bg-amber-500/20 rounded-xl flex items-center justify-center">
                                <MapPin size={18} className="text-amber-400" />
                            </div>
                            <h2 className="font-semibold text-white">Find Us</h2>
                        </div>
                        <p className="text-primary-300 text-sm leading-relaxed mb-3">
                            B-199, Malviya Nagar,<br />
                            Alwar (301001),<br />
                            Rajasthan, India
                        </p>
                        <div className="flex items-center gap-2 text-primary-300 text-sm">
                            <Phone size={14} className="text-amber-400" />
                            <a href="tel:6003494209" className="hover:text-amber-400 transition-colors">6003494209</a>
                        </div>
                    </div>

                    <div className="card p-6">
                        <div className="flex items-center gap-3 mb-4">
                            <div className="w-9 h-9 bg-amber-500/20 rounded-xl flex items-center justify-center">
                                <Clock size={18} className="text-amber-400" />
                            </div>
                            <h2 className="font-semibold text-white">Library Hours</h2>
                        </div>
                        <ul className="space-y-2 text-sm text-primary-300">
                            <li className="flex justify-between">
                                <span>Morning Shift</span>
                                <span className="text-white">6:00 AM – 2:00 PM</span>
                            </li>
                            <li className="flex justify-between">
                                <span>Evening Shift</span>
                                <span className="text-white">2:00 PM – 10:00 PM</span>
                            </li>
                            <li className="flex justify-between">
                                <span>Full Day</span>
                                <span className="text-white">6:00 AM – 10:00 PM</span>
                            </li>
                        </ul>
                        <p className="text-primary-500 text-xs mt-3">Open 7 days a week · 365 days a year</p>
                    </div>
                </div>
            </div>
        </div>
    )
}
