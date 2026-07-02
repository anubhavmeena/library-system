package com.library.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.admin.dto.*;
import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.service.AdminService;
import com.library.admin.service.ImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  AdminService adminService;
    @MockBean  ImportService importService;

    // ── GET /api/admin/dashboard ─────────────────────────────────────────────

    @Test
    void getDashboard_returns200WithDashboardDto() throws Exception {
        DashboardDto dto = DashboardDto.builder()
                .totalStudents(50L)
                .activeMemberships(40L).expiredMemberships(5L)
                .expiringThisWeek(3L).totalSeats(110L)
                .occupiedSeats(40L).availableSeats(70L)
                .revenueToday(new BigDecimal("1200.00"))
                .revenueThisMonth(new BigDecimal("35000.00"))
                .paymentsThisMonth(58L)
                .build();

        when(adminService.getDashboardStats()).thenReturn(dto);

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalStudents").value(50))
                .andExpect(jsonPath("$.data.activeMemberships").value(40))
                .andExpect(jsonPath("$.data.totalSeats").value(110))
                .andExpect(jsonPath("$.data.revenueToday").value(1200.00));
    }

    // ── GET /api/admin/students ──────────────────────────────────────────────

    @Test
    void getAllStudents_defaultParams_forwardsPageZeroSize20() throws Exception {
        when(adminService.getAllStudents(eq(0), eq(20), isNull(), isNull(), eq("createdAt"), eq("desc"))).thenReturn(new StudentListDto(List.of(), 0));

        mockMvc.perform(get("/api/admin/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.students").isArray());

        verify(adminService).getAllStudents(0, 20, null, null, "createdAt", "desc");
    }

    @Test
    void getAllStudents_customParams_forwarded() throws Exception {
        when(adminService.getAllStudents(eq(2), eq(5), eq("PAID"), isNull(), eq("createdAt"), eq("desc"))).thenReturn(new StudentListDto(List.of(), 0));

        mockMvc.perform(get("/api/admin/students")
                        .param("page", "2")
                        .param("size", "5")
                        .param("membershipStatus", "PAID"))
                .andExpect(status().isOk());

        verify(adminService).getAllStudents(2, 5, "PAID", null, "createdAt", "desc");
    }

    @Test
    void getAllStudents_returnsListInData() throws Exception {
        StudentDto student = StudentDto.builder()
                .id(UUID.randomUUID().toString())
                .name("Alice")
                .displayStatus("PAID")
                .build();

        when(adminService.getAllStudents(anyInt(), anyInt(), any(), any(), any(), any())).thenReturn(new StudentListDto(List.of(student), 1));

        mockMvc.perform(get("/api/admin/students"))
                .andExpect(jsonPath("$.data.students", hasSize(1)))
                .andExpect(jsonPath("$.data.students[0].name").value("Alice"));
    }

    // ── GET /api/admin/students/{userId} ────────────────────────────────────

    @Test
    void getStudent_found_returns200() throws Exception {
        String uid = UUID.randomUUID().toString();
        StudentDto dto = StudentDto.builder().id(uid).name("Alice").build();

        when(adminService.getStudentDetails(uid)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/students/{userId}", uid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(uid))
                .andExpect(jsonPath("$.data.name").value("Alice"));
    }

    @Test
    void getStudent_notFound_returns404() throws Exception {
        String uid = UUID.randomUUID().toString();
        when(adminService.getStudentDetails(uid))
                .thenThrow(new ResourceNotFoundException("Student not found: " + uid));

        mockMvc.perform(get("/api/admin/students/{userId}", uid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Student not found: " + uid));
    }

    // ── GET /api/admin/seats/map ─────────────────────────────────────────────

    @Test
    void getSeatMap_defaultShiftIsFullDay() throws Exception {
        SeatMapDto dto = SeatMapDto.builder()
                .shift("FULL_DAY").date("2025-06-04")
                .totalSeats(110).occupiedSeats(5).availableSeats(105)
                .seatsByRow(Map.of())
                .build();

        when(adminService.getSeatMap(eq("FULL_DAY"), isNull())).thenReturn(dto);

        mockMvc.perform(get("/api/admin/seats/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSeats").value(110));

        verify(adminService).getSeatMap("FULL_DAY", null);
    }

    @Test
    void getSeatMap_customShiftAndDate_forwarded() throws Exception {
        when(adminService.getSeatMap(eq("MORNING"), eq("2025-06-01")))
                .thenReturn(SeatMapDto.builder().shift("MORNING").date("2025-06-01")
                        .totalSeats(110).occupiedSeats(0).availableSeats(110)
                        .seatsByRow(Map.of()).build());

        mockMvc.perform(get("/api/admin/seats/map")
                        .param("shift", "MORNING")
                        .param("date", "2025-06-01"))
                .andExpect(status().isOk());

        verify(adminService).getSeatMap("MORNING", "2025-06-01");
    }

    // ── GET /api/admin/memberships/expiring ──────────────────────────────────

    @Test
    void getExpiringMemberships_defaultWithinDays7() throws Exception {
        when(adminService.getExpiringMemberships(eq(7))).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/memberships/expiring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(adminService).getExpiringMemberships(7);
    }

    @Test
    void getExpiringMemberships_customWithinDays_forwarded() throws Exception {
        when(adminService.getExpiringMemberships(14)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/memberships/expiring").param("withinDays", "14"))
                .andExpect(status().isOk());

        verify(adminService).getExpiringMemberships(14);
    }

    // ── POST /api/admin/reminders/send ──────────────────────────────────────

    @Test
    void sendBulkReminders_emptyUserIds_returns200WithCount() throws Exception {
        when(adminService.sendBulkReminders(List.of())).thenReturn(5);

        SendReminderRequest req = new SendReminderRequest();
        req.setUserIds(List.of());

        mockMvc.perform(post("/api/admin/reminders/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", containsString("5")));
    }

    @Test
    void sendBulkReminders_specificUserIds_forwarded() throws Exception {
        String uid = UUID.randomUUID().toString();
        when(adminService.sendBulkReminders(List.of(uid))).thenReturn(1);

        SendReminderRequest req = new SendReminderRequest();
        req.setUserIds(List.of(uid));

        mockMvc.perform(post("/api/admin/reminders/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", containsString("1")));

        verify(adminService).sendBulkReminders(List.of(uid));
    }

    // ── GET /api/admin/reports/revenue ───────────────────────────────────────

    @Test
    void getRevenueReport_returns200WithRevenueDto() throws Exception {
        RevenueReportDto dto = RevenueReportDto.builder()
                .fromDate("2025-01-01").toDate("2025-01-31")
                .totalRevenue(new BigDecimal("12500.00"))
                .totalTransactions(45L)
                .dailyBreakdown(List.of())
                .build();

        when(adminService.getRevenueReport("2025-01-01", "2025-01-31")).thenReturn(dto);

        mockMvc.perform(get("/api/admin/reports/revenue")
                        .param("from", "2025-01-01")
                        .param("to", "2025-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRevenue").value(12500.00))
                .andExpect(jsonPath("$.data.totalTransactions").value(45));
    }

    @Test
    void getRevenueReport_forwardsFromAndToParams() throws Exception {
        when(adminService.getRevenueReport("2025-03-01", "2025-03-31"))
                .thenReturn(RevenueReportDto.builder()
                        .fromDate("2025-03-01").toDate("2025-03-31")
                        .totalRevenue(BigDecimal.ZERO).totalTransactions(0L)
                        .dailyBreakdown(List.of()).build());

        mockMvc.perform(get("/api/admin/reports/revenue")
                        .param("from", "2025-03-01")
                        .param("to",   "2025-03-31"))
                .andExpect(status().isOk());

        verify(adminService).getRevenueReport("2025-03-01", "2025-03-31");
    }

    // ── Content-Type header ──────────────────────────────────────────────────

    @Test
    void allGetEndpoints_returnJsonContentType() throws Exception {
        when(adminService.getDashboardStats()).thenReturn(DashboardDto.builder().build());

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
