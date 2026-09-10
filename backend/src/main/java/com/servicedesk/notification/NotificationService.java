package com.servicedesk.notification;

import com.servicedesk.common.PagedResponse;
import com.servicedesk.common.exception.ResourceNotFoundException;
import com.servicedesk.common.exception.UnauthorizedActionException;
import com.servicedesk.notification.dto.NotificationResponse;
import com.servicedesk.user.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * In-app notifications. Kept intentionally simple (a DB row + an
 * unread-count endpoint the frontend can poll) rather than adding email/
 * push/websocket delivery, which would need infrastructure this project
 * doesn't have. Called directly from the services that know when
 * something notification-worthy happened, rather than via Kafka - most
 * of these moments (e.g. "remediation now needs approval") don't already
 * have a Kafka event to hang off of, and adding one just to trigger a
 * notification would be exactly the kind of unnecessary Kafka usage the
 * spec warns against.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;

    @Transactional
    public void notify(User recipient, NotificationType type, String message, UUID relatedIncidentId) {
        if (recipient == null) {
            log.debug("Skipping notification {} - no recipient available", type);
            return;
        }
        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(type)
                .message(message)
                .relatedIncidentId(relatedIncidentId)
                .build();
        repository.save(notification);
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> list(User currentUser, boolean unreadOnly, Pageable pageable) {
        var page = unreadOnly
                ? repository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(currentUser.getId(), pageable)
                : repository.findByRecipientIdOrderByCreatedAtDesc(currentUser.getId(), pageable);
        return PagedResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public long unreadCount(User currentUser) {
        return repository.countByRecipientIdAndReadFalse(currentUser.getId());
    }

    @Transactional
    public void markRead(UUID notificationId, User currentUser) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (!notification.getRecipient().getId().equals(currentUser.getId())) {
            throw new UnauthorizedActionException("You can only mark your own notifications as read");
        }
        notification.setRead(true);
        repository.save(notification);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.getRelatedIncidentId(),
                n.isRead(), n.getCreatedAt());
    }
}
