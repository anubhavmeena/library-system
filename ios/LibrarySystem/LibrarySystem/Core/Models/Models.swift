import Foundation

// Only ever decoded (it's the response envelope every backend call returns) —
// never encoded — so T only needs Decodable, not the full Codable this used to
// require. That stricter bound broke every generic request<T: Decodable>() call
// site, since T there is only guaranteed Decodable, not Encodable.
struct ApiResponse<T: Decodable>: Decodable {
    let success: Bool
    let message: String?
    let data: T?
}

// Rust's rust_decimal::Decimal (this backend builds with the "serde-with-str"
// feature, for lossless precision) always serializes as a JSON string
// ("1234.50"), never a bare number — but Foundation's JSONDecoder won't
// auto-coerce a string into a Double. Every money field decoded from the
// backend goes through these instead of a plain decode(Double.self, ...) /
// decodeIfPresent(Double.self, ...), which previously threw a type-mismatch
// DecodingError the moment they were reached, failing the ENTIRE containing
// struct's decode (e.g. the admin dashboard falling back to all-zero stats).
extension KeyedDecodingContainer {
    func decodeMoney(forKey key: Key) throws -> Double {
        if let d = try? decode(Double.self, forKey: key) { return d }
        let s = try decode(String.self, forKey: key)
        guard let d = Double(s) else {
            throw DecodingError.dataCorruptedError(forKey: key, in: self, debugDescription: "Not a valid decimal string: \(s)")
        }
        return d
    }

    func decodeMoneyIfPresent(forKey key: Key) throws -> Double? {
        if let d = try? decodeIfPresent(Double.self, forKey: key) { return d }
        guard let s = try decodeIfPresent(String.self, forKey: key) else { return nil }
        guard let d = Double(s) else {
            throw DecodingError.dataCorruptedError(forKey: key, in: self, debugDescription: "Not a valid decimal string: \(s)")
        }
        return d
    }
}

struct User: Codable, Identifiable, Equatable {
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

    // auth-service's login/register/admin-login responses embed a slimmer
    // UserInfoDto (id, name, mobile, email, role, photoUrl only) than the full
    // user-service profile returned by /users/me — isActive and the other
    // fields below simply aren't present in that shape. The default
    // synthesized Decodable init has no fallback for a genuinely absent
    // isActive key (it's non-optional), which threw "The data couldn't be
    // read because it is missing" on every login. Decode defensively instead.
    enum CodingKeys: String, CodingKey {
        case id, name, mobile, email, role, isActive, photoUrl
        case fatherName, address, gender, dateOfBirth, aadhaarUrl
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id          = try container.decode(String.self, forKey: .id)
        name        = try container.decode(String.self, forKey: .name)
        mobile      = try container.decodeIfPresent(String.self, forKey: .mobile) ?? ""
        email       = try container.decodeIfPresent(String.self, forKey: .email)
        role        = try container.decode(String.self, forKey: .role)
        isActive    = try container.decodeIfPresent(Bool.self, forKey: .isActive) ?? true
        photoUrl    = try container.decodeIfPresent(String.self, forKey: .photoUrl)
        fatherName  = try container.decodeIfPresent(String.self, forKey: .fatherName)
        address     = try container.decodeIfPresent(String.self, forKey: .address)
        gender      = try container.decodeIfPresent(String.self, forKey: .gender)
        dateOfBirth = try container.decodeIfPresent(String.self, forKey: .dateOfBirth)
        aadhaarUrl  = try container.decodeIfPresent(String.self, forKey: .aadhaarUrl)
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

struct Membership: Codable, Identifiable, Equatable {
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
    let duesAmount: Double?  // non-null while status == GRACE

    var displayAmount: Double { amountPaid ?? planPrice ?? 0 }

    init(
        id: String = "", userId: String = "", planId: String = "",
        planName: String = "", planType: String = "", seatNumber: String = "",
        shift: String = "", startDate: String = "", endDate: String = "",
        status: String = "", amountPaid: Double? = nil, planPrice: Double? = nil,
        duesAmount: Double? = nil
    ) {
        self.id = id; self.userId = userId; self.planId = planId
        self.planName = planName; self.planType = planType
        self.seatNumber = seatNumber; self.shift = shift
        self.startDate = startDate; self.endDate = endDate
        self.status = status; self.amountPaid = amountPaid; self.planPrice = planPrice
        self.duesAmount = duesAmount
    }

    private enum CodingKeys: String, CodingKey {
        case id, userId, planId, planName, planType, seatNumber, shift
        case startDate, endDate, status, amountPaid, planPrice, duesAmount
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id         = try c.decode(String.self, forKey: .id)
        userId     = try c.decode(String.self, forKey: .userId)
        planId     = try c.decode(String.self, forKey: .planId)
        planName   = try c.decode(String.self, forKey: .planName)
        planType   = try c.decode(String.self, forKey: .planType)
        seatNumber = try c.decode(String.self, forKey: .seatNumber)
        shift      = try c.decode(String.self, forKey: .shift)
        startDate  = try c.decode(String.self, forKey: .startDate)
        endDate    = try c.decode(String.self, forKey: .endDate)
        status     = try c.decode(String.self, forKey: .status)
        amountPaid = try c.decodeMoneyIfPresent(forKey: .amountPaid)
        planPrice  = try c.decodeMoneyIfPresent(forKey: .planPrice)
        duesAmount = try c.decodeMoneyIfPresent(forKey: .duesAmount)
    }
}

struct Plan: Codable, Identifiable {
    let id: String
    let name: String
    let description: String?   // DB column is nullable (TEXT, no NOT NULL constraint)
    let price: Double
    let planType: String
    let durationDays: Int
    let isActive: Bool

    // The Java backend's PlanDto used a bare `boolean isActive`, which Lombok/Jackson
    // serialize under the JavaBean-stripped key "active" rather than "isActive" — but
    // the currently-deployed rust-backend's PlanWithFee derives `#[serde(rename_all =
    // "camelCase")]` with no such override, so its actual JSON key is "isActive".
    // Decoded defensively (like every other isActive field in this file) so a future
    // backend swap degrades to a default rather than failing every plan's decode.
    enum CodingKeys: String, CodingKey {
        case id, name, description, price, planType, durationDays, isActive
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id           = try c.decode(String.self, forKey: .id)
        name         = try c.decode(String.self, forKey: .name)
        description  = try c.decodeIfPresent(String.self, forKey: .description)
        price        = try c.decodeMoney(forKey: .price)
        planType     = try c.decode(String.self, forKey: .planType)
        durationDays = try c.decode(Int.self, forKey: .durationDays)
        isActive     = try c.decodeIfPresent(Bool.self, forKey: .isActive) ?? true
    }
}

struct Seat: Codable, Identifiable {
    let id: String?
    let seatNumber: String
    let row: String
    let isBooked: Bool
    let studentName: String?
    let studentMobile: String?
    let membershipEnd: String?

    // seat-service's SeatDto sends this field as "rowLabel", not "row" — the
    // required, non-optional `row` key was never actually present in the
    // response, so every seat in the array failed to decode (on top of the
    // separate SeatAvailability wrapper-object mismatch fixed in
    // SeatRepository.getAvailability).
    enum CodingKeys: String, CodingKey {
        case id, seatNumber, isBooked, studentName, studentMobile, membershipEnd
        case row = "rowLabel"
    }

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
    let studentId: String?      // userId — already sent by SeatMapDto.SeatInfoDto, just wasn't decoded here
    let studentName: String?
    let studentMobile: String?
    let studentGender: String?
    let shift: String?
    let membershipEnd: String?
}

struct PaymentOrder: Codable, Equatable {
    let orderId: String
    let membershipId: String
    let amount: Double
    let currency: String?
    let gateway: String?          // "CASHFREE" | "RAZORPAY"
    let paymentSessionId: String? // Cashfree only
    let razorpayKeyId: String?    // Razorpay only

    private enum CodingKeys: String, CodingKey {
        case orderId, membershipId, amount, currency, gateway, paymentSessionId, razorpayKeyId
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        orderId          = try c.decode(String.self, forKey: .orderId)
        membershipId     = try c.decode(String.self, forKey: .membershipId)
        amount           = try c.decodeMoney(forKey: .amount)
        currency         = try c.decodeIfPresent(String.self, forKey: .currency)
        gateway          = try c.decodeIfPresent(String.self, forKey: .gateway)
        paymentSessionId = try c.decodeIfPresent(String.self, forKey: .paymentSessionId)
        razorpayKeyId    = try c.decodeIfPresent(String.self, forKey: .razorpayKeyId)
    }
}

struct AdminStats: Codable {
    let totalStudents: Int
    let activeMemberships: Int
    let expiredMemberships: Int
    let expiringThisWeek: Int
    let orphanedSeatMemberships: Int
    let totalSeats: Int
    let occupiedSeats: Int
    let availableSeats: Int
    let revenueToday: Double
    let revenueThisMonth: Double
    let paymentsThisMonth: Int
    let totalVisitors: Int
    let visitorsToday: Int

    init(
        totalStudents: Int = 0, activeMemberships: Int = 0, expiredMemberships: Int = 0,
        expiringThisWeek: Int = 0, orphanedSeatMemberships: Int = 0,
        totalSeats: Int = 0, occupiedSeats: Int = 0, availableSeats: Int = 0,
        revenueToday: Double = 0, revenueThisMonth: Double = 0, paymentsThisMonth: Int = 0,
        totalVisitors: Int = 0, visitorsToday: Int = 0
    ) {
        self.totalStudents = totalStudents; self.activeMemberships = activeMemberships
        self.expiredMemberships = expiredMemberships; self.expiringThisWeek = expiringThisWeek
        self.orphanedSeatMemberships = orphanedSeatMemberships
        self.totalSeats = totalSeats; self.occupiedSeats = occupiedSeats
        self.availableSeats = availableSeats; self.revenueToday = revenueToday
        self.revenueThisMonth = revenueThisMonth; self.paymentsThisMonth = paymentsThisMonth
        self.totalVisitors = totalVisitors; self.visitorsToday = visitorsToday
    }

    // Defensive decode: the default synthesized init has no fallback for a
    // missing/renamed key on these non-optional numeric fields, so ANY single
    // field drifting from the backend's DashboardDto (as activeStudents did —
    // an iOS-only field the backend never actually returned) fails the WHOLE
    // decode, silently leaving every stat at 0 rather than just the one that
    // doesn't match. Missing fields now degrade to 0 individually instead.
    enum CodingKeys: String, CodingKey {
        case totalStudents, activeMemberships, expiredMemberships, expiringThisWeek
        case orphanedSeatMemberships, totalSeats, occupiedSeats, availableSeats
        case revenueToday, revenueThisMonth, paymentsThisMonth, totalVisitors, visitorsToday
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        totalStudents           = try c.decodeIfPresent(Int.self, forKey: .totalStudents) ?? 0
        activeMemberships       = try c.decodeIfPresent(Int.self, forKey: .activeMemberships) ?? 0
        expiredMemberships      = try c.decodeIfPresent(Int.self, forKey: .expiredMemberships) ?? 0
        expiringThisWeek        = try c.decodeIfPresent(Int.self, forKey: .expiringThisWeek) ?? 0
        orphanedSeatMemberships = try c.decodeIfPresent(Int.self, forKey: .orphanedSeatMemberships) ?? 0
        totalSeats              = try c.decodeIfPresent(Int.self, forKey: .totalSeats) ?? 0
        occupiedSeats           = try c.decodeIfPresent(Int.self, forKey: .occupiedSeats) ?? 0
        availableSeats          = try c.decodeIfPresent(Int.self, forKey: .availableSeats) ?? 0
        revenueToday            = try c.decodeMoneyIfPresent(forKey: .revenueToday) ?? 0
        revenueThisMonth        = try c.decodeMoneyIfPresent(forKey: .revenueThisMonth) ?? 0
        paymentsThisMonth       = try c.decodeIfPresent(Int.self, forKey: .paymentsThisMonth) ?? 0
        totalVisitors           = try c.decodeIfPresent(Int.self, forKey: .totalVisitors) ?? 0
        visitorsToday           = try c.decodeIfPresent(Int.self, forKey: .visitorsToday) ?? 0
    }
}

struct StudentSummary: Codable, Identifiable {
    let id: String
    let name: String
    let mobile: String
    let email: String?
    let isActive: Bool
    let photoUrl: String?
    let joinedAt: String?
    let membershipId: String?
    let membershipStatus: String?
    let seatNumber: String?
    let shift: String?
    let membershipStart: String?
    let membershipEnd: String?
    let planName: String?
    let daysRemaining: Int
    let paymentMode: String?
    let pendingAmount: Double?
    let duesAmount: Double?
    let displayStatus: String?

    // Defensive decode: the backend's StudentDto (GET /admin/students) has no
    // isActive field at all — the admin service has no student enable/disable
    // concept (see backend/admin-service/CLAUDE.md). Decoding every student in
    // the list against a non-optional isActive with no fallback failed the
    // WHOLE array, so the students screen always showed "No students found."
    enum CodingKeys: String, CodingKey {
        case id, name, mobile, email, isActive, photoUrl, joinedAt, membershipId, membershipStatus
        case seatNumber, shift, membershipStart, membershipEnd, planName, daysRemaining
        case paymentMode, pendingAmount, duesAmount, displayStatus
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id               = try c.decode(String.self, forKey: .id)
        name             = try c.decode(String.self, forKey: .name)
        mobile           = try c.decode(String.self, forKey: .mobile)
        email            = try c.decodeIfPresent(String.self, forKey: .email)
        isActive         = try c.decodeIfPresent(Bool.self, forKey: .isActive) ?? true
        photoUrl         = try c.decodeIfPresent(String.self, forKey: .photoUrl)
        joinedAt         = try c.decodeIfPresent(String.self, forKey: .joinedAt)
        membershipId     = try c.decodeIfPresent(String.self, forKey: .membershipId)
        membershipStatus = try c.decodeIfPresent(String.self, forKey: .membershipStatus)
        seatNumber       = try c.decodeIfPresent(String.self, forKey: .seatNumber)
        shift            = try c.decodeIfPresent(String.self, forKey: .shift)
        membershipStart  = try c.decodeIfPresent(String.self, forKey: .membershipStart)
        membershipEnd    = try c.decodeIfPresent(String.self, forKey: .membershipEnd)
        planName         = try c.decodeIfPresent(String.self, forKey: .planName)
        daysRemaining    = try c.decodeIfPresent(Int.self, forKey: .daysRemaining) ?? 0
        paymentMode      = try c.decodeIfPresent(String.self, forKey: .paymentMode)
        pendingAmount    = try c.decodeMoneyIfPresent(forKey: .pendingAmount)
        duesAmount       = try c.decodeMoneyIfPresent(forKey: .duesAmount)
        displayStatus    = try c.decodeIfPresent(String.self, forKey: .displayStatus)
    }
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
    let duesAmount: Double? // overdue grace-period dues, distinct from pendingAmount above
    let displayStatus: String? // NEW | PAID | PENDING | GRACE | EXPIRED | RELEASED

    // Defensive decode: same reason as StudentSummary — the backend's
    // StudentDto (GET /admin/students/{id}) has no isActive field at all.
    enum CodingKeys: String, CodingKey {
        case id, name, mobile, email, address, gender, dateOfBirth, photoUrl
        case aadhaarUrl, isActive, joinedAt, membershipId, membershipPlanId
        case planName, planType, seatNumber, shift, membershipStart, membershipEnd
        case membershipStatus, daysRemaining, paymentMode, pendingAmount
        case duesAmount, displayStatus
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id               = try c.decode(String.self, forKey: .id)
        name             = try c.decode(String.self, forKey: .name)
        mobile           = try c.decode(String.self, forKey: .mobile)
        email            = try c.decodeIfPresent(String.self, forKey: .email)
        address          = try c.decodeIfPresent(String.self, forKey: .address)
        gender           = try c.decodeIfPresent(String.self, forKey: .gender)
        dateOfBirth      = try c.decodeIfPresent(String.self, forKey: .dateOfBirth)
        photoUrl         = try c.decodeIfPresent(String.self, forKey: .photoUrl)
        aadhaarUrl       = try c.decodeIfPresent(String.self, forKey: .aadhaarUrl)
        isActive         = try c.decodeIfPresent(Bool.self, forKey: .isActive) ?? true
        joinedAt         = try c.decodeIfPresent(String.self, forKey: .joinedAt)
        membershipId     = try c.decodeIfPresent(String.self, forKey: .membershipId)
        membershipPlanId = try c.decodeIfPresent(String.self, forKey: .membershipPlanId)
        planName         = try c.decodeIfPresent(String.self, forKey: .planName)
        planType         = try c.decodeIfPresent(String.self, forKey: .planType)
        seatNumber       = try c.decodeIfPresent(String.self, forKey: .seatNumber)
        shift            = try c.decodeIfPresent(String.self, forKey: .shift)
        membershipStart  = try c.decodeIfPresent(String.self, forKey: .membershipStart)
        membershipEnd    = try c.decodeIfPresent(String.self, forKey: .membershipEnd)
        membershipStatus = try c.decodeIfPresent(String.self, forKey: .membershipStatus)
        daysRemaining    = try c.decodeIfPresent(Int.self, forKey: .daysRemaining) ?? 0
        paymentMode      = try c.decodeIfPresent(String.self, forKey: .paymentMode)
        pendingAmount    = try c.decodeMoneyIfPresent(forKey: .pendingAmount)
        duesAmount       = try c.decodeMoneyIfPresent(forKey: .duesAmount)
        displayStatus    = try c.decodeIfPresent(String.self, forKey: .displayStatus)
    }
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

        private enum CodingKeys: String, CodingKey { case date, amount, count }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            date   = try c.decode(String.self, forKey: .date)
            amount = try c.decodeMoney(forKey: .amount)
            count  = try c.decode(Int.self, forKey: .count)
        }
    }

    init() {
        fromDate = ""; toDate = ""; totalRevenue = 0; totalTransactions = 0
        halfDayRevenue = 0; fullDayRevenue = 0; dailyBreakdown = []
    }

    // halfDayRevenue/fullDayRevenue can arrive as JSON null from older backend
    // builds that never populated them — decodeIfPresent treats a null value the
    // same as a missing key, so this defaults to 0 instead of failing the whole decode.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        fromDate           = try c.decode(String.self, forKey: .fromDate)
        toDate             = try c.decode(String.self, forKey: .toDate)
        totalRevenue       = try c.decodeMoney(forKey: .totalRevenue)
        totalTransactions  = try c.decode(Int.self, forKey: .totalTransactions)
        halfDayRevenue     = try c.decodeMoneyIfPresent(forKey: .halfDayRevenue) ?? 0
        fullDayRevenue     = try c.decodeMoneyIfPresent(forKey: .fullDayRevenue) ?? 0
        dailyBreakdown     = try c.decode([DailyRevenue].self, forKey: .dailyBreakdown)
    }

    private enum CodingKeys: String, CodingKey {
        case fromDate, toDate, totalRevenue, totalTransactions, halfDayRevenue, fullDayRevenue, dailyBreakdown
    }
}

struct DailyPayment: Codable {
    let studentName: String
    let studentMobile: String
    let amount: Double
    let paymentGateway: String?
    let referenceId: String?
    let paidAt: String

    private enum CodingKeys: String, CodingKey {
        case studentName, studentMobile, amount, paymentGateway, referenceId, paidAt
    }

    // studentMobile and paidAt are both nullable on the backend
    // (DailyPaymentItem.student_mobile/paid_at: Option<...>) though these fields'
    // types stay non-optional — default rather than failing the whole day's
    // payment list over one null value.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        studentName    = try c.decode(String.self, forKey: .studentName)
        studentMobile  = try c.decodeIfPresent(String.self, forKey: .studentMobile) ?? ""
        amount         = try c.decodeMoney(forKey: .amount)
        paymentGateway = try c.decodeIfPresent(String.self, forKey: .paymentGateway)
        referenceId    = try c.decodeIfPresent(String.self, forKey: .referenceId)
        paidAt         = try c.decodeIfPresent(String.self, forKey: .paidAt) ?? ""
    }
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

    private enum CodingKeys: String, CodingKey {
        case id, membershipId, amount, paymentGateway, gatewayOrderId, gatewayPaymentId, status, paidAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id               = try c.decode(String.self, forKey: .id)
        membershipId     = try c.decodeIfPresent(String.self, forKey: .membershipId)
        amount           = try c.decodeMoney(forKey: .amount)
        paymentGateway   = try c.decodeIfPresent(String.self, forKey: .paymentGateway)
        gatewayOrderId   = try c.decodeIfPresent(String.self, forKey: .gatewayOrderId)
        gatewayPaymentId = try c.decodeIfPresent(String.self, forKey: .gatewayPaymentId)
        status           = try c.decode(String.self, forKey: .status)
        paidAt           = try c.decodeIfPresent(String.self, forKey: .paidAt)
    }
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

        private enum CodingKeys: String, CodingKey { case description, amount }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            description = try c.decode(String.self, forKey: .description)
            amount      = try c.decodeMoney(forKey: .amount)
        }
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

    // The backend's MonthlyExpenseWithItems names this field "total", not
    // "totalExpense" — a non-optional-field key mismatch that failed the whole
    // decode on its own, independent of the Decimal-as-string issue every
    // amount field here also needs decodeMoney(...) for.
    private enum CodingKeys: String, CodingKey {
        case year, month, waterTankerQty, waterTankerPrice, electricityBill
        case internetBill, miscellaneous, totalExpense = "total", miscItems
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        year             = try c.decode(Int.self, forKey: .year)
        month            = try c.decode(Int.self, forKey: .month)
        waterTankerQty   = try c.decode(Int.self, forKey: .waterTankerQty)
        waterTankerPrice = try c.decodeMoney(forKey: .waterTankerPrice)
        electricityBill  = try c.decodeMoney(forKey: .electricityBill)
        internetBill     = try c.decodeMoney(forKey: .internetBill)
        miscellaneous    = try c.decodeMoney(forKey: .miscellaneous)
        totalExpense     = try c.decodeMoney(forKey: .totalExpense)
        miscItems        = try c.decodeIfPresent([MiscItem].self, forKey: .miscItems)
    }
}

// MARK: - Inbox / Email

// InboxSummaryDto/InboxMessageDto's `isRead` is a primitive `boolean` with no
// @JsonProperty override, so Lombok's plain `isRead()` getter serializes to the
// JSON key "read", not "isRead" — same Jackson boolean-getter stripping that broke
// Plan.isActive ("active") above. Mapped explicitly rather than hitting the same
// non-optional decode crash on every inbox open.
struct InboxSummary: Codable, Identifiable, Hashable {
    var id: Int { messageNumber }
    let messageNumber: Int
    let from: String
    let subject: String
    let date: String
    let isRead: Bool

    enum CodingKeys: String, CodingKey {
        case messageNumber, from, subject, date
        case isRead = "read"
    }
}

struct InboxMessage: Codable {
    let messageNumber: Int
    let from: String
    let subject: String
    let date: String
    let isRead: Bool
    let body: String

    enum CodingKeys: String, CodingKey {
        case messageNumber, from, subject, date, body
        case isRead = "read"
    }
}

// MARK: - Gallery

struct GalleryPhoto: Codable, Identifiable {
    let id: String
    let url: String
    let caption: String?
    let uploadedAt: String?
}

// MARK: - Seat History

struct SeatHistoryEntry: Codable, Identifiable {
    let membershipId: String
    let studentName: String?
    let studentMobile: String?
    let shift: String?
    let startDate: String?
    let endDate: String?
    let status: String?
    let seatNumber: String? // populated on the per-student endpoint only
    let planName: String?

    var id: String { membershipId }
}

// MARK: - Payment Breakdown

struct PaymentBreakdownItem: Codable {
    let amount: Double
    let count: Int

    private enum CodingKeys: String, CodingKey { case amount, count }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        amount = try c.decodeMoney(forKey: .amount)
        count  = try c.decode(Int.self, forKey: .count)
    }
}

// MARK: - App Settings

struct AppSettings: Codable {
    var wifiName: String?
    var wifiPassword: String?
    var upiId: String?
    var graceDays: Int?
    var convenienceFee: Double?
    var waterTankerRate: Double?
    let updatedAt: String?

    init(wifiName: String? = nil, wifiPassword: String? = nil, upiId: String? = nil, graceDays: Int? = nil,
         convenienceFee: Double? = nil, waterTankerRate: Double? = nil, updatedAt: String? = nil) {
        self.wifiName = wifiName; self.wifiPassword = wifiPassword; self.upiId = upiId; self.graceDays = graceDays
        self.convenienceFee = convenienceFee; self.waterTankerRate = waterTankerRate
        self.updatedAt = updatedAt
    }

    private enum CodingKeys: String, CodingKey {
        case wifiName, wifiPassword, upiId, graceDays, convenienceFee, waterTankerRate, updatedAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        wifiName        = try c.decodeIfPresent(String.self, forKey: .wifiName)
        wifiPassword    = try c.decodeIfPresent(String.self, forKey: .wifiPassword)
        upiId           = try c.decodeIfPresent(String.self, forKey: .upiId)
        graceDays       = try c.decodeIfPresent(Int.self, forKey: .graceDays)
        convenienceFee  = try c.decodeMoneyIfPresent(forKey: .convenienceFee)
        waterTankerRate = try c.decodeMoneyIfPresent(forKey: .waterTankerRate)
        updatedAt       = try c.decodeIfPresent(String.self, forKey: .updatedAt)
    }
}

// Student-submitted UPI payment claim (screenshot + self-reported amount),
// reviewed by an admin on the "Verify Payments" screen. Verifying auto-clears
// the student's dues/pending fee server-side; the screenshot itself is
// uploaded by the student via the web /pay page, never by this app.
struct PaymentClaim: Codable, Identifiable {
    let id: String
    let userId: String
    let studentName: String
    let studentMobile: String?
    let claimType: String        // "DUES" | "PENDING_FEE"
    let membershipId: String?
    let amountClaimed: Double
    let screenshotUrl: String
    let status: String           // "PENDING" | "VERIFIED" | "REJECTED"
    let createdAt: String
    let reviewedAt: String?
    let reviewedBy: String?

    private enum CodingKeys: String, CodingKey {
        case id, userId, studentName, studentMobile, claimType, membershipId
        case amountClaimed, screenshotUrl, status, createdAt, reviewedAt, reviewedBy
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id             = try c.decode(String.self, forKey: .id)
        userId         = try c.decode(String.self, forKey: .userId)
        studentName    = try c.decode(String.self, forKey: .studentName)
        studentMobile  = try c.decodeIfPresent(String.self, forKey: .studentMobile)
        claimType      = try c.decode(String.self, forKey: .claimType)
        membershipId   = try c.decodeIfPresent(String.self, forKey: .membershipId)
        amountClaimed  = try c.decodeMoney(forKey: .amountClaimed)
        screenshotUrl  = try c.decode(String.self, forKey: .screenshotUrl)
        status         = try c.decode(String.self, forKey: .status)
        createdAt      = try c.decode(String.self, forKey: .createdAt)
        reviewedAt     = try c.decodeIfPresent(String.self, forKey: .reviewedAt)
        reviewedBy     = try c.decodeIfPresent(String.self, forKey: .reviewedBy)
    }
}

// MARK: - Bulk Import Result

struct ImportResult: Codable {
    let totalRows: Int
    let imported: Int
    let skipped: Int
    let errors: [RowError]?

    struct RowError: Codable, Identifiable {
        var id: Int { row }
        let row: Int
        let name: String?
        let phone: String?
        let reason: String
    }
}

struct ManualImportWithPhotoResponse: Decodable {
    let message: String
    let photoUrl: String?
}

// MARK: - Notification Settings

struct NotificationSetting: Codable, Identifiable {
    var id: String { notificationKey }
    let notificationKey: String
    var sendToStudent: Bool
    var sendToAdmin: Bool
    let studentEditable: Bool // false = "Send to Student" is fixed, UI must disable the toggle
    let adminEditable: Bool   // false = "Send to Admin" is fixed, UI must disable the toggle
    let hindiEditable: Bool   // false = this notification has no Hindi translation option at all
    var hindiEnabled: Bool
    var hindiTextStudent: String?
    var hindiTextAdmin: String?
    let updatedAt: String?
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

    private enum CodingKeys: String, CodingKey {
        case id, membershipId, amount, paymentGateway, gatewayOrderId, gatewayPaymentId, status, createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id               = try c.decode(String.self, forKey: .id)
        membershipId     = try c.decodeIfPresent(String.self, forKey: .membershipId)
        amount           = try c.decodeMoney(forKey: .amount)
        paymentGateway   = try c.decodeIfPresent(String.self, forKey: .paymentGateway)
        gatewayOrderId   = try c.decodeIfPresent(String.self, forKey: .gatewayOrderId)
        gatewayPaymentId = try c.decodeIfPresent(String.self, forKey: .gatewayPaymentId)
        status           = try c.decode(String.self, forKey: .status)
        createdAt        = try c.decodeIfPresent(String.self, forKey: .createdAt)
    }
}
