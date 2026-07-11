import Foundation

enum HTTPMethod: String {
    case GET, POST, PATCH, DELETE
}

struct Endpoint {
    let path: String
    let method: HTTPMethod
    let body: Data?
    let queryItems: [URLQueryItem]

    init(path: String, method: HTTPMethod = .GET, body: Data? = nil, queryItems: [URLQueryItem] = []) {
        self.path = path
        self.method = method
        self.body = body
        self.queryItems = queryItems
    }
}

extension Endpoint {
    // MARK: - Auth
    static func sendOtp(_ req: SendOtpRequest) -> Endpoint {
        Endpoint(path: "auth/send-otp", method: .POST, body: encode(req))
    }
    static func verifyOtp(_ req: VerifyOtpRequest) -> Endpoint {
        Endpoint(path: "auth/verify-otp", method: .POST, body: encode(req))
    }
    static func register(_ req: RegisterRequest) -> Endpoint {
        Endpoint(path: "auth/register", method: .POST, body: encode(req))
    }
    static func login(_ req: LoginRequest) -> Endpoint {
        Endpoint(path: "auth/login", method: .POST, body: encode(req))
    }
    static func adminLogin(_ req: AdminLoginRequest) -> Endpoint {
        Endpoint(path: "auth/admin/login", method: .POST, body: encode(req))
    }

    // MARK: - User
    static let getProfile     = Endpoint(path: "users/me")
    static let getAdminContact = Endpoint(path: "users/admin-contact")
    static func updateProfile(_ req: UpdateProfileRequest) -> Endpoint {
        Endpoint(path: "users/me", method: .PATCH, body: encode(req))
    }

    // MARK: - Membership
    static let getMyMembership    = Endpoint(path: "memberships/my")
    static let getMembershipHistory = Endpoint(path: "memberships/my/all")
    static let getQueuedMembership  = Endpoint(path: "memberships/my/queued")
    static let getPlans             = Endpoint(path: "plans")

    // MARK: - Payments
    static func createOrder(_ req: CreateOrderRequest) -> Endpoint {
        Endpoint(path: "payments/create-order", method: .POST, body: encode(req))
    }
    static func verifyPayment(_ req: VerifyPaymentRequest) -> Endpoint {
        Endpoint(path: "payments/verify", method: .POST, body: encode(req))
    }
    static let createDuesOrder = Endpoint(path: "payments/dues/create-order", method: .POST)
    static func verifyDuesPayment(_ req: VerifyPaymentRequest) -> Endpoint {
        Endpoint(path: "payments/dues/verify", method: .POST, body: encode(req))
    }

    // MARK: - Seats
    static func getSeatAvailability(shift: String, date: String? = nil) -> Endpoint {
        var items = [URLQueryItem(name: "shift", value: shift)]
        if let date { items.append(URLQueryItem(name: "date", value: date)) }
        return Endpoint(path: "seats/availability", queryItems: items)
    }
    static func bookSeat(_ req: BookSeatRequest) -> Endpoint {
        Endpoint(path: "seats/book", method: .POST, body: encode(req))
    }

    // MARK: - Feedback
    static let getMyFeedback = Endpoint(path: "users/feedback/my")
    static func submitFeedback(_ req: SubmitFeedbackRequest) -> Endpoint {
        Endpoint(path: "users/feedback", method: .POST, body: encode(req))
    }

    // MARK: - Admin: Dashboard & Students
    static let getAdminStats = Endpoint(path: "admin/dashboard")
    static func getStudents(page: Int = 0, size: Int = 20, status: String? = nil,
                            membershipStatus: String? = nil, search: String? = nil) -> Endpoint {
        var items = [URLQueryItem(name: "page", value: "\(page)"),
                     URLQueryItem(name: "size", value: "\(size)")]
        if let s = status { items.append(URLQueryItem(name: "status", value: s)) }
        if let ms = membershipStatus { items.append(URLQueryItem(name: "membershipStatus", value: ms)) }
        if let q = search { items.append(URLQueryItem(name: "search", value: q)) }
        return Endpoint(path: "admin/students", queryItems: items)
    }
    static func getStudentDetail(id: String) -> Endpoint {
        Endpoint(path: "admin/students/\(id)")
    }
    static func getStudentPayments(userId: String) -> Endpoint {
        Endpoint(path: "admin/students/\(userId)/payments")
    }
    static func toggleStudentStatus(id: String, req: ToggleStatusRequest) -> Endpoint {
        Endpoint(path: "admin/students/\(id)/status", method: .PATCH, body: encode(req))
    }
    static func updateStudent(id: String, req: UpdateStudentRequest) -> Endpoint {
        Endpoint(path: "admin/students/\(id)", method: .PATCH, body: encode(req))
    }
    static func deleteStudent(id: String) -> Endpoint {
        Endpoint(path: "admin/students/\(id)", method: .DELETE)
    }
    static let getStudentsWithPendingFees = Endpoint(path: "admin/students/pending-fees")
    static let getStudentsWithOrphanedSeats = Endpoint(path: "admin/students/orphaned-seats")
    static func getStudentSeatHistory(userId: String) -> Endpoint {
        Endpoint(path: "admin/students/\(userId)/seat-history")
    }
    static func clearPendingFees(userId: String, req: ClearAmountRequest) -> Endpoint {
        Endpoint(path: "admin/students/\(userId)/clear-pending-fees", method: .PATCH, body: encode(req))
    }
    static let getStudentsInGraceWithDues = Endpoint(path: "admin/students/grace-dues")

    // MARK: - Admin: Memberships & Seats
    static func changeSeat(membershipId: String, req: ChangeSeatRequest) -> Endpoint {
        Endpoint(path: "admin/memberships/\(membershipId)/seat", method: .PATCH, body: encode(req))
    }
    static func updateMembershipPlan(membershipId: String, req: UpdateMembershipPlanRequest) -> Endpoint {
        Endpoint(path: "admin/memberships/\(membershipId)/plan", method: .PATCH, body: encode(req))
    }
    static func getExpiringMemberships(withinDays: Int = 7) -> Endpoint {
        Endpoint(path: "admin/memberships/expiring",
                 queryItems: [URLQueryItem(name: "withinDays", value: "\(withinDays)")])
    }
    static func createCashMembership(_ req: CreateCashMembershipRequest) -> Endpoint {
        Endpoint(path: "admin/memberships/cash", method: .POST, body: encode(req))
    }
    static func getAdminSeatMap(shift: String, date: String? = nil) -> Endpoint {
        var items = [URLQueryItem(name: "shift", value: shift)]
        if let date { items.append(URLQueryItem(name: "date", value: date)) }
        return Endpoint(path: "admin/seats/map", queryItems: items)
    }
    static func clearDues(membershipId: String, req: ClearAmountRequest) -> Endpoint {
        Endpoint(path: "admin/memberships/\(membershipId)/clear-dues", method: .PATCH, body: encode(req))
    }
    static func releaseSeat(membershipId: String, req: ReleaseSeatRequest) -> Endpoint {
        Endpoint(path: "admin/memberships/\(membershipId)/release", method: .PATCH, body: encode(req))
    }
    static func markPending(membershipId: String, req: MarkPendingRequest) -> Endpoint {
        Endpoint(path: "admin/memberships/\(membershipId)/mark-pending", method: .PATCH, body: encode(req))
    }
    static func markGrace(membershipId: String) -> Endpoint {
        Endpoint(path: "admin/memberships/\(membershipId)/mark-grace", method: .PATCH)
    }
    static func renewSeat(membershipId: String) -> Endpoint {
        Endpoint(path: "admin/memberships/\(membershipId)/renew", method: .PATCH)
    }
    static let runExpiryCheck = Endpoint(path: "admin/memberships/run-expiry-check", method: .POST)
    static func getSeatHistory(seatNumber: String) -> Endpoint {
        Endpoint(path: "admin/seats/\(seatNumber)/history")
    }

    // MARK: - Admin: Reminders & Broadcast
    static func sendReminders(_ req: SendReminderRequest) -> Endpoint {
        Endpoint(path: "admin/reminders/send", method: .POST, body: encode(req))
    }
    static func sendPendingFeeReminders(_ req: SendReminderRequest) -> Endpoint {
        Endpoint(path: "admin/reminders/pending-fees", method: .POST, body: encode(req))
    }
    static func sendGraceDuesReminders(_ req: SendReminderRequest) -> Endpoint {
        Endpoint(path: "admin/reminders/grace-dues", method: .POST, body: encode(req))
    }
    static func sendBroadcast(_ req: BroadcastRequest) -> Endpoint {
        Endpoint(path: "admin/broadcast", method: .POST, body: encode(req))
    }
    static let getBroadcastHistory = Endpoint(path: "admin/broadcast/history")
    static func sendMessageToStudent(_ id: String, _ req: BroadcastRequest) -> Endpoint {
        Endpoint(path: "admin/students/\(id)/message", method: .POST, body: encode(req))
    }

    // MARK: - Admin: Notification Settings
    static let getNotificationSettings = Endpoint(path: "admin/notification-settings")
    static func updateNotificationSetting(key: String, req: UpdateNotificationSettingRequest) -> Endpoint {
        Endpoint(path: "admin/notification-settings/\(key)", method: .PATCH, body: encode(req))
    }

    // MARK: - Admin: Feedback
    static let getAllFeedback = Endpoint(path: "admin/feedback")
    static func updateFeedback(id: String, req: UpdateFeedbackRequest) -> Endpoint {
        Endpoint(path: "admin/feedback/\(id)", method: .PATCH, body: encode(req))
    }

    // MARK: - Admin: Revenue Reports
    static func getRevenueReport(from: String, to: String) -> Endpoint {
        Endpoint(path: "admin/reports/revenue",
                 queryItems: [URLQueryItem(name: "from", value: from),
                               URLQueryItem(name: "to",   value: to)])
    }
    static func getDailyPayments(date: String) -> Endpoint {
        Endpoint(path: "admin/reports/payments/daily",
                 queryItems: [URLQueryItem(name: "date", value: date)])
    }
    static func getPaymentBreakdown(from: String, to: String) -> Endpoint {
        Endpoint(path: "admin/reports/payments/breakdown",
                 queryItems: [URLQueryItem(name: "from", value: from),
                               URLQueryItem(name: "to",   value: to)])
    }

    // MARK: - Admin: App Settings
    static let getAppSettings = Endpoint(path: "admin/settings")
    static func saveAppSettings(_ req: SaveAppSettingsRequest) -> Endpoint {
        Endpoint(path: "admin/settings", method: .POST, body: encode(req))
    }

    // MARK: - Admin: UPI Pay Links & Payment Verification
    static func createPayLink(_ req: PayLinkRequest) -> Endpoint {
        Endpoint(path: "admin/pay-links", method: .POST, body: encode(req))
    }
    static func getPaymentClaims(status: String? = nil) -> Endpoint {
        var items: [URLQueryItem] = []
        if let status { items.append(URLQueryItem(name: "status", value: status)) }
        return Endpoint(path: "admin/payment-claims", queryItems: items)
    }
    static func reviewPaymentClaim(id: String, req: PaymentClaimReviewRequest) -> Endpoint {
        Endpoint(path: "admin/payment-claims/\(id)", method: .PATCH, body: encode(req))
    }

    // MARK: - Admin: Expenses
    static func getExpenses(year: Int? = nil, month: Int? = nil) -> Endpoint {
        var items: [URLQueryItem] = []
        if let y = year  { items.append(URLQueryItem(name: "year",  value: "\(y)")) }
        if let m = month { items.append(URLQueryItem(name: "month", value: "\(m)")) }
        return Endpoint(path: "admin/expenses", queryItems: items)
    }
    static func saveExpenses(_ req: SaveExpenseRequest) -> Endpoint {
        Endpoint(path: "admin/expenses", method: .POST, body: encode(req))
    }

    // MARK: - Admin: Inbox
    static let getInbox = Endpoint(path: "admin/inbox")
    static func getInboxMessage(_ number: Int) -> Endpoint {
        Endpoint(path: "admin/inbox/\(number)")
    }
    static func replyToMessage(_ number: Int, req: ReplyRequest) -> Endpoint {
        Endpoint(path: "admin/inbox/\(number)/reply", method: .POST, body: encode(req))
    }
    static func deleteInboxMessage(_ number: Int) -> Endpoint {
        Endpoint(path: "admin/inbox/\(number)", method: .DELETE)
    }

    // MARK: - Student payments
    static let getMyPayments = Endpoint(path: "payments/my")

    // MARK: - Gallery
    static let getGallery = Endpoint(path: "gallery")
    static func deleteGalleryPhoto(id: String) -> Endpoint {
        Endpoint(path: "gallery/\(id)", method: .DELETE)
    }

    // MARK: - Admin: Import
    static func importSingleStudent(_ req: ManualImportRequest) -> Endpoint {
        Endpoint(path: "admin/students/import/single", method: .POST, body: encode(req))
    }

    // MARK: - Seat Assistance
    static let callAdmin = Endpoint(path: "memberships/my/call-admin", method: .POST)

    // MARK: - ID Card
    static let downloadIdCard = Endpoint(path: "memberships/my/id-card")

    // MARK: - Helper
    private static func encode<T: Encodable>(_ value: T) -> Data? {
        try? JSONEncoder().encode(value)
    }
}
