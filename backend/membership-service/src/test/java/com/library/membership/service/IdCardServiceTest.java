package com.library.membership.service;

import com.library.common.idcard.IdCardPdfGenerator;
import com.library.membership.dto.MembershipDto;
import com.library.membership.dto.UserApiResponse;
import com.library.membership.dto.UserProfileDto;
import com.library.membership.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdCardServiceTest {

    @Mock
    private MembershipService membershipService;
    @Mock
    private RestTemplate restTemplate;

    private IdCardService idCardService;

    @BeforeEach
    void setup() {
        idCardService = new IdCardService(membershipService, restTemplate, new IdCardPdfGenerator());
    }

    private MembershipDto buildMembership() {
        return MembershipDto.builder()
                .id("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
                .userId("user-123")
                .planName("Full Day Plan")
                .shift("FULL_DAY")
                .seatNumber("B12")
                .endDate("2026-08-03")
                .status("ACTIVE")
                .planPrice(BigDecimal.valueOf(600))
                .build();
    }

    private UserApiResponse buildUserApiResponse(String photoUrl) {
        UserProfileDto profile = new UserProfileDto();
        profile.setId("user-123");
        profile.setName("Manish Meena");
        profile.setFatherName("Suresh Meena");
        profile.setMobile("9876543210");
        profile.setPhotoUrl(photoUrl);
        profile.setDateOfBirth("2000-01-01");

        UserApiResponse resp = new UserApiResponse();
        resp.setSuccess(true);
        resp.setData(profile);
        return resp;
    }

    private byte[] tinyPngBytes() throws Exception {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    @Test
    void generateIdCard_activeMembershipWithPhoto_returnsValidPdf() throws Exception {
        when(membershipService.getUserActiveMembership("user-123")).thenReturn(buildMembership());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(UserApiResponse.class)))
                .thenReturn(ResponseEntity.ok(buildUserApiResponse("/uploads/photos/user-123.jpg")));
        when(restTemplate.getForEntity(anyString(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(tinyPngBytes(), HttpStatus.OK));

        byte[] pdf = idCardService.generateIdCard("user-123");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateIdCard_activeMembershipNoPhoto_fallsBackToSilhouette() {
        when(membershipService.getUserActiveMembership("user-123")).thenReturn(buildMembership());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(UserApiResponse.class)))
                .thenReturn(ResponseEntity.ok(buildUserApiResponse(null)));

        byte[] pdf = idCardService.generateIdCard("user-123");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateIdCard_noActiveMembership_throwsResourceNotFound() {
        when(membershipService.getUserActiveMembership("user-123")).thenReturn(null);

        assertThatThrownBy(() -> idCardService.generateIdCard("user-123"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generateIdCard_userProfileFetchFails_propagatesRuntimeException() {
        when(membershipService.getUserActiveMembership("user-123")).thenReturn(buildMembership());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(UserApiResponse.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> idCardService.generateIdCard("user-123"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void generateIdCard_invalidPhotoBytes_fallsBackToSilhouetteGracefully() {
        when(membershipService.getUserActiveMembership("user-123")).thenReturn(buildMembership());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(UserApiResponse.class)))
                .thenReturn(ResponseEntity.ok(buildUserApiResponse("/uploads/photos/user-123.jpg")));
        when(restTemplate.getForEntity(anyString(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(new byte[]{1, 2, 3}, HttpStatus.OK));

        byte[] pdf = idCardService.generateIdCard("user-123");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
