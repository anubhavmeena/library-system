import Foundation

struct ApiResponse<T: Codable>: Codable {
    let success: Bool
    let message: String?
    let data: T?
}

struct User: Codable, Identifiable {
    let id: String
    let name: String
    let mobile: String
    let email: String?
    let role: String
    let isActive: Bool
    let photoUrl: String?
    let fatherName: String?
    let address: String?
    let gender: String?
    let dateOfBirth: String?
    let aadhaarUrl: String?

    init(
        id: String = "", name: String = "", mobile: String = "",
        email: String? = nil, role: String = "STUDENT", isActive: Bool = true,
        photoUrl: String? = nil, fatherName: String? = nil,
        address: String? = nil, gender: String? = nil,
        dateOfBirth: String? = nil, aadhaarUrl: String? = nil
    ) {
        self.id = id; self.name = name; self.mobile = mobile
        self.email = email; self.role = role; self.isActive = isActive
        self.photoUrl = photoUrl; self.fatherName = fatherName
        self.address = address; self.gender = gender
        self.dateOfBirth = dateOfBirth; self.aadhaarUrl = aadhaarUrl
    }
}

struct AuthResponse: Codable {
    let user: User
    let accessToken: String
}

struct OtpVerifyResponse: Codable {
    let sessionToken: String
    let newUser: Bool
}

struct Membership: Codable, Identifiable {
    let id: String
    let userId: String
    let planId: String
    let planName: String
    let planType: String
    let seatNumber: String
    let shift: String
    let startDate: String
    let endDate: String
    let status: String
    let amountPaid: Double

    init(
        id: String = "", userId: String = "", planId: String = "",
        planName: String = "", planType: String = "", seatNumber: String = "",
        shift: String = "", startDate: String = "", endDate: String = "",
        status: String = "", amountPaid: Double = 0.0
    ) {
        self.id = id; self.userId = userId; self.planId = planId
        self.planName = planName; self.planType = planType
        self.seatNumber = seatNumber; self.shift = shift
        self.startDate = startDate; self.endDate = endDate
        self.status = status; self.amountPaid = amountPaid
    }
}

struct Plan: Codable, Identifiable {
    let id: String
    let name: String
    let description: String
    let price: Double
    let planType: String
    let durationDays: Int
    let isActive: Bool
}

struct Seat: Codable, Identifiable {
    let id: String?
    let seatNumber: String
    let row: String
    let isBooked: Bool
    let studentName: String?
    let studentMobile: String?
    let membershipEnd: String?

    var rowLabel: String { row }
}

struct SeatAvailability: Codable {
    let shift: String
    let date: String
    let totalSeats: Int
    let bookedSeats: Int
    let availableSeats: Int
    let seats: [Seat]
}

struct SeatMapDto: Codable {
    let shift: String
    let date: String
    let totalSeats: Int
    let occupiedSeats: Int
    let availableSeats: Int
    let seatsByRow: [String: [SeatInfoItem]]
}

struct SeatInfoItem: Codable {
    let seatNumber: String
    let isOccupied: Bool
    let studentName: String?
    let studentMobile: String?
    let shift: String?
    let membershipEnd: String?
}

struct PaymentOrder: Codable {
    let orderId: String
    let membershipId: String
    let amount: Double
    let razorpayKeyId: String
}

struct AdminStats: Codable {
    let totalStudents: Int
    let activeStudents: Int
    let activeMemberships: Int
    let expiringThisWeek: Int
    let totalSeats: Int
    let occupiedSeats: Int
    let availableSeats: Int
    let revenueToday: Double
    let revenueThisMonth: Double
    let paymentsThisMonth: Int

    init(
        totalStudents: Int = 0, activeStudents: Int = 0,
        activeMemberships: Int = 0, expiringThisWeek: Int = 0,
        totalSeats: Int = 0, occupiedSeats: Int = 0, availableSeats: Int = 0,
        revenueToday: Double = 0, revenueThisMonth: Double = 0, paymentsThisMonth: Int = 0
    ) {
        self.totalStudents = totalStudents; self.activeStudents = activeStudents
        self.activeMemberships = activeMemberships; self.expiringThisWeek = expiringThisWeek
        self.totalSeats = totalSeats; self.occupiedSeats = occupiedSeats
        self.availableSeats = availableSeats; self.revenueToday = revenueToday
        self.revenueThisMonth = revenueThisMonth; self.paymentsThisMonth = paymentsThisMonth
    }
}

struct StudentSummary: Codable, Identifiable {
    let id: String
    let name: String
    let mobile: String
    let email: String?
    let isActive: Bool
    let membershipId: String?
    let membershipStatus: String?
    let seatNumber: String?
    let shift: String?
    let membershipStart: String?
    let endDate: String?
    let planName: String?
}

struct StudentDetail: Codable, Identifiable {
    let id: String
    let name: String
    let mobile: String
    let email: String?
    let address: String?
    let gender: String?
    let dateOfBirth: String?
    let photoUrl: String?
    let aadhaarUrl: String?
    let isActive: Bool
    let joinedAt: String?
    let membershipId: String?
    let planName: String?
    let planType: String?
    let seatNumber: String?
    let shift: String?
    let membershipStart: String?
    let membershipEnd: String?
    let membershipStatus: String?
    let daysRemaining: Int
    let paymentMode: String?
}

struct FeedbackItem: Codable, Identifiable {
    let id: String
    let userId: String?
    let type: String
    let subject: String
    let description: String
    let status: String
    let adminNotes: String?
    let createdAt: String
    let updatedAt: String?
    let studentName: String?
    let studentMobile: String?
}

struct ReminderStudent: Codable, Identifiable {
    let id: String
    let name: String
    let mobile: String
    let email: String?
    let endDate: String
    let daysLeft: Int
    let seatNumber: String?
    let shift: String?
}
