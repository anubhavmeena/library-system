package com.library.membership.service;

import com.library.common.idcard.IdCardData;
import com.library.common.idcard.IdCardIdGenerator;
import com.library.common.idcard.IdCardPdfGenerator;
import com.library.membership.dto.MembershipDto;
import com.library.membership.dto.UserApiResponse;
import com.library.membership.dto.UserProfileDto;
import com.library.membership.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdCardService {

    private final MembershipService membershipService;
    private final RestTemplate      restTemplate;
    private final IdCardPdfGenerator idCardPdfGenerator;

    @Value("${app.user-service.base-url}")
    private String userServiceBaseUrl;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public byte[] generateIdCard(String userId) {
        MembershipDto membership = membershipService.getUserActiveMembership(userId);
        if (membership == null) {
            throw new ResourceNotFoundException(
                    "No active membership found. Purchase a plan to download your ID card.");
        }
        UserProfileDto user       = fetchUserProfile(userId);
        byte[]         photoBytes = fetchPhotoBytes(user.getPhotoUrl());
        return idCardPdfGenerator.generate(toIdCardData(user, membership, photoBytes));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cross-service HTTP calls
    // ─────────────────────────────────────────────────────────────────────────

    private UserProfileDto fetchUserProfile(String userId) {
        String url = userServiceBaseUrl + "/api/users/" + userId;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id",   userId);
            headers.set("X-User-Role", "STUDENT");
            HttpEntity<Void> req = new HttpEntity<>(headers);

            ResponseEntity<UserApiResponse> resp = restTemplate.exchange(
                    url, HttpMethod.GET, req, UserApiResponse.class);

            if (resp.getBody() != null && resp.getBody().getData() != null) {
                return resp.getBody().getData();
            }
            throw new ResourceNotFoundException("User profile not found for ID: " + userId);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch user profile for userId={}: {}", userId, e.getMessage());
            throw new RuntimeException("Unable to retrieve user profile. Please try again.");
        }
    }

    private byte[] fetchPhotoBytes(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) return null;
        try {
            ResponseEntity<byte[]> resp = restTemplate.getForEntity(
                    userServiceBaseUrl + photoUrl, byte[].class);
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                return resp.getBody();
            }
        } catch (Exception e) {
            log.warn("Could not fetch photo from {}: {} — proceeding without photo",
                    photoUrl, e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapping to the shared IdCardData / renderer (common-lib)
    // ─────────────────────────────────────────────────────────────────────────

    private IdCardData toIdCardData(UserProfileDto user, MembershipDto m, byte[] photoBytes) {
        return new IdCardData(
                orDash(user.getName()),
                orDash(user.getMobile()),
                IdCardIdGenerator.shortId(m.getId()),
                m.getPlanName(),
                formatDateDdMmmYyyy(m.getEndDate()),
                photoBytes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String formatDateDdMmmYyyy(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "—";
        try {
            LocalDate date = LocalDate.parse(isoDate);
            return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }

    private String orDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }
}
