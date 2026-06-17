import { Link } from 'react-router-dom'
import { BookOpen } from 'lucide-react'
import { Helmet } from 'react-helmet-async'

export default function PrivacyPolicyPage() {
    return (
        <div className="min-h-screen bg-[#0d1b4b] text-white">
            <Helmet>
                <title>Privacy Policy | Target Zone Library</title>
                <meta name="description" content="Privacy policy for Target Zone Library — how we collect, use, and protect your personal data." />
            </Helmet>
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
                <h1 className="font-display text-4xl font-bold text-white mb-2">Privacy Policy</h1>
                <p className="text-primary-400 text-sm mb-8">Last updated: June 2025</p>

                <div className="card p-8 space-y-6 text-primary-300 text-sm leading-relaxed">
                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">1. Information We Collect</h2>
                        <p>When you register or use our services, we collect the following information: full name, mobile number, email address (optional), date of birth, gender, residential address, and Aadhaar card document (optional, for identity verification purposes).</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">2. How We Use Your Information</h2>
                        <p>Your personal information is used to: create and manage your membership account, process seat bookings, send booking confirmations and renewal reminders via WhatsApp and email, verify your identity when required, and maintain records as required by applicable law.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">3. Data Sharing</h2>
                        <p>We do not sell, trade, or rent your personal information to third parties. We may share data with trusted service providers — namely Razorpay (payment processing), Twilio (WhatsApp/SMS notifications), and SendGrid (email notifications) — solely to deliver the services you have subscribed to.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">4. Data Security</h2>
                        <p>We implement appropriate technical and organizational measures to protect your personal data against unauthorized access, alteration, disclosure, or destruction. All payment transactions are encrypted and processed through Razorpay's secure payment gateway.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">5. Data Retention</h2>
                        <p>We retain your personal data for as long as your account remains active or as required for legal, regulatory, and business purposes. You may request deletion of your account and associated data by contacting us directly.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">6. Cookies & Local Storage</h2>
                        <p>Our website uses your browser's local storage to keep you signed in between sessions. We do not use third-party tracking cookies or advertising cookies.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">7. Your Rights</h2>
                        <p>You have the right to access, correct, or request deletion of your personal data at any time. To exercise any of these rights, please contact us at the address below.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">8. Changes to This Policy</h2>
                        <p>We may update this Privacy Policy from time to time. Any changes will be reflected on this page with an updated date. We encourage you to review this policy periodically.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">9. Contact</h2>
                        <p>For privacy-related queries, please contact:<br />
                        TARGET ZONE, B-199, Malviya Nagar, Alwar (301001), Rajasthan<br />
                        Phone: 6003494209</p>
                    </section>
                </div>
            </div>
        </div>
    )
}
