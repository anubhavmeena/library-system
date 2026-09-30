// "New" badge shows for the first 20 days after a student's first-ever
// membership starts (renewals don't count — see first_membership_start).
const NEW_MEMBER_DAYS = 20

export function isNewMember(firstStart) {
    if (!firstStart) return false
    const [y, m, d] = firstStart.split('-').map(Number)
    const today = new Date()
    const days = Math.floor((Date.UTC(today.getFullYear(), today.getMonth(), today.getDate()) - Date.UTC(y, m - 1, d)) / 86400000)
    return days >= 0 && days < NEW_MEMBER_DAYS
}
