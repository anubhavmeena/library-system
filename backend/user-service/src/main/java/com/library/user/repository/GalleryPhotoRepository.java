package com.library.user.repository;

import com.library.user.entity.GalleryPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GalleryPhotoRepository extends JpaRepository<GalleryPhoto, UUID> {
    List<GalleryPhoto> findAllByOrderByUploadedAtDesc();
}
