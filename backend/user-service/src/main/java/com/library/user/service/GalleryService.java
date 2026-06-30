package com.library.user.service;

import com.library.user.dto.GalleryPhotoDto;
import com.library.user.entity.GalleryPhoto;
import com.library.user.exception.ResourceNotFoundException;
import com.library.user.repository.GalleryPhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryService {

    private final GalleryPhotoRepository galleryPhotoRepository;

    @Value("${app.upload.dir:/app/uploads}")
    private String uploadDir;

    public List<GalleryPhotoDto> getAll() {
        return galleryPhotoRepository.findAllByOrderByUploadedAtDesc()
                .stream()
                .map(GalleryPhotoDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public GalleryPhotoDto upload(MultipartFile file, String caption, String userId) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !List.of("image/jpeg", "image/png", "image/webp").contains(contentType))
            throw new IllegalArgumentException("Invalid file type. Only JPEG, PNG, WebP allowed.");

        if (file.getSize() > 5_242_880)
            throw new IllegalArgumentException("File must not exceed 5MB.");

        Path uploadPath = Paths.get(uploadDir, "gallery");
        Files.createDirectories(uploadPath);

        String ext      = getExtension(file.getOriginalFilename());
        String fileName = "gallery_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Files.copy(file.getInputStream(), uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

        String url = "/java-uploads/gallery/" + fileName;
        GalleryPhoto photo = GalleryPhoto.builder()
                .url(url)
                .caption(caption != null && !caption.isBlank() ? caption.trim() : null)
                .uploadedBy(userId)
                .build();
        galleryPhotoRepository.save(photo);

        log.info("Gallery photo uploaded by {}: {}", userId, url);
        return GalleryPhotoDto.fromEntity(photo);
    }

    @Transactional
    public void delete(UUID id) throws IOException {
        GalleryPhoto photo = galleryPhotoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found: " + id));

        try {
            Path filePath = Paths.get(uploadDir, "gallery",
                    Paths.get(photo.getUrl()).getFileName().toString());
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            log.warn("Could not delete gallery file: {}", photo.getUrl(), e);
        }

        galleryPhotoRepository.delete(photo);
        log.info("Gallery photo deleted: {}", id);
    }

    private String getExtension(String filename) {
        if (filename == null) return ".jpg";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : ".jpg";
    }
}
