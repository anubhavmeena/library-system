package com.library.user.service;

import com.library.user.dto.CreateFeedbackRequest;
import com.library.user.dto.FeedbackDto;
import com.library.user.entity.Feedback;
import com.library.user.repository.FeedbackRepository;
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
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    @Transactional
    public FeedbackDto submit(String userId, CreateFeedbackRequest req) {
        Feedback.Type type;
        try {
            type = Feedback.Type.valueOf(req.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid type. Must be FEEDBACK or COMPLAINT");
        }

        Feedback feedback = Feedback.builder()
                .userId(UUID.fromString(userId))
                .type(type)
                .subject(req.getSubject().trim())
                .description(req.getDescription().trim())
                .build();

        Feedback saved = feedbackRepository.save(feedback);
        log.info("Feedback submitted by user {}: type={}, subject='{}'", userId, type, req.getSubject());
        return FeedbackDto.fromEntity(saved);
    }

    public List<FeedbackDto> getMyFeedback(String userId) {
        return feedbackRepository
                .findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId))
                .stream()
                .map(FeedbackDto::fromEntity)
                .collect(Collectors.toList());
    }
}
