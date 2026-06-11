package com.library.user.controller;

import com.library.user.dto.CreateFeedbackRequest;
import com.library.user.dto.FeedbackDto;
import com.library.user.service.FeedbackService;
import com.library.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/feedback")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<ApiResponse<FeedbackDto>> submit(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateFeedbackRequest req) {
        return ResponseEntity.ok(ApiResponse.success(feedbackService.submit(userId, req)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<FeedbackDto>>> getMyFeedback(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(feedbackService.getMyFeedback(userId)));
    }
}
