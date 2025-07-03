package com.example.notificationservice.listener;

import com.example.notificationservice.dto.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationListener {

    public static final String PAYMENT_SUCCESS_TOPIC = "payment.success.events";

    @KafkaListener(topics = PAYMENT_SUCCESS_TOPIC, groupId = "notification_group")
    public void onPaymentSuccess(PaymentSucceededEvent event) {
        log.info("Received PaymentSucceededEvent from Kafka: {}", event);
        
        // Simulate sending an email
        log.info("-----> Sending confirmation email to customer {} for order {} <-----", 
                event.getCustomerId(), event.getOrderId());
        
        // In a real application, you would integrate with an email service (e.g., SendGrid, AWS SES) here.
        // Add error handling as needed, e.g., push to a dead-letter queue if email sending fails.
    }
} 