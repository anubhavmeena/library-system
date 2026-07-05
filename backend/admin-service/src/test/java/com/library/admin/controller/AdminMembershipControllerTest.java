package com.library.admin.controller;

import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.service.AdminMembershipService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Scoped to PATCH /api/admin/memberships/{membershipId}/release — the endpoint
// touched by broadening releaseSeat() to accept ACTIVE (not just GRACE)
// memberships. Other AdminMembershipController routes are untouched and not
// covered here.
@WebMvcTest(AdminMembershipController.class)
class AdminMembershipControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  AdminMembershipService adminMembershipService;

    @Test
    void releaseSeat_success_returns200WithConfirmationMessage() throws Exception {
        String membershipId = UUID.randomUUID().toString();
        doNothing().when(adminMembershipService).releaseSeat(membershipId, false);

        mockMvc.perform(patch("/api/admin/memberships/{membershipId}/release", membershipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Seat released"));

        verify(adminMembershipService).releaseSeat(membershipId, false);
    }

    @Test
    void releaseSeat_membershipNotFound_returns404() throws Exception {
        String membershipId = UUID.randomUUID().toString();
        doThrow(new ResourceNotFoundException("Membership not found: " + membershipId))
                .when(adminMembershipService).releaseSeat(membershipId, false);

        mockMvc.perform(patch("/api/admin/memberships/{membershipId}/release", membershipId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Membership not found: " + membershipId));
    }

    @Test
    void releaseSeat_noOccupiedSeat_returns400() throws Exception {
        String membershipId = UUID.randomUUID().toString();
        doThrow(new IllegalArgumentException("Membership has no currently-occupied seat to release"))
                .when(adminMembershipService).releaseSeat(membershipId, false);

        mockMvc.perform(patch("/api/admin/memberships/{membershipId}/release", membershipId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Membership has no currently-occupied seat to release"));
    }
}
