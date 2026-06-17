package com.library.user.dto;

import com.library.user.entity.GalleryPhoto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class GalleryPhotoDto {
    private UUID id;
    private String url;
    private String caption;
    private String uploadedAt;

    public static GalleryPhotoDto fromEntity(GalleryPhoto p) {
        return new GalleryPhotoDto(
            p.getId(),
            p.getUrl(),
            p.getCaption(),
            p.getUploadedAt() != null ? p.getUploadedAt().toString() : null
        );
    }
}
