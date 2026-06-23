package com.library.admin.controller;

import com.library.admin.dto.*;
import com.library.admin.service.AdminService;
import com.library.admin.service.ImportService;
import com.library.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminService  adminService;
    private final ImportService importService;

    // ── Dashboard ─────────────────────────────────────────────────────────────
    // Returns: totalStudents, activeStudents, activeMemberships, expiringThisWeek,
    //          totalSeats, occupiedSeats, availableSeats, revenueToday, revenueThisMonth

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardDto>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDashboardStats()));
    }

    // ── Students ──────────────────────────────────────────────────────────────

    @GetMapping("/students")
    public ResponseEntity<ApiResponse<StudentListDto>> getAllStudents(
            @RequestParam(defaultValue = "0")           int page,
            @RequestParam(defaultValue = "20")          int size,
            @RequestParam(required = false)             String status,
            @RequestParam(required = false)             String membershipStatus,
            @RequestParam(required = false)             String search,
            @RequestParam(defaultValue = "createdAt")   String sortBy,
            @RequestParam(defaultValue = "desc")        String sortDir) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getAllStudents(page, size, status, membershipStatus, search, sortBy, sortDir)));
    }

    @GetMapping("/students/{userId}")
    public ResponseEntity<ApiResponse<StudentDto>> getStudent(
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getStudentDetails(userId)));
    }

    @PatchMapping("/students/{userId}/status")
    public ResponseEntity<ApiResponse<String>> updateStudentStatus(
            @PathVariable String userId,
            @RequestBody UpdateStatusRequest request) {
        adminService.updateStudentStatus(userId, request.isActive());
        return ResponseEntity.ok(ApiResponse.success("Status updated successfully"));
    }

    @PatchMapping("/students/{userId}")
    public ResponseEntity<ApiResponse<StudentDto>> updateStudent(
            @PathVariable String userId,
            @RequestBody UpdateStudentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminService.updateStudent(userId, request)));
    }

    @GetMapping("/students/{userId}/payments")
    public ResponseEntity<ApiResponse<List<PaymentHistoryDto>>> getStudentPayments(
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getStudentPayments(userId)));
    }

    @DeleteMapping("/students/{userId}")
    public ResponseEntity<ApiResponse<String>> deleteStudent(@PathVariable String userId) {
        adminService.deleteStudent(userId);
        return ResponseEntity.ok(ApiResponse.success("Student deleted successfully"));
    }

    @PostMapping("/students/import/single")
    public ResponseEntity<ApiResponse<String>> importSingleStudent(
            @Valid @RequestBody ManualStudentImportRequest req) {
        importService.importSingleStudent(req);
        return ResponseEntity.ok(ApiResponse.success("Student added successfully"));
    }

    // ── Seat Map ──────────────────────────────────────────────────────────────
    // Returns all 110 seats grouped by row (A/B/C/D), each with isOccupied,
    // studentName, studentMobile, shift, membershipEnd

    @GetMapping("/seats/map")
    public ResponseEntity<ApiResponse<SeatMapDto>> getSeatMap(
            @RequestParam(defaultValue = "FULL_DAY") String shift,
            @RequestParam(required = false)          String date) {   // yyyy-MM-dd, defaults to today
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getSeatMap(shift, date)));
    }

    // ── Expiring Memberships ──────────────────────────────────────────────────
    // Used by AdminRemindersPage to show who is expiring within N days

    @GetMapping("/memberships/expiring")
    public ResponseEntity<ApiResponse<List<StudentDto>>> getExpiringMemberships(
            @RequestParam(defaultValue = "7") int withinDays) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getExpiringMemberships(withinDays)));
    }

    // ── Reminders ─────────────────────────────────────────────────────────────
    // userIds = [] → sends to ALL expiring within 7 days
    // userIds = ["uuid1","uuid2"] → sends only to those students

    @PostMapping("/reminders/send")
    public ResponseEntity<ApiResponse<String>> sendBulkReminders(
            @RequestBody SendReminderRequest request) {
        int count = adminService.sendBulkReminders(request.getUserIds());
        return ResponseEntity.ok(ApiResponse.success(
                "Reminders queued for " + count + " students"));
    }

    // ── Broadcast Notification ────────────────────────────────────────────────
    // Sends a custom WhatsApp message to all students with active memberships

    @PostMapping("/broadcast")
    public ResponseEntity<ApiResponse<String>> broadcastNotification(
            @Valid @RequestBody BroadcastRequest request) {
        int count = adminService.broadcastNotification(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Message queued for " + count + " active members"));
    }

    @GetMapping("/broadcast/history")
    public ResponseEntity<ApiResponse<List<BroadcastHistoryDto>>> getBroadcastHistory() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getBroadcastHistory()));
    }

    // ── Revenue Report ────────────────────────────────────────────────────────
    // Returns total revenue + daily breakdown between two dates
    // Example: GET /api/admin/reports/revenue?from=2024-12-01&to=2024-12-31

    @GetMapping("/reports/revenue")
    public ResponseEntity<ApiResponse<RevenueReportDto>> getRevenueReport(
            @RequestParam String from,    // yyyy-MM-dd
            @RequestParam String to) {    // yyyy-MM-dd
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getRevenueReport(from, to)));
    }

    @GetMapping("/reports/payments/breakdown")
    public ResponseEntity<ApiResponse<List<PaymentAmountBreakdownDto>>> getPaymentBreakdown(
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getPaymentAmountBreakdown(from, to)));
    }

    @GetMapping("/reports/payments/daily")
    public ResponseEntity<ApiResponse<List<DailyPaymentDto>>> getDailyPayments(
            @RequestParam String date) {  // yyyy-MM-dd
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getPaymentsByDate(date)));
    }
}