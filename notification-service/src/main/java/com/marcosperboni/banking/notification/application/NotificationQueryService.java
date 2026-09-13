package com.marcosperboni.banking.notification.application;

import com.marcosperboni.banking.notification.domain.Notification;
import com.marcosperboni.banking.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Ownership of {@code accountId} is not verified against account-service:
 * doing so would require an HTTP client dependency solely for this one
 * read-only, non-sensitive history endpoint. Any authenticated caller may
 * query any account's notification history. Accepted simplification for a
 * portfolio notification/read-log service.
 */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    public Page<Notification> getByAccount(UUID accountId, Pageable pageable) {
        return notificationRepository.findByAccountId(accountId, pageable);
    }
}
