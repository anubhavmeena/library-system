import { Link } from 'react-router-dom'
import { BookOpen } from 'lucide-react'
import { Helmet } from 'react-helmet-async'

export default function CancellationPolicyPage() {
    return (
        <div className="min-h-screen bg-[#0d1b4b] text-white">
            <Helmet>
                <title>Cancellation Policy | Target Zone Library</title>
                <meta name="description" content="Cancellation policy for Target Zone Library seat bookings — how to cancel and what to expect." />
                <link rel="canonical" href="https://targetzone.co.in/cancellation-policy" />
                <meta property="og:type" content="website" />
                <meta property="og:url" content="https://targetzone.co.in/cancellation-policy" />
                <meta property="og:title" content="Cancellation Policy | Target Zone Library" />
                <meta property="og:description" content="Cancellation policy for Target Zone Library seat bookings — how to cancel and what to expect." />
                <meta property="og:image" content="https://targetzone.co.in/og-image.jpg" />
                <meta property="og:site_name" content="Target Zone Library" />
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
                <h1 className="font-display text-4xl font-bold text-white mb-2">Cancellation Policy</h1>
                <p className="text-primary-400 text-sm mb-8">Last updated: June 2025</p>

                <div className="card p-8 space-y-6 text-primary-300 text-sm leading-relaxed">
                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">1. Cancel Anytime</h2>
                        <p>Members may cancel their membership at any time with <strong className="text-white">no notice period required</strong>. There are no lock-in obligations or cancellation fees.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">2. Effect of Cancellation</h2>
                        <p>Upon cancellation, your membership access will continue until the end of your current paid membership period. No further renewals will be charged after the current period ends. Your seat booking will be released at the end of your membership validity.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">3. How to Cancel</h2>
                        <p>To cancel your membership, please contact the library admin directly at the front desk or call us at +91 81329 78111. You may also raise a request through the in-app feedback/complaint section.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">4. Cancellation by TARGET ZONE</h2>
                        <p>TARGET ZONE reserves the right to cancel a membership without prior notice in cases of: violation of library rules, misconduct on premises, provision of false registration information, or any activity that disrupts the study environment for other members. In such cases, no refund will be issued.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">5. Refunds on Cancellation</h2>
                        <p>Cancellation does not entitle the member to a refund of any unused membership days. Please refer to our <Link to="/refund-policy" className="text-amber-400 hover:text-amber-300 underline">Return & Refund Policy</Link> for full details.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">6. Contact</h2>
                        <p>For cancellation requests or queries, please contact:<br />
                        TARGET ZONE, B-199, Malviya Nagar, Alwar (301001), Rajasthan<br />
                        Phone: +91 81329 78111</p>
                    </section>
                </div>
            </div>
        </div>
    )
}
