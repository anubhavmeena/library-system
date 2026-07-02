package com.library.admin.controller;

import com.library.admin.dto.ChangeSeatRequest;
import com.library.admin.dto.CreateCashMembershipRequest;
import com.library.admin.dto.MembershipDto;
import com.library.admin.dto.UpdateMembershipPlanRequest;
import com.library.admin.service.AdminMembershipService;
import com.library.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/memberships")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminMembershipController {

    private final AdminMembershipService adminMembershipService;

    @PostMapping("/cash")
    public ResponseEntity<ApiResponse<MembershipDto>> createCashMembership(
            @Valid @RequestBody CreateCashMembershipRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(adminMembershipService.createCashMembership(req)));
    }

    @PatchMapping("/{membershipId}/seat")
    public ResponseEntity<ApiResponse<MembershipDto>> changeSeat(
            @PathVariable String membershipId,
            @Valid @RequestBody ChangeSeatRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success(adminMembershipService.changeSeat(membershipId, req)));
    }

    @PatchMapping("/{membershipId}/plan")
    public ResponseEntity<ApiResponse<String>> updateMembershipPlan(
            @PathVariable String membershipId,
            @Valid @RequestBody UpdateMembershipPlanRequest req) {
        adminMembershipService.updateMembershipPlan(membershipId, req);
        return ResponseEntity.ok(ApiResponse.success("Plan updated"));
    }

    // Explicitly frees a seat held by a GRACE (overdue, unpaid) membership.
    // Only seats released this way become bookable again — a lapsed membership
    // never frees its seat on its own.
    @PatchMapping("/{membershipId}/release")
    public ResponseEntity<ApiResponse<String>> releaseSeat(@PathVariable String membershipId) {
        adminMembershipService.releaseSeat(membershipId);
        return ResponseEntity.ok(ApiResponse.success("Seat released"));
    }
}
