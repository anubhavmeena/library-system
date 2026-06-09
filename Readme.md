Architecture Overview
Browser / Mobile
│
▼
Frontend (React - port 3000)
│  All API calls go to /api/*
│  Nginx proxies to →
▼
API Gateway (port 8080)
│  Validates JWT
│  Injects X-User-Id, X-User-Role headers
│  Routes to downstream service
│
├──→ Auth Service        (8081)
├──→ User Service        (8082)
├──→ Membership Service  (8083)
├──→ Seat Service        (8084)
├──→ Notification Service(8085)  ← Kafka consumer only, no HTTP routing
└──→ Admin Service       (8086)
│
├── PostgreSQL (shared DB)
├── Redis      (OTP + seat cache)
└── Kafka      (async notifications)

Flow 1 — Student Registration
Student enters mobile number on RegisterPage.jsx
│
├─ Step 1: Send OTP
│   Frontend  →  POST /api/auth/send-otp
│                { contact: "9876543210", contactType: "MOBILE" }
│   API Gateway  →  validates no JWT needed (public route)
│                →  forwards to Auth Service :8081
│   Auth Service →  generates OTP ("123456" in dev)
│                →  stores in Redis:  KEY="otp:9876543210"  VALUE="123456"  TTL=5min
│                →  calls OtpService.sendSms() → Twilio API (or logs in dev)
│                →  returns { success: true, data: "OTP sent successfully" }
│
├─ Step 2: Verify OTP
│   Frontend  →  POST /api/auth/verify-otp
│                { contact: "9876543210", otp: "123456" }
│   Auth Service →  reads Redis KEY="otp:9876543210" → "123456"
│                →  compares → match ✅
│                →  deletes OTP from Redis
│                →  checks DB: user exists? → NO → isNewUser=true
│                →  generates sessionToken (UUID)
│                →  stores in Redis: KEY="session:UUID"  VALUE="9876543210"  TTL=15min
│                →  returns { verified: true, sessionToken: "uuid", isNewUser: true }
│
└─ Step 3: Complete Registration
Frontend  →  POST /api/auth/register
{ name: "Ravi Kumar", sessionToken: "uuid", gender: "Male", ... }
Auth Service →  reads Redis KEY="session:uuid" → "9876543210"
→  creates User row in PostgreSQL
{ mobile: "9876543210", name: "Ravi Kumar", role: "STUDENT" }
→  deletes session from Redis
→  generates JWT token (24h expiry)
payload: { sub: userId, role: "STUDENT", name: "Ravi Kumar" }
→  returns { accessToken: "eyJ...", user: { id, name, role } }

    Frontend stores JWT in localStorage
    Redux authSlice stores user object
    Navigate → /student/dashboard

Flow 2 — Student Login (Returning User)
Student enters mobile on LoginPage.jsx
│
├─ POST /api/auth/send-otp  →  same as registration Step 1
│
└─ POST /api/auth/login
{ contact: "9876543210", otp: "123456" }
Auth Service →  reads Redis otp:9876543210 → "123456"
→  match ✅ → deletes from Redis
→  finds User in PostgreSQL by mobile
→  generates JWT
→  returns { accessToken, user }

    Frontend stores JWT → navigates to /student/dashboard

Flow 3 — Every Authenticated Request
Frontend has JWT in localStorage
│
Every API call adds:  Authorization: Bearer eyJ...
│
API Gateway receives request
│
AuthFilter.java runs:
├─ Is path in PUBLIC_PATHS? (/api/auth/**, /api/plans)
│       YES → forward immediately, no JWT check
│       NO  → continue
│
├─ Extract "Authorization: Bearer eyJ..." header
├─ Parse JWT → verify HMAC-SHA256 signature
├─ Extract claims: sub=userId, role=STUDENT
│
├─ Inject headers into request:
│       X-User-Id:   "550e8400-e29b-41d4-a716-446655440000"
│       X-User-Role: "STUDENT"
│
└─ Forward to downstream service

Downstream service reads:
@RequestHeader("X-User-Id")   String userId   // never needs to parse JWT
@RequestHeader("X-User-Role") String role

Flow 4 — Seat Booking (Full End-to-End)
This is the most complex flow, touching 4 services and Kafka.
Student opens BookingPage.jsx
│
├─ Step 1: Load Plans
│   GET /api/plans  (public, no JWT)
│   API Gateway → Membership Service
│   Membership Service → SELECT * FROM membership_plans WHERE is_active=true
│   Returns: [{ id, name:"Half Day Plan", price:400 }, { id, name:"Full Day Plan", price:600 }]
│   Frontend renders two plan cards
│
├─ Step 2: Load Seat Availability
│   Student picks "Full Day Plan" → shift selector hidden
│   GET /api/seats/availability?shift=FULL_DAY
│   API Gateway → AuthFilter (validates JWT) → Seat Service
│   Seat Service:
│       ├─ CHECK Redis: KEY="seats:availability:FULL_DAY:2024-12-01"
│       │       HIT  → return cached SeatAvailabilityDto immediately
│       │       MISS → continue
│       ├─ SELECT * FROM seats WHERE is_active=true ORDER BY row_label, seat_index
│       │       Returns all 110 seats (A1-A28, B1-B28, C1-C28, D1-D26)
│       ├─ SELECT seat_id FROM seat_bookings
│       │       WHERE status='ACTIVE' AND booking_date<=today AND end_date>=today
│       │       AND (shift='FULL_DAY' OR shift='FULL_DAY')
│       │       Returns: [uuid-of-B3, uuid-of-A15, ...]  (already booked seats)
│       ├─ Map each seat: isBooked = bookedIds.contains(seat.id)
│       ├─ Group by row: { A:[...28 seats], B:[...28], C:[...28], D:[...26] }
│       ├─ STORE in Redis TTL=5min
│       └─ Returns SeatAvailabilityDto
│   Frontend renders cinema-style 110-seat grid
│       Green = available, Red = booked
│
├─ Step 3: Student Selects Seat B14
│   Redux: dispatch(selectSeat({ seatNumber: "B14", row: "B" }))
│   No API call — pure client-side state
│
├─ Step 4: Create Payment Order
│   Student clicks "Pay ₹600"
│   POST /api/payments/create-order
│   { planId: "uuid-full-day", seatId: "uuid-B14", seatNumber: "B14", shift: "FULL_DAY" }
│   │
│   API Gateway → AuthFilter → Membership Service
│   Membership Service (PaymentService.createOrder):
│       ├─ Load Plan from DB (price=600, durationDays=30)
│       ├─ INSERT INTO memberships:
│       │       { userId, planId, seatNumber:"B14", shift:"FULL_DAY",
│       │         startDate:today, endDate:today+30, status:"PENDING" }
│       ├─ Call Razorpay API → create order (or dev_order_xxx in dev mode)
│       ├─ INSERT INTO payments:
│       │       { membershipId, userId, amount:600, gatewayOrderId:"order_xxx", status:"PENDING" }
│       └─ Returns { orderId:"order_xxx", membershipId, amount:600, razorpayKeyId }
│
├─ Step 5: Razorpay Payment
│   Frontend opens Razorpay checkout modal (using razorpayKeyId)
│   Student enters card / UPI → Razorpay processes payment
│   Razorpay callback returns:
│       { razorpay_order_id, razorpay_payment_id, razorpay_signature }
│   (In dev mode: order prefixed dev_order_ → step skipped, auto-approved)
│
├─ Step 6: Verify Payment
│   POST /api/payments/verify
│   { gatewayOrderId, gatewayPaymentId, signature, membershipId }
│   │
│   Membership Service (PaymentService.verifyAndActivateMembership):
│       ├─ VERIFY HMAC-SHA256: SHA256(orderId+"|"+paymentId, keySecret) == signature
│       │       (skipped for dev_order_ prefix)
│       ├─ UPDATE payments SET status='SUCCESS', gatewayPaymentId='pay_xxx'
│       ├─ UPDATE memberships SET status='ACTIVE'
│       │
│       └─ PUBLISH to Kafka topic "booking-confirmed":
│               BookingConfirmedEvent {
│                   userId, membershipId,
│                   planName:"Full Day Plan", planType:"FULL_DAY",
│                   seatNumber:"B14", shift:"FULL_DAY",
│                   startDate, endDate, amountPaid:600,
│                   eventType:"BOOKING_CONFIRMED"
│               }
│       Returns MembershipDto { status:"ACTIVE", seatNumber:"B14", ... }
│
├─ Step 7: Book the Seat
│   POST /api/seats/book
│   { seatNumber:"B14", membershipId:"uuid", shift:"FULL_DAY", startDate, endDate }
│   │
│   Seat Service (SeatService.bookSeat):
│       ├─ SELECT * FROM seats WHERE seat_number='B14' → found
│       ├─ CHECK conflict:
│       │       SELECT COUNT(*) FROM seat_bookings
│       │       WHERE seat_id=uuid-B14 AND shift='FULL_DAY'
│       │         AND status='ACTIVE' AND booking_date<=endDate AND end_date>=startDate
│       │       → 0 (no conflict) ✅
│       ├─ INSERT INTO seat_bookings:
│       │       { seatId, userId, membershipId, shift:"FULL_DAY",
│       │         bookingDate:today, endDate:today+30, status:"ACTIVE" }
│       ├─ BUST Redis cache for all 30 days in range:
│       │       DELETE "seats:availability:FULL_DAY:2024-12-01"
│       │       DELETE "seats:availability:FULL_DAY:2024-12-02"
│       │       ... (30 deletes)
│       └─ Returns SeatBookingDto
│
└─ Step 8: Frontend navigates to /student/payment-success
Shows booking confirmation with seat, shift, dates

Flow 5 — Kafka Notification (Async, After Booking)
Membership Service published to Kafka "booking-confirmed" topic
│
│  (completely async — student already sees success page)
│
Notification Service Kafka Consumer:
NotificationConsumer.handleBookingConfirmed() wakes up
│
├─ Calls NotificationService.sendBookingConfirmation(event)
│
├─ Build WhatsApp message:
│       "✅ Booking Confirmed!
│        Plan: Full Day Plan
│        Seat: B14
│        Shift: Full Day (6AM-10PM)
│        From: 2024-12-01  To: 2024-12-31
│        Paid: ₹600"
│
├─ WhatsAppService.send("9876543210", message, userId, "BOOKING_CONFIRMED")
│       → Twilio API → WhatsApp delivered to student
│       → INSERT INTO notification_logs { channel:WHATSAPP, status:SENT }
│
├─ EmailService.sendText(email, subject, body, userId, "BOOKING_CONFIRMED")
│       → SendGrid API → Email delivered to student
│       → INSERT INTO notification_logs { channel:EMAIL, status:SENT }
│
├─ Admin WhatsApp alert:
│       "📚 New Booking! Student: Ravi Kumar, Seat: B14, Amount: ₹600"
│       → WhatsAppService.send(adminWhatsapp, adminMsg, null, "ADMIN_BOOKING_ALERT")
│
└─ Admin Email alert:
→ EmailService.sendText(adminEmail, "New Booking — Ravi Kumar | Seat B14", ...)

Flow 6 — Admin Login
Admin opens /admin/login
│
POST /api/auth/send-otp { contact:"9999999999", contactType:"MOBILE" }
Auth Service → same OTP flow → OTP=123456 in dev
│
POST /api/auth/admin/login { contact:"9999999999", otp:"123456" }
Auth Service:
├─ Verify OTP from Redis ✅
├─ Find user in DB where mobile="9999999999"
├─ Check role == "ADMIN" ✅
├─ Generate JWT with role:"ADMIN"
└─ Return { accessToken, user: { role:"ADMIN" } }

Frontend stores JWT → navigates to /admin/dashboard

Flow 7 — Admin Dashboard Request
Admin loads /admin/dashboard
│
GET /api/admin/dashboard
│
API Gateway:
├─ AuthFilter: validates JWT → extracts role="ADMIN"
├─ AdminRoleFilter: checks X-User-Role == "ADMIN" ✅
└─ Forwards to Admin Service :8086
│
Admin Service (AdminService.getDashboardStats()):
├─ SELECT COUNT(*) FROM users WHERE role='STUDENT'               → totalStudents
├─ SELECT COUNT(*) FROM users WHERE role='STUDENT' AND is_active=true  → activeStudents
├─ SELECT COUNT(*) FROM memberships WHERE status='ACTIVE'        → activeMemberships
├─ SELECT COUNT(*) FROM memberships WHERE status='EXPIRED'       → expiredMemberships
├─ SELECT memberships expiring in next 7 days                    → expiringThisWeek
├─ SELECT COALESCE(SUM(amount),0) FROM payments
│       WHERE status='SUCCESS' AND created_at >= today_start     → revenueToday
├─ SELECT COALESCE(SUM(amount),0) FROM payments
│       WHERE status='SUCCESS' AND created_at >= month_start     → revenueThisMonth
└─ Returns DashboardDto

    Frontend renders stat cards, occupancy bar, quick links

Flow 8 — Admin Seat Map
Admin opens /admin/seats
│
GET /api/admin/seats/map?shift=FULL_DAY&date=2024-12-01
│
API Gateway → AdminRoleFilter ✅ → Admin Service
│
Admin Service (AdminService.getSeatMap()):
├─ Load all ACTIVE memberships where:
│       startDate <= 2024-12-01 AND endDate >= 2024-12-01
│       AND (shift='FULL_DAY' OR requested shift matches)
├─ Build seatNumber → Membership map
├─ Bulk load all student Users for those memberships
├─ Build grid: rows A(28) B(28) C(28) D(26)
│       Each seat: { seatNumber, isOccupied, studentName, studentMobile, shift, membershipEnd }
└─ Returns SeatMapDto { seatsByRow: { A:[...], B:[...], C:[...], D:[...] } }

    Frontend renders admin seat map
    Admin clicks red seat → popup shows student name, mobile, expiry

Flow 9 — Daily Renewal Reminder (Scheduled)
Every day at 9:00 AM (cron: "0 0 9 * * *")
│
Admin Service ExpiryReminderScheduler.sendExpiryReminders():
│
├─ SELECT * FROM memberships
│       WHERE status='ACTIVE'
│         AND end_date >= today
│         AND end_date <= today+7
│         AND reminder_sent = false
│
├─ For each membership:
│       daysLeft = endDate - today
│
│       IF daysLeft == 7 OR daysLeft == 3:
│           Load student User from DB
│           PUBLISH to Kafka "renewal-reminder":
│               RenewalReminderEvent {
│                   userId, userName:"Ravi Kumar",
│                   userMobile:"9876543210",
│                   seatNumber:"B14",
│                   expiryDate:"2024-12-31",
│                   daysRemaining: 7,
│                   eventType:"RENEWAL_REMINDER"
│               }
│           UPDATE memberships SET reminder_sent=true
│       ELSE:
│           skip (check again tomorrow)
│
└─ Log: "3 reminders published to Kafka"

Notification Service Kafka Consumer wakes up:
NotificationConsumer.handleRenewalReminder()
│
├─ Build urgency message:
│       daysRemaining <= 3 → "⚠️ URGENT"
│       daysRemaining <= 7 → "⏰ Reminder"
│
├─ WhatsApp to student: "⏰ Reminder — Membership Expiring in 7 days..."
└─ Email to student:    "⏰ Reminder: Membership expiring in 7 days"

Flow 10 — Admin Manual Reminder
Admin selects 3 students on /admin/reminders page
Clicks "Send to 3 Reminders"
│
POST /api/admin/reminders/send
{ userIds: ["uuid1", "uuid2", "uuid3"] }
│
API Gateway → AdminRoleFilter ✅ → Admin Service
│
AdminService.sendBulkReminders(["uuid1","uuid2","uuid3"]):
├─ For each userId: findByUserIdAndStatus(ACTIVE)
├─ Bulk load User records
├─ PUBLISH RenewalReminderEvent to Kafka "renewal-reminder" for each
└─ Returns "Reminders queued for 3 students"

Notification Service processes 3 Kafka messages
→ WhatsApp + Email sent to each student

Flow 11 — Profile Update with Photo Upload
Student opens /student/profile
│
GET /api/users/me
API Gateway → AuthFilter → User Service
User Service → SELECT * FROM users WHERE id=userId
Returns UserDto { name, mobile, email, address, photoUrl, ... }
│
Student edits address, clicks Save
PATCH /api/users/me
{ address: "123 MG Road, Bangalore" }
User Service → UPDATE users SET address=... WHERE id=userId
Returns updated UserDto
│
Student clicks "Change Photo" → selects file
POST /api/users/me/photo  (multipart/form-data)
User Service:
├─ Validate file type (JPEG/PNG/WebP only)
├─ Validate file size (max 5MB)
├─ Delete old photo file from /app/uploads/photos/ if exists
├─ Save new file: /app/uploads/photos/user_{userId}_{random}.jpg
├─ UPDATE users SET photo_url='/uploads/photos/user_...' WHERE id=userId
└─ Returns { photoUrl: "/uploads/photos/user_xxx.jpg" }

Photo served by Nginx:
GET /uploads/photos/user_xxx.jpg
Nginx proxies → User Service :8082/uploads/...

Redis Key Structure
otp:{contact}              → "123456"           TTL: 5 min
session:{uuid}             → "9876543210"       TTL: 15 min
seats:availability:{shift}:{date} → SeatAvailabilityDto JSON   TTL: 5 min

Examples:
otp:9876543210                     → "123456"
otp:test@gmail.com                 → "847291"
session:550e8400-e29b-41d4-a716... → "9876543210"
seats:availability:MORNING:2024-12-01 → { totalSeats:110, bookedSeats:45, ... }
seats:availability:FULL_DAY:2024-12-01 → { ... }

Kafka Topics
Topic: booking-confirmed
Producer: membership-service  (after payment verified)
Consumer: notification-service
Payload:  BookingConfirmedEvent
Triggers: WhatsApp + Email to student, alert to admin

Topic: user-registered
Producer: auth-service  (after new user registers)
Consumer: notification-service
Payload:  BookingConfirmedEvent (reused shape)
Triggers: Welcome WhatsApp + Email to student

Topic: renewal-reminder
Producer: admin-service  (scheduler at 9AM + manual send)
Consumer: notification-service
Payload:  RenewalReminderEvent
Triggers: Expiry warning WhatsApp + Email to student

Database Tables Per Service
auth-service        → reads/writes:  users
user-service        → reads/writes:  users
membership-service  → reads/writes:  memberships, payments
→ reads only:    membership_plans
seat-service        → reads/writes:  seat_bookings
→ reads only:    seats
notification-service→ reads/writes:  notification_logs
admin-service       → reads/writes:  memberships (reminder_sent flag, is_active)
→ reads only:    users, payments, membership_plans, seats