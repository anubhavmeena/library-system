package com.library.admin.repository;

import com.library.admin.entity.BroadcastMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BroadcastMessageRepository extends JpaRepository<BroadcastMessage, UUID> {
    List<BroadcastMessage> findTop5ByOrderBySentAtDesc();
}
