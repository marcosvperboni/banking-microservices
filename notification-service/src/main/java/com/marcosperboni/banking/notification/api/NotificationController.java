package com.marcosperboni.banking.notification.api;

import com.marcosperboni.banking.notification.api.dto.NotificationResponse;
import com.marcosperboni.banking.notification.application.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> findByAccount(
            @RequestParam UUID accountId, Pageable pageable) {
        Page<NotificationResponse> page = notificationQueryService.getByAccount(accountId, pageable)
                .map(NotificationResponse::from);
        return ResponseEntity.ok(page);
    }
}
