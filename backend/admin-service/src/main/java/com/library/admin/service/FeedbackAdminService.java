package com.library.admin.service;

import com.library.admin.dto.FeedbackDto;
import com.library.admin.dto.UpdateFeedbackRequest;
import com.library.admin.entity.Feedback;
import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackAdminService {

    private final FeedbackRepository feedbackRepository;

    public List<FeedbackDto> getAllFeedback(String type, String status) {
        boolean hasType   = type   != null && !type.isBlank();
        boolean hasStatus = status != null && !status.isBlank();

        List<Feedback> results;
        try {
            if (hasType && hasStatus) {
                results = feedbackRepository.findByTypeAndStatusWithUser(
                        Feedback.Type.valueOf(type.toUpperCase()),
                        Feedback.Status.valueOf(status.toUpperCase()));
            } else if (hasType) {
                results = feedbackRepository.findByTypeWithUser(
                        Feedback.Type.valueOf(type.toUpperCase()));
            } else if (hasStatus) {
                results = feedbackRepository.findByStatusWithUser(
                        Feedback.Status.valueOf(status.toUpperCase()));
            } else {
                results = feedbackRepository.findAllWithUser();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid filter value: " + e.getMessage());
        }

        return results.stream().map(FeedbackDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public FeedbackDto updateFeedback(String feedbackId, UpdateFeedbackRequest req) {
        Feedback feedback = feedbackRepository.findById(UUID.fromString(feedbackId))
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found: " + feedbackId));

        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            Feedback.Status newStatus;
            try {
                newStatus = Feedback.Status.valueOf(req.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status. Must be OPEN, UNDER_REVIEW, or RESOLVED");
            }
            validateStatusTransition(feedback.getStatus(), newStatus);
            feedback.setStatus(newStatus);
        }

        if (req.getAdminNotes() != null) {
            feedback.setAdminNotes(req.getAdminNotes().trim());
        }

        Feedback saved = feedbackRepository.save(feedback);
        log.info("Feedback {} updated: status={}", feedbackId, saved.getStatus());
        return FeedbackDto.fromEntity(saved);
    }

    private void validateStatusTransition(Feedback.Status current, Feedback.Status next) {
        boolean valid = switch (current) {
            case OPEN         -> true;
            case UNDER_REVIEW -> next == Feedback.Status.UNDER_REVIEW || next == Feedback.Status.RESOLVED;
            case RESOLVED     -> next == Feedback.Status.RESOLVED;
        };
        if (!valid) throw new IllegalArgumentException(
                "Invalid status transition: " + current + " → " + next);
    }
}
