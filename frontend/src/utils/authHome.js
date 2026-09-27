// Where a logged-in user lands — shared by App.jsx's GuestRoute (bounces
// already-authenticated users off the login pages) and LandingPage's nav.
export function homePathFor(user) {
    return user?.role === 'ADMIN' ? '/admin/seats' : '/student/dashboard'
}
