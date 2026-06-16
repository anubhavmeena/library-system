import { Link } from 'react-router-dom'
import { BookOpen } from 'lucide-react'

export default function RefundPolicyPage() {
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
                <h1 className="font-display text-4xl font-bold text-white mb-2">Return & Refund Policy</h1>
                <p className="text-primary-400 text-sm mb-8">Last updated: June 2025</p>

                <div className="card p-8 space-y-6 text-primary-300 text-sm leading-relaxed">
                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">1. No Refund Policy</h2>
                        <p>All membership purchases at Target Zone Library are <strong className="text-white">final and non-refundable</strong>. Once a membership is activated and payment is confirmed, no refunds will be issued under any circumstances.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">2. Scope</h2>
                        <p>This policy applies to all membership plans offered by TARGET ZONE, including monthly, 6-month, and annual plans for both Half Day and Full Day access.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">3. Failed or Duplicate Payments</h2>
                        <p>In the event of a failed transaction where your bank or payment provider has debited your account but no membership was activated, please contact us immediately with proof of payment. Such cases will be investigated and resolved within 5–7 business days. Any amount wrongly debited will be refunded to the original payment method.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">4. Service Disruption</h2>
                        <p>In the rare event that the library is forced to permanently close or is unable to provide services for an extended period due to circumstances beyond our control, we will assess refund eligibility for any unused membership period on a case-by-case basis.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">5. Refund Mode</h2>
                        <p>Any refunds approved under exceptional circumstances (as described in Sections 3 and 4 above) will be processed to the original payment method used at the time of purchase — either to the bank/UPI account, or in cash for cash transactions, within 7–10 business days.</p>
                    </section>

                    <section>
                        <h2 className="text-amber-400 font-semibold text-base mb-2">6. Contact</h2>
                        <p>For any payment-related concerns, please contact us at:<br />
                        TARGET ZONE, B-199, Malviya Nagar, Alwar (301001), Rajasthan<br />
                        Phone: 6003494209</p>
                    </section>
                </div>
            </div>
        </div>
    )
}
