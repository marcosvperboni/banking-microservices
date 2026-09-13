package com.marcosperboni.banking.notification.domain.repository;

import com.marcosperboni.banking.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByAccountId(UUID accountId, Pageable pageable);
}
