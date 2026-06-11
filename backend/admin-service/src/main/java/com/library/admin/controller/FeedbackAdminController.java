package com.library.admin.controller;

import com.library.admin.dto.FeedbackDto;
import com.library.admin.dto.UpdateFeedbackRequest;
import com.library.admin.service.FeedbackAdminService;
import com.library.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FeedbackAdminController {

    private final FeedbackAdminService feedbackAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FeedbackDto>>> getAllFeedback(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(feedbackAdminService.getAllFeedback(type, status)));
    }

    @PatchMapping("/{feedbackId}")
    public ResponseEntity<ApiResponse<FeedbackDto>> updateFeedback(
            @PathVariable String feedbackId,
            @RequestBody UpdateFeedbackRequest req) {
        return ResponseEntity.ok(ApiResponse.success(feedbackAdminService.updateFeedback(feedbackId, req)));
    }
}
