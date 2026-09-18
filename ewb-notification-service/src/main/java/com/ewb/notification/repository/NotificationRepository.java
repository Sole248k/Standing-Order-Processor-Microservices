package com.ewb.notification.repository;

import com.ewb.notification.entity.NotificationDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationDeliveryEntity, Long> {
    Optional<NotificationDeliveryEntity> findByEventId(String eventId);
    List<NotificationDeliveryEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
