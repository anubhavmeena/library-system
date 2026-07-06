package com.library.admin.service;

import com.library.admin.dto.CreateCashMembershipRequest;
import com.library.admin.dto.ImportResultDto;
import com.library.admin.dto.ManualImportWithPhotoResponse;
import com.library.admin.dto.ManualStudentImportRequest;
import com.library.admin.entity.Plan;
import com.library.admin.entity.User;
import com.library.admin.repository.PlanRepository;
import com.library.admin.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock UserRepository         userRepository;
    @Mock PlanRepository         planRepository;
    @Mock AdminMembershipService membershipService;

    @InjectMocks ImportService importService;

    @TempDir Path tempUploadDir;

    private Plan halfDayPlan() {
        return Plan.builder().id(UUID.randomUUID()).name("Half Day")
                .planType(Plan.PlanType.HALF_DAY).price(new BigDecimal("400")).durationDays(30).isActive(true).build();
    }

    private Plan fullDayPlan() {
        return Plan.builder().id(UUID.randomUUID()).name("Full Day")
                .planType(Plan.PlanType.FULL_DAY).price(new BigDecimal("600")).durationDays(30).isActive(true).build();
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "students.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @org.junit.jupiter.api.BeforeEach
    void setUploadConfig() {
        ReflectionTestUtils.setField(importService, "uploadDir", tempUploadDir.toString());
        ReflectionTestUtils.setField(importService, "allowedTypes", "image/jpeg,image/png,image/webp");
    }

    // ── importSingleStudent ──────────────────────────────────────────────────

    @Test
    void importSingleStudent_newPhone_createsStudent() {
        when(userRepository.findByMobile("9876543210")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ManualStudentImportRequest req = new ManualStudentImportRequest();
        req.setName("  John Doe  ");
        req.setPhone("987-654-3210");

        importService.importSingleStudent(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getMobile()).isEqualTo("9876543210");
        assertThat(captor.getValue().getName()).isEqualTo("John Doe");
        assertThat(captor.getValue().getRole()).isEqualTo(User.Role.STUDENT);
        assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    void importSingleStudent_existingPhone_doesNotCreateDuplicate() {
        User existing = User.builder().id(UUID.randomUUID()).mobile("9876543210").name("Existing").build();
        when(userRepository.findByMobile("9876543210")).thenReturn(Optional.of(existing));

        ManualStudentImportRequest req = new ManualStudentImportRequest();
        req.setName("John Doe");
        req.setPhone("9876543210");

        importService.importSingleStudent(req);

        verify(userRepository, never()).save(any());
    }

    @Test
    void importSingleStudent_phoneWithNoDigits_throws() {
        ManualStudentImportRequest req = new ManualStudentImportRequest();
        req.setName("John Doe");
        req.setPhone("abc-def");

        assertThatThrownBy(() -> importService.importSingleStudent(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phone number is invalid");
        verifyNoInteractions(userRepository);
    }

    // ── importSingleStudentWithPhoto ─────────────────────────────────────────

    @Test
    void importSingleStudentWithPhoto_noPhoto_createsStudentWithoutPhotoUrl() throws Exception {
        when(userRepository.findByMobile("9876543210")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ManualImportWithPhotoResponse res =
                importService.importSingleStudentWithPhoto("John Doe", "9876543210", null);

        assertThat(res.getPhotoUrl()).isNull();
        assertThat(res.getMessage()).isEqualTo("Student added successfully");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPhotoUrl()).isNull();
    }

    @Test
    void importSingleStudentWithPhoto_validPhoto_setsPhotoUrlAndSavesUser() throws Exception {
        when(userRepository.findByMobile("9876543210")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile photo = new MockMultipartFile(
                "photo", "student.jpg", "image/jpeg", "fake-image-bytes".getBytes(StandardCharsets.UTF_8));

        ManualImportWithPhotoResponse res =
                importService.importSingleStudentWithPhoto("John Doe", "9876543210", photo);

        assertThat(res.getPhotoUrl()).startsWith("/uploads/photos/user_");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(captor.capture()); // once in findOrCreateUser, once after setting photoUrl
        assertThat(captor.getValue().getPhotoUrl()).isEqualTo(res.getPhotoUrl());
    }

    @Test
    void importSingleStudentWithPhoto_invalidContentType_throws() {
        MockMultipartFile photo = new MockMultipartFile(
                "photo", "notes.txt", "text/plain", "not an image".getBytes(StandardCharsets.UTF_8));
        when(userRepository.findByMobile("9111111111")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() ->
                importService.importSingleStudentWithPhoto("Jane Doe", "9111111111", photo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    void importSingleStudentWithPhoto_oversizedFile_throws() {
        byte[] tooBig = new byte[5_242_881];
        MockMultipartFile photo = new MockMultipartFile("photo", "big.jpg", "image/jpeg", tooBig);
        when(userRepository.findByMobile("9222222222")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() ->
                importService.importSingleStudentWithPhoto("Big File", "9222222222", photo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 5MB");
    }

    @Test
    void importSingleStudentWithPhoto_existingPhone_reusesUserAndStillSetsPhoto() throws Exception {
        User existing = User.builder().id(UUID.randomUUID()).mobile("9876543210").name("Existing").build();
        when(userRepository.findByMobile("9876543210")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile photo = new MockMultipartFile(
                "photo", "student.jpg", "image/jpeg", "fake-image-bytes".getBytes(StandardCharsets.UTF_8));

        ManualImportWithPhotoResponse res =
                importService.importSingleStudentWithPhoto("Existing", "9876543210", photo);

        assertThat(res.getPhotoUrl()).contains(existing.getId().toString());
        // findOrCreateUser found an existing user, so save() is only called once —
        // to persist the photoUrl — not to create a brand-new user row.
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getPhotoUrl()).isEqualTo(res.getPhotoUrl());
    }

    // ── importStudents — CSV parsing + row outcomes ──────────────────────────

    @Test
    void importStudents_happyRow_createsUserAndCashMembership() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of(halfDayPlan(), fullDayPlan()));
        when(userRepository.findByMobile("9876543210")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return u;
        });

        String content = "#,Name,Phone,Fees,Date,Seat\n" +
                "1,John Doe,9876543210,400,01-06-2025,A1\n";

        ImportResultDto result = importService.importStudents(csv(content));

        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getSkipped()).isZero();
        assertThat(result.getErrors()).isEmpty();

        ArgumentCaptor<CreateCashMembershipRequest> captor = ArgumentCaptor.forClass(CreateCashMembershipRequest.class);
        verify(membershipService).createCashMembership(captor.capture());
        assertThat(captor.getValue().getSeatNumber()).isEqualTo("A1");
        assertThat(captor.getValue().getShift()).isEqualTo("MORNING"); // HALF_DAY plan (closest to 400)
    }

    @Test
    void importStudents_seatNumberNormalization_stripsSpacesDashesAndLeadingZeros() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of(halfDayPlan()));
        when(userRepository.findByMobile(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String content = "#,Name,Phone,Fees,Date,Seat\n" +
                "1,Jane Doe,9123456780,400,01-06-2025,b-002\n";

        importService.importStudents(csv(content));

        ArgumentCaptor<CreateCashMembershipRequest> captor = ArgumentCaptor.forClass(CreateCashMembershipRequest.class);
        verify(membershipService).createCashMembership(captor.capture());
        assertThat(captor.getValue().getSeatNumber()).isEqualTo("B2");
    }

    @Test
    void importStudents_closestPricePlanMatching_pricesFeesTowardsCheaperOrPricierPlan() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of(halfDayPlan(), fullDayPlan()));
        when(userRepository.findByMobile(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 550 is closer to 600 (Full Day) than to 400 (Half Day)
        String content = "#,Name,Phone,Fees,Date,Seat\n" +
                "1,Near Full,9111111111,550,01-06-2025,A1\n";

        importService.importStudents(csv(content));

        ArgumentCaptor<CreateCashMembershipRequest> captor = ArgumentCaptor.forClass(CreateCashMembershipRequest.class);
        verify(membershipService).createCashMembership(captor.capture());
        assertThat(captor.getValue().getShift()).isEqualTo("FULL_DAY");
    }

    @Test
    void importStudents_noActivePlans_recordsRowError() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of());
        when(userRepository.findByMobile(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String content = "#,Name,Phone,Fees,Date,Seat\n" +
                "1,No Plans,9111111111,400,01-06-2025,A1\n";

        ImportResultDto result = importService.importStudents(csv(content));

        assertThat(result.getImported()).isZero();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getReason()).contains("No active plans configured");
        verifyNoInteractions(membershipService);
    }

    @Test
    void importStudents_blankName_recordsRowError() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of(halfDayPlan()));

        String content = "#,Name,Phone,Fees,Date,Seat\n" +
                ",,9111111111,400,01-06-2025,A1\n";

        ImportResultDto result = importService.importStudents(csv(content));

        assertThat(result.getImported()).isZero();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getReason()).contains("Name is blank");
    }

    @Test
    void importStudents_blankPhone_recordsRowError() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of(halfDayPlan()));

        String content = "#,Name,Phone,Fees,Date,Seat\n" +
                "1,Bad Row,,400,01-06-2025,A1\n";

        ImportResultDto result = importService.importStudents(csv(content));

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getReason()).contains("Phone is blank");
    }

    @Test
    void importStudents_blankSeat_recordsRowError() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of(halfDayPlan()));

        String content = "#,Name,Phone,Fees,Date,Seat\n" +
                "1,No Seat,9111111111,400,01-06-2025,\n";

        ImportResultDto result = importService.importStudents(csv(content));

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getReason()).contains("Seat is blank");
    }

    @Test
    void importStudents_unparseableDate_recordsRowError() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of(halfDayPlan()));

        String content = "#,Name,Phone,Fees,Date,Seat\n" +
                "1,Bad Date,9111111111,400,not-a-date,A1\n";

        ImportResultDto result = importService.importStudents(csv(content));

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getReason()).contains("Cannot parse date");
    }

    @Test
    void importStudents_blankTrailingRow_ignoredNotCountedAsError() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of(halfDayPlan()));
        when(userRepository.findByMobile(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String content = "#,Name,Phone,Fees,Date,Seat\n" +
                "1,John Doe,9876543210,400,01-06-2025,A1\n" +
                ",,,,,\n";

        ImportResultDto result = importService.importStudents(csv(content));

        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void importStudents_startDateFarInPast_correctedToToday() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of(halfDayPlan())); // 30-day duration
        when(userRepository.findByMobile(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String content = "#,Name,Phone,Fees,Date,Seat\n" +
                "1,Old Join,9111111111,400,01-01-2020,A1\n";

        importService.importStudents(csv(content));

        ArgumentCaptor<CreateCashMembershipRequest> captor = ArgumentCaptor.forClass(CreateCashMembershipRequest.class);
        verify(membershipService).createCashMembership(captor.capture());
        assertThat(captor.getValue().getStartDate()).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void importStudents_quotedFieldWithComma_parsedAsSingleField() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of(halfDayPlan()));
        when(userRepository.findByMobile(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String content = "#,Name,Phone,Fees,Date,Seat\n" +
                "1,\"Doe, John\",9876543210,400,01-06-2025,A1\n";

        ImportResultDto result = importService.importStudents(csv(content));

        assertThat(result.getImported()).isEqualTo(1);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getName()).isEqualTo("Doe, John");
    }

    @Test
    void importStudents_headerDateFormat_overridesDefaultFallbackChain() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of(halfDayPlan()));
        when(userRepository.findByMobile(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // "05-04-2027" is ambiguous: the default fallback chain tries
        // MM-dd-yyyy first (-> May 4, 2027), but the header's explicit
        // "(dd-MM-yyyy)" pattern should force day=05/month=04 -> April 5, 2027.
        String content = "#,Name,Phone,Fees,Date (dd-MM-yyyy),Seat\n" +
                "1,Explicit Format,9111111111,400,05-04-2027,A1\n";

        importService.importStudents(csv(content));

        ArgumentCaptor<CreateCashMembershipRequest> captor = ArgumentCaptor.forClass(CreateCashMembershipRequest.class);
        verify(membershipService).createCashMembership(captor.capture());
        assertThat(captor.getValue().getStartDate()).isEqualTo("2027-04-05");
    }

    @Test
    void importStudents_multipleDateFormats_allParseSuccessfully() throws Exception {
        when(planRepository.findAll()).thenReturn(List.of(halfDayPlan()));
        when(userRepository.findByMobile(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String content = "#,Name,Phone,Fees,Date,Seat\n" +
                "1,Slash Format,9111111111,400,15/06/2025,A1\n" +
                "2,ISO Format,9222222222,400,2025-06-15,A2\n";

        ImportResultDto result = importService.importStudents(csv(content));

        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getErrors()).isEmpty();
    }
}
