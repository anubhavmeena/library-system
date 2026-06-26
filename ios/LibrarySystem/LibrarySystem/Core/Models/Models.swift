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

struct AdminContact: Codable {
    let name: String?
    let mobile: String?
    let email: String?
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
    let amountPaid: Double?  // from payment verification response
    let planPrice: Double?   // from membership service MembershipDto

    var displayAmount: Double { amountPaid ?? planPrice ?? 0 }

    init(
        id: String = "", userId: String = "", planId: String = "",
        planName: String = "", planType: String = "", seatNumber: String = "",
        shift: String = "", startDate: String = "", endDate: String = "",
        status: String = "", amountPaid: Double? = nil, planPrice: Double? = nil
    ) {
        self.id = id; self.userId = userId; self.planId = planId
        self.planName = planName; self.planType = planType
        self.seatNumber = seatNumber; self.shift = shift
        self.startDate = startDate; self.endDate = endDate
        self.status = status; self.amountPaid = amountPaid; self.planPrice = planPrice
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
    let totalVisitors: Int
    let visitorsToday: Int

    init(
        totalStudents: Int = 0, activeStudents: Int = 0,
        activeMemberships: Int = 0, expiringThisWeek: Int = 0,
        totalSeats: Int = 0, occupiedSeats: Int = 0, availableSeats: Int = 0,
        revenueToday: Double = 0, revenueThisMonth: Double = 0, paymentsThisMonth: Int = 0,
        totalVisitors: Int = 0, visitorsToday: Int = 0
    ) {
        self.totalStudents = totalStudents; self.activeStudents = activeStudents
        self.activeMemberships = activeMemberships; self.expiringThisWeek = expiringThisWeek
        self.totalSeats = totalSeats; self.occupiedSeats = occupiedSeats
        self.availableSeats = availableSeats; self.revenueToday = revenueToday
        self.revenueThisMonth = revenueThisMonth; self.paymentsThisMonth = paymentsThisMonth
        self.totalVisitors = totalVisitors; self.visitorsToday = visitorsToday
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
    let pendingAmount: Double?
}

// Wrapper for the paginated students response from GET /admin/students
struct StudentListResponse: Codable {
    let students: [StudentSummary]
    let total: Int
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
    let membershipPlanId: String?
    let planName: String?
    let planType: String?
    let seatNumber: String?
    let shift: String?
    let membershipStart: String?
    let membershipEnd: String?
    let membershipStatus: String?
    let daysRemaining: Int
    let paymentMode: String?
    let pendingAmount: Double?
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

// MARK: - Broadcast History

struct BroadcastHistory: Codable, Identifiable {
    let id: String
    let message: String
    let recipientCount: Int
    let sentAt: String
}

// MARK: - Revenue Reports

struct RevenueReport: Codable {
    let fromDate: String
    let toDate: String
    let totalRevenue: Double
    let totalTransactions: Int
    let halfDayRevenue: Double
    let fullDayRevenue: Double
    let dailyBreakdown: [DailyRevenue]

    struct DailyRevenue: Codable {
        let date: String
        let amount: Double
        let count: Int
    }

    init() {
        fromDate = ""; toDate = ""; totalRevenue = 0; totalTransactions = 0
        halfDayRevenue = 0; fullDayRevenue = 0; dailyBreakdown = []
    }
}

struct DailyPayment: Codable {
    let studentName: String
    let studentMobile: String
    let amount: Double
    let paymentGateway: String?
    let referenceId: String?
    let paidAt: String
}

// MARK: - Payment History (for admin student detail)

struct PaymentHistoryItem: Codable, Identifiable {
    let id: String
    let membershipId: String?
    let amount: Double
    let paymentGateway: String?
    let gatewayOrderId: String?
    let gatewayPaymentId: String?
    let status: String
    let paidAt: String?
}

// MARK: - Expenses

struct MonthlyExpense: Codable, Equatable {
    let year: Int
    let month: Int
    let waterTankerQty: Int
    let waterTankerPrice: Double
    let electricityBill: Double
    let internetBill: Double
    let miscellaneous: Double
    let totalExpense: Double
    let miscItems: [MiscItem]?

    struct MiscItem: Codable, Equatable {
        let description: String
        let amount: Double
    }

    init(
        year: Int = Calendar.current.component(.year, from: Date()),
        month: Int = Calendar.current.component(.month, from: Date()),
        waterTankerQty: Int = 0, waterTankerPrice: Double = 0,
        electricityBill: Double = 0, internetBill: Double = 0,
        miscellaneous: Double = 0, totalExpense: Double = 0,
        miscItems: [MiscItem]? = nil
    ) {
        self.year = year; self.month = month
        self.waterTankerQty = waterTankerQty; self.waterTankerPrice = waterTankerPrice
        self.electricityBill = electricityBill; self.internetBill = internetBill
        self.miscellaneous = miscellaneous; self.totalExpense = totalExpense
        self.miscItems = miscItems
    }
}

// MARK: - Inbox / Email

struct InboxSummary: Codable, Identifiable, Hashable {
    var id: Int { messageNumber }
    let messageNumber: Int
    let from: String
    let subject: String
    let date: String
    let isRead: Bool
}

struct InboxMessage: Codable {
    let messageNumber: Int
    let from: String
    let subject: String
    let date: String
    let isRead: Bool
    let body: String
}

// MARK: - Gallery

struct GalleryPhoto: Codable, Identifiable {
    let id: String
    let url: String
    let caption: String?
    let uploadedAt: String?
}

// MARK: - Student Payment History

struct StudentPayment: Codable, Identifiable {
    let id: String
    let membershipId: String?
    let amount: Double
    let paymentGateway: String?
    let gatewayOrderId: String?
    let gatewayPaymentId: String?
    let status: String
    let createdAt: String?
}
