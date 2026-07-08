package com.library.admin.service;

import com.library.admin.dto.FeedbackDto;
import com.library.admin.dto.UpdateFeedbackRequest;
import com.library.admin.entity.Feedback;
import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.repository.FeedbackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackAdminServiceTest {

    @Mock FeedbackRepository feedbackRepository;

    @InjectMocks FeedbackAdminService feedbackAdminService;

    private Feedback buildFeedback(UUID id, Feedback.Status status) {
        return Feedback.builder()
                .id(id).userId(UUID.randomUUID())
                .type(Feedback.Type.FEEDBACK).subject("Subject").description("Description")
                .status(status).build();
    }

    // ── getAllFeedback — filter routing ──────────────────────────────────────

    @Test
    void getAllFeedback_noFilters_callsFindAllWithUser() {
        when(feedbackRepository.findAllWithUser()).thenReturn(List.of(buildFeedback(UUID.randomUUID(), Feedback.Status.OPEN)));

        List<FeedbackDto> result = feedbackAdminService.getAllFeedback(null, null);

        assertThat(result).hasSize(1);
        verify(feedbackRepository).findAllWithUser();
        verifyNoMoreInteractions(feedbackRepository);
    }

    @Test
    void getAllFeedback_typeOnly_callsFindByTypeWithUser() {
        when(feedbackRepository.findByTypeWithUser(Feedback.Type.COMPLAINT)).thenReturn(List.of());

        feedbackAdminService.getAllFeedback("complaint", null);

        verify(feedbackRepository).findByTypeWithUser(Feedback.Type.COMPLAINT);
    }

    @Test
    void getAllFeedback_statusOnly_callsFindByStatusWithUser() {
        when(feedbackRepository.findByStatusWithUser(Feedback.Status.OPEN)).thenReturn(List.of());

        feedbackAdminService.getAllFeedback(null, "open");

        verify(feedbackRepository).findByStatusWithUser(Feedback.Status.OPEN);
    }

    @Test
    void getAllFeedback_typeAndStatus_callsFindByTypeAndStatusWithUser() {
        when(feedbackRepository.findByTypeAndStatusWithUser(Feedback.Type.FEEDBACK, Feedback.Status.RESOLVED))
                .thenReturn(List.of());

        feedbackAdminService.getAllFeedback("FEEDBACK", "RESOLVED");

        verify(feedbackRepository).findByTypeAndStatusWithUser(Feedback.Type.FEEDBACK, Feedback.Status.RESOLVED);
    }

    @Test
    void getAllFeedback_invalidTypeValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> feedbackAdminService.getAllFeedback("NOT_A_TYPE", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid filter value");
        verifyNoInteractions(feedbackRepository);
    }

    @Test
    void getAllFeedback_invalidStatusValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> feedbackAdminService.getAllFeedback(null, "NOT_A_STATUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid filter value");
    }

    // ── updateFeedback ────────────────────────────────────────────────────────

    @Test
    void updateFeedback_notFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(feedbackRepository.findById(id)).thenReturn(Optional.empty());

        UpdateFeedbackRequest req = new UpdateFeedbackRequest();
        req.setStatus("RESOLVED");

        assertThatThrownBy(() -> feedbackAdminService.updateFeedback(id.toString(), req))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void updateFeedback_validTransition_openToUnderReview_succeeds() {
        UUID id = UUID.randomUUID();
        Feedback feedback = buildFeedback(id, Feedback.Status.OPEN);
        when(feedbackRepository.findById(id)).thenReturn(Optional.of(feedback));
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateFeedbackRequest req = new UpdateFeedbackRequest();
        req.setStatus("under_review");

        FeedbackDto dto = feedbackAdminService.updateFeedback(id.toString(), req);

        assertThat(dto.getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void updateFeedback_invalidStatusString_throwsWithHelpfulMessage() {
        UUID id = UUID.randomUUID();
        when(feedbackRepository.findById(id)).thenReturn(Optional.of(buildFeedback(id, Feedback.Status.OPEN)));

        UpdateFeedbackRequest req = new UpdateFeedbackRequest();
        req.setStatus("CLOSED");

        assertThatThrownBy(() -> feedbackAdminService.updateFeedback(id.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Must be OPEN, UNDER_REVIEW, or RESOLVED");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void updateFeedback_resolvedCannotRevertToOpen() {
        UUID id = UUID.randomUUID();
        when(feedbackRepository.findById(id)).thenReturn(Optional.of(buildFeedback(id, Feedback.Status.RESOLVED)));

        UpdateFeedbackRequest req = new UpdateFeedbackRequest();
        req.setStatus("OPEN");

        assertThatThrownBy(() -> feedbackAdminService.updateFeedback(id.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid status transition: RESOLVED");
    }

    @Test
    void updateFeedback_underReviewCannotRevertToOpen() {
        UUID id = UUID.randomUUID();
        when(feedbackRepository.findById(id)).thenReturn(Optional.of(buildFeedback(id, Feedback.Status.UNDER_REVIEW)));

        UpdateFeedbackRequest req = new UpdateFeedbackRequest();
        req.setStatus("OPEN");

        assertThatThrownBy(() -> feedbackAdminService.updateFeedback(id.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid status transition: UNDER_REVIEW");
    }

    @Test
    void updateFeedback_resolvedToResolved_idempotentAllowed() {
        UUID id = UUID.randomUUID();
        when(feedbackRepository.findById(id)).thenReturn(Optional.of(buildFeedback(id, Feedback.Status.RESOLVED)));
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateFeedbackRequest req = new UpdateFeedbackRequest();
        req.setStatus("RESOLVED");

        FeedbackDto dto = feedbackAdminService.updateFeedback(id.toString(), req);

        assertThat(dto.getStatus()).isEqualTo("RESOLVED");
    }

    @Test
    void updateFeedback_openCanTransitionToAnyStatus() {
        UUID id = UUID.randomUUID();
        when(feedbackRepository.findById(id)).thenReturn(Optional.of(buildFeedback(id, Feedback.Status.OPEN)));
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateFeedbackRequest req = new UpdateFeedbackRequest();
        req.setStatus("RESOLVED");

        FeedbackDto dto = feedbackAdminService.updateFeedback(id.toString(), req);

        assertThat(dto.getStatus()).isEqualTo("RESOLVED");
    }

    @Test
    void updateFeedback_notesOnlyNoStatusChange_leavesStatusUntouched() {
        UUID id = UUID.randomUUID();
        Feedback feedback = buildFeedback(id, Feedback.Status.OPEN);
        when(feedbackRepository.findById(id)).thenReturn(Optional.of(feedback));
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateFeedbackRequest req = new UpdateFeedbackRequest();
        req.setAdminNotes("  Investigating this  ");

        FeedbackDto dto = feedbackAdminService.updateFeedback(id.toString(), req);

        assertThat(dto.getStatus()).isEqualTo("OPEN");
        assertThat(dto.getAdminNotes()).isEqualTo("Investigating this");
    }

    @Test
    void updateFeedback_blankStatusInRequest_skipsStatusValidationEntirely() {
        UUID id = UUID.randomUUID();
        Feedback feedback = buildFeedback(id, Feedback.Status.RESOLVED);
        when(feedbackRepository.findById(id)).thenReturn(Optional.of(feedback));
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateFeedbackRequest req = new UpdateFeedbackRequest();
        req.setStatus("   ");
        req.setAdminNotes("note only");

        FeedbackDto dto = feedbackAdminService.updateFeedback(id.toString(), req);

        assertThat(dto.getStatus()).isEqualTo("RESOLVED"); // untouched despite starting "invalid-looking"
        assertThat(dto.getAdminNotes()).isEqualTo("note only");
    }
}
