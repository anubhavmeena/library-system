package com.library.user.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoUploadResponseTest {

    @Test
    void builder_setsAllFields() {
        PhotoUploadResponse resp = PhotoUploadResponse.builder()
                .photoUrl("/java-uploads/photos/user_abc.jpg")
                .message("Photo uploaded successfully")
                .build();

        assertThat(resp.getPhotoUrl()).isEqualTo("/java-uploads/photos/user_abc.jpg");
        assertThat(resp.getMessage()).isEqualTo("Photo uploaded successfully");
    }

    @Test
    void noArgsConstructor_fieldsNull() {
        PhotoUploadResponse resp = new PhotoUploadResponse();
        assertThat(resp.getPhotoUrl()).isNull();
        assertThat(resp.getMessage()).isNull();
    }

    @Test
    void setters_work() {
        PhotoUploadResponse resp = new PhotoUploadResponse();
        resp.setPhotoUrl("/java-uploads/photos/test.png");
        resp.setMessage("Done");

        assertThat(resp.getPhotoUrl()).isEqualTo("/java-uploads/photos/test.png");
        assertThat(resp.getMessage()).isEqualTo("Done");
    }
}
