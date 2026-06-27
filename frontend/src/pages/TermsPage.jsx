import { Link } from 'react-router-dom'
import { BookOpen } from 'lucide-react'
import { Helmet } from 'react-helmet-async'

export default function TermsPage() {
    return (
        <div className="min-h-screen bg-[#0d1b4b] text-white">
            <Helmet>
                <title>Terms &amp; Conditions | Target Zone Library</title>
                <meta name="description" content="Terms and conditions for using Target Zone Library seat booking services in Alwar, Rajasthan." />
                <link rel="canonical" href="https://targetzone.co.in/terms" />
                <meta property="og:type" content="website" />
                <meta property="og:url" content="https://targetzone.co.in/terms" />
                <meta property="og:title" content="Terms &amp; Conditions | Target Zone Library" />
                <meta property="og:description" content="Terms and conditions for using Target Zone Library seat booking services in Alwar, Rajasthan." />
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
                <h1 className="font-display text-4xl font-bold text-white mb-2">Terms & Conditions</h1>
                <p className="text-primary-400 text-sm mb-8">Last updated: June 2025</p>

                <div className="card p-8 space-y-6 text-primary-300 text-sm leading-relaxed">
                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">1. Introduction</h2>
                        <p>This website is operated by TARGET ZONE. By accessing or using our services, you agree to be bound by these Terms and Conditions. Please read them carefully before using the platform.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">2. Services</h2>
                        <p>Target Zone Library provides reserved study-space memberships at our physical library premises located at B-199, Malviya Nagar, Alwar (301001), Rajasthan. Our services include seat booking, shift-based access (Morning, Evening, or Full Day), and associated digital facilities.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">3. Membership & Eligibility</h2>
                        <p>Membership is open to all students and individuals above the age of 14 years. Registration requires a valid mobile number. Each registered account is for individual use only and must not be shared with others.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">4. Payments</h2>
                        <p>All membership fees are payable in advance. Payments may be made online via Razorpay or in cash at the library front desk (admin-assisted). Prices are listed in Indian Rupees (INR) and are inclusive of all applicable charges.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">5. Library Rules</h2>
                        <p>Members are required to follow all library rules and guidelines, including maintaining silence in study areas, keeping phones on silent mode, and refraining from bringing food to study seats. Violation of rules may result in membership cancellation without refund.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">6. Seat Booking</h2>
                        <p>Seats are reserved for the duration of your active membership. Seat changes may be requested through the app subject to availability. Target Zone reserves the right to reassign seats in exceptional circumstances with prior notice.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">7. Limitation of Liability</h2>
                        <p>Target Zone Library is not responsible for loss, theft, or damage to personal belongings on the premises. Members are advised to use the provided lockers for valuables. The library management shall not be liable for any indirect or consequential losses.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">8. Amendments</h2>
                        <p>TARGET ZONE reserves the right to update these Terms & Conditions at any time. Continued use of the service after any changes constitutes your acceptance of the revised terms.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">9. Contact</h2>
                        <p>For questions regarding these terms, please contact us at:<br />
                        TARGET ZONE, B-199, Malviya Nagar, Alwar (301001), Rajasthan<br />
                        Phone: +91 81329 78111</p>
                    </section>
                </div>
            </div>
        </div>
    )
}
