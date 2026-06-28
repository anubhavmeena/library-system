package model

type DashboardDTO struct {
	TotalStudents      int     `json:"totalStudents"`
	ActiveStudents     int     `json:"activeStudents"`
	ActiveMemberships  int     `json:"activeMemberships"`
	ExpiredMemberships int     `json:"expiredMemberships"`
	ExpiringThisWeek   int     `json:"expiringThisWeek"`
	TotalSeats         int     `json:"totalSeats"`
	OccupiedSeats      int     `json:"occupiedSeats"`
	AvailableSeats     int     `json:"availableSeats"`
	RevenueToday       float64 `json:"revenueToday"`
	RevenueThisMonth   float64 `json:"revenueThisMonth"`
	PaymentsThisMonth  int     `json:"paymentsThisMonth"`
	TotalVisitors      int     `json:"totalVisitors"`
	VisitorsToday      int     `json:"visitorsToday"`
}

type StudentDTO struct {
	ID               string   `json:"id"`
	Name             string   `json:"name"`
	Mobile           *string  `json:"mobile"`
	Email            *string  `json:"email"`
	Address          *string  `json:"address"`
	Gender           *string  `json:"gender"`
	DateOfBirth      *string  `json:"dateOfBirth"`
	PhotoURL         *string  `json:"photoUrl"`
	IsActive         bool     `json:"isActive"`
	JoinedAt         string   `json:"joinedAt"`
	MembershipID     *string  `json:"membershipId"`
	PlanName         *string  `json:"planName"`
	SeatNumber       *string  `json:"seatNumber"`
	Shift            *string  `json:"shift"`
	MembershipStart  *string  `json:"membershipStart"`
	MembershipEnd    *string  `json:"membershipEnd"`
	MembershipStatus *string  `json:"membershipStatus"`
	DaysRemaining    *int     `json:"daysRemaining"`
	PaymentMode      *string  `json:"paymentMode"`
	PendingAmount    float64  `json:"pendingAmount"`
}

type StudentListDTO struct {
	Students   []StudentDTO `json:"students"`
	Total      int          `json:"total"`
	Page       int          `json:"page"`
	Size       int          `json:"size"`
	TotalPages int          `json:"totalPages"`
}

type UpdateStatusRequest struct {
	IsActive bool `json:"isActive"`
}

type UpdateStudentRequest struct {
	Name        *string `json:"name"`
	Mobile      *string `json:"mobile"`
	Email       *string `json:"email"`
	Address     *string `json:"address"`
	Gender      *string `json:"gender"`
	DateOfBirth *string `json:"dateOfBirth"`
	JoinedAt    *string `json:"joinedAt"`
}

type SeatInfoDTO struct {
	SeatNumber    string  `json:"seatNumber"`
	IsOccupied    bool    `json:"isOccupied"`
	StudentName   *string `json:"studentName"`
	StudentMobile *string `json:"studentMobile"`
	StudentGender *string `json:"studentGender"`
	Shift         *string `json:"shift"`
	MembershipEnd *string `json:"membershipEnd"`
}

type SeatMapDTO struct {
	Shift         string                 `json:"shift"`
	Date          string                 `json:"date"`
	TotalSeats    int                    `json:"totalSeats"`
	OccupiedSeats int                    `json:"occupiedSeats"`
	AvailableSeats int                   `json:"availableSeats"`
	SeatsByRow    map[string][]SeatInfoDTO `json:"seatsByRow"`
}

type SendReminderRequest struct {
	UserIDs []string `json:"userIds"`
}

type BroadcastRequest struct {
	Message string `json:"message" validate:"required,min=5,max=1000"`
}

type BroadcastHistoryDTO struct {
	ID             string `json:"id"`
	Message        string `json:"message"`
	RecipientCount int    `json:"recipientCount"`
	SentAt         string `json:"sentAt"`
}

type UpdateFeedbackRequest struct {
	Status     *string `json:"status"`
	AdminNotes *string `json:"adminNotes"`
}

type FeedbackDTO struct {
	ID          string  `json:"id"`
	UserID      string  `json:"userId"`
	Type        string  `json:"type"`
	Subject     string  `json:"subject"`
	Description string  `json:"description"`
	Status      string  `json:"status"`
	AdminNotes  *string `json:"adminNotes"`
	CreatedAt   string  `json:"createdAt"`
	UpdatedAt   string  `json:"updatedAt"`
}

type RevenueReportDTO struct {
	FromDate         string           `json:"fromDate"`
	ToDate           string           `json:"toDate"`
	TotalRevenue     float64          `json:"totalRevenue"`
	TotalTransactions int             `json:"totalTransactions"`
	DailyBreakdown   []DailyRevenueDTO `json:"dailyBreakdown"`
}

type DailyRevenueDTO struct {
	Date   string  `json:"date"`
	Amount float64 `json:"amount"`
	Count  int     `json:"count"`
}

type PaymentBreakdownDTO struct {
	Amount float64 `json:"amount"`
	Count  int     `json:"count"`
}

type DailyPaymentDTO struct {
	StudentName   string  `json:"studentName"`
	StudentMobile string  `json:"studentMobile"`
	Amount        float64 `json:"amount"`
	PaymentGateway string `json:"paymentGateway"`
	ReferenceID   string  `json:"referenceId"`
	PaidAt        string  `json:"paidAt"`
}

type CreateCashMembershipRequest struct {
	StudentID     string  `json:"studentId" validate:"required"`
	PlanID        string  `json:"planId" validate:"required"`
	Shift         string  `json:"shift"`
	SeatNumber    string  `json:"seatNumber" validate:"required"`
	StartDate     string  `json:"startDate"`
	PaidAmount    float64 `json:"paidAmount"`
	PendingAmount float64 `json:"pendingAmount"`
}

type ChangeSeatRequest struct {
	SeatNumber string `json:"seatNumber" validate:"required"`
}

type ExpenseDTO struct {
	ID               *string          `json:"id"`
	Year             int              `json:"year"`
	Month            int              `json:"month"`
	WaterTankerQty   int              `json:"waterTankerQty"`
	WaterTankerPrice float64          `json:"waterTankerPrice"`
	ElectricityBill  float64          `json:"electricityBill"`
	InternetBill     float64          `json:"internetBill"`
	Miscellaneous    float64          `json:"miscellaneous"`
	TotalExpense     float64          `json:"totalExpense"`
	MiscItems        []MiscItemDTO    `json:"miscItems"`
}

type MiscItemDTO struct {
	Description string  `json:"description"`
	Amount      float64 `json:"amount"`
}

type SaveExpenseRequest struct {
	Year             int          `json:"year"`
	Month            int          `json:"month"`
	WaterTankerQty   int          `json:"waterTankerQty"`
	WaterTankerPrice float64      `json:"waterTankerPrice"`
	ElectricityBill  float64      `json:"electricityBill"`
	InternetBill     float64      `json:"internetBill"`
	MiscItems        []MiscItemDTO `json:"miscItems"`
}

type InboxSummaryDTO struct {
	MessageNumber uint32  `json:"messageNumber"`
	From          string  `json:"from"`
	Subject       string  `json:"subject"`
	Date          string  `json:"date"`
	IsRead        bool    `json:"isRead"`
}

type InboxMessageDTO struct {
	MessageNumber uint32  `json:"messageNumber"`
	From          string  `json:"from"`
	Subject       string  `json:"subject"`
	Date          string  `json:"date"`
	Body          string  `json:"body"`
}

type ReplyRequest struct {
	Body string `json:"body" validate:"required"`
}

type ManualStudentImportRequest struct {
	Name  string `json:"name" validate:"required"`
	Phone string `json:"phone" validate:"required"`
}

type ImportResultDTO struct {
	TotalRows int         `json:"totalRows"`
	Imported  int         `json:"imported"`
	Skipped   int         `json:"skipped"`
	Errors    []RowError  `json:"errors"`
}

type RowError struct {
	Row    int    `json:"row"`
	Name   string `json:"name"`
	Phone  string `json:"phone"`
	Reason string `json:"reason"`
}

type PaymentHistoryDTO struct {
	ID             string  `json:"id"`
	Amount         float64 `json:"amount"`
	PendingAmount  float64 `json:"pendingAmount"`
	PaymentGateway string  `json:"paymentGateway"`
	Status         string  `json:"status"`
	CreatedAt      string  `json:"createdAt"`
}
