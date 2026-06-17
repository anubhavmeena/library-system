import { Routes, Route, Navigate } from 'react-router-dom'
import { useSelector } from 'react-redux'

import LandingPage              from './pages/LandingPage'
import TermsPage                from './pages/TermsPage'
import PrivacyPolicyPage        from './pages/PrivacyPolicyPage'
import RefundPolicyPage         from './pages/RefundPolicyPage'
import CancellationPolicyPage   from './pages/CancellationPolicyPage'
import AboutUsPage              from './pages/AboutUsPage'
import LoginPage          from './pages/auth/LoginPage'
import RegisterPage       from './pages/auth/RegisterPage'
import AdminLoginPage     from './pages/admin/AdminLoginPage'

import StudentLayout      from './layouts/StudentLayout'
import DashboardPage      from './pages/student/DashboardPage'
import BookingPage        from './pages/student/BookingPage'
import MembershipPage     from './pages/student/MembershipPage'
import ProfilePage        from './pages/student/ProfilePage'
import FacilitiesPage     from './pages/student/FacilitiesPage'
import PaymentSuccessPage from './pages/student/PaymentSuccessPage'

import AdminLayout        from './layouts/AdminLayout'
import AdminDashboardPage from './pages/admin/AdminDashboardPage'
import AdminStudentsPage  from './pages/admin/AdminStudentsPage'
import AdminSeatsPage     from './pages/admin/AdminSeatsPage'
import AdminRemindersPage from './pages/admin/AdminRemindersPage'
import AdminFeedbackPage  from './pages/admin/AdminFeedbackPage'
import AdminCreateMembershipPage from './pages/admin/AdminCreateMembershipPage'
import AdminBroadcastPage from './pages/admin/AdminBroadcastPage'
import AdminExpensesPage  from './pages/admin/AdminExpensesPage'
import AdminImportPage    from './pages/admin/AdminImportPage'

import FeedbackPage       from './pages/student/FeedbackPage'

function ProtectedRoute({ children, role }) {
    const { user, token } = useSelector(s => s.auth)
    if (!token || !user) return <Navigate to="/login" replace />
    if (role && user.role !== role) return <Navigate to="/" replace />
    return children
}

export default function App() {
    return (
        <Routes>
            <Route path="/"                    element={<LandingPage />} />
            <Route path="/about"               element={<AboutUsPage />} />
            <Route path="/terms"               element={<TermsPage />} />
            <Route path="/privacy-policy"      element={<PrivacyPolicyPage />} />
            <Route path="/refund-policy"       element={<RefundPolicyPage />} />
            <Route path="/cancellation-policy" element={<CancellationPolicyPage />} />
            <Route path="/login"       element={<LoginPage />} />
            <Route path="/register"    element={<RegisterPage />} />
            <Route path="/admin/login" element={<AdminLoginPage />} />

            <Route path="/student" element={
                <ProtectedRoute role="STUDENT"><StudentLayout /></ProtectedRoute>
            }>
                <Route index                   element={<Navigate to="/student/dashboard" replace />} />
                <Route path="dashboard"        element={<DashboardPage />} />
                <Route path="booking"          element={<BookingPage />} />
                <Route path="membership"       element={<MembershipPage />} />
                <Route path="profile"          element={<ProfilePage />} />
                <Route path="facilities"       element={<FacilitiesPage />} />
                <Route path="payment-success"  element={<PaymentSuccessPage />} />
                <Route path="feedback"         element={<FeedbackPage />} />
            </Route>

            <Route path="/admin" element={
                <ProtectedRoute role="ADMIN"><AdminLayout /></ProtectedRoute>
            }>
                <Route index             element={<Navigate to="/admin/dashboard" replace />} />
                <Route path="dashboard"  element={<AdminDashboardPage />} />
                <Route path="students"   element={<AdminStudentsPage />} />
                <Route path="seats"      element={<AdminSeatsPage />} />
                <Route path="reminders"  element={<AdminRemindersPage />} />
                <Route path="feedback"         element={<AdminFeedbackPage />} />
                <Route path="memberships/new" element={<AdminCreateMembershipPage />} />
                <Route path="broadcast"       element={<AdminBroadcastPage />} />
                <Route path="expenses"        element={<AdminExpensesPage />} />
                <Route path="import"          element={<AdminImportPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    )
}