package com.library.user.controller;

import com.library.common.dto.ApiResponse;
import com.library.user.dto.GalleryPhotoDto;
import com.library.user.service.GalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GalleryController {

    private final GalleryService galleryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GalleryPhotoDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(galleryService.getAll()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<GalleryPhotoDto>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "caption", required = false) String caption,
            @RequestHeader("X-User-Role") String userRole,
            @RequestHeader("X-User-Id") String userId) throws IOException {

        if (!"ADMIN".equals(userRole))
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("Only admins can upload gallery photos"));

        return ResponseEntity.ok(ApiResponse.success(galleryService.upload(file, caption, userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(
            @PathVariable UUID id,
            @RequestHeader("X-User-Role") String userRole) throws IOException {

        if (!"ADMIN".equals(userRole))
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("Only admins can delete gallery photos"));

        galleryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Photo deleted"));
    }
}
