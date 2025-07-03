package com.example.saga.service;

import com.example.saga.model.ShippingContext;
import com.example.saga.model.ShippingEvents;
import com.example.saga.model.ShippingStates;
import com.example.saga.persistence.SagaInstance;
import com.example.saga.persistence.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing shipping sagas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingSagaService {

    private final SagaOrchestrationService orchestrationService;
    private final SagaInstanceRepository sagaInstanceRepository;

    /**
     * Start a new shipping saga
     * 
     * @param shippingContext The shipping context
     * @return The ID of the created shipping saga
     */
    public String startShippingSaga(ShippingContext shippingContext) {
        log.info("Starting shipping saga for order: {}", shippingContext.getOrderId());
        
        // Start a new shipping saga
        String sagaId = orchestrationService.startSaga("SHIPPING_SAGA", shippingContext);
        
        // Set the saga ID in the context
        shippingContext.setSagaId(sagaId);
        
        // Send the start event to the saga
        orchestrationService.sendEvent(sagaId, ShippingEvents.START_SHIPPING);
        
        return sagaId;
    }

    @Transactional
    public void updateShippingStatus(String sagaId, String status, String location) {
        SagaInstance saga = sagaInstanceRepository.findById(sagaId)
                .orElseThrow(() -> new RuntimeException("Shipping saga not found: " + sagaId));

        // Update context with new status
        ShippingContext context = (ShippingContext) orchestrationService.getSagaContext(sagaId);
        if (context == null) {
            log.warn("No shipping context found for saga: {}", sagaId);
            return;
        }
        
        context.setCurrentStatus(status);
        context.setCurrentLocation(location);
        context.setLastStatusUpdate(LocalDateTime.now());
        
        // Determine which event to send based on status
        ShippingEvents event = mapStatusToEvent(status);
        if (event != null) {
            orchestrationService.sendEvent(sagaId, event);
            log.info("Sent event {} to shipping saga {}", event, sagaId);
        }
        
        // Update the context in the saga
        orchestrationService.updateSagaContext(sagaId, context);
    }

    /**
     * Map a status string to a shipping event
     */
    private ShippingEvents mapStatusToEvent(String status) {
        if (status == null) {
            return null;
        }
        
        switch (status.toUpperCase()) {
            case "PREPARED":
                return ShippingEvents.SELLER_PREPARED;
            case "PICKED_UP":
                return ShippingEvents.PICKUP_COMPLETED;
            case "AT_SORTING_CENTER":
                return ShippingEvents.SORTING_RECEIVED;
            case "SORTING_COMPLETED":
                return ShippingEvents.SORTING_COMPLETED;
            case "AT_LOCAL_WAREHOUSE":
                return ShippingEvents.LOCAL_RECEIVED;
            case "READY_FOR_DELIVERY":
                return ShippingEvents.LOCAL_READY;
            case "OUT_FOR_DELIVERY":
                return ShippingEvents.START_DELIVERY;
            case "DELIVERED":
                return ShippingEvents.DELIVERY_COMPLETED;
            default:
                return null;
        }
    }

    @Transactional
    public void handleDeliveryFailure(String sagaId, String reason) {
        SagaInstance saga = sagaInstanceRepository.findById(sagaId)
                .orElseThrow(() -> new RuntimeException("Shipping saga not found: " + sagaId));

        // Update context with failure reason
        ShippingContext context = (ShippingContext) orchestrationService.getSagaContext(sagaId);
        if (context == null) {
            log.warn("No shipping context found for saga: {}", sagaId);
            return;
        }
        
        context.setFailureReason(reason);
        context.setLastStatusUpdate(LocalDateTime.now());
        
        // Trigger delivery failed event
        orchestrationService.sendEvent(sagaId, ShippingEvents.DELIVERY_ATTEMPT_FAILED);

        log.warn("Delivery failed for saga {}: {}", sagaId, reason);
    }

    @Transactional
    public void startReturn(String sagaId, String reason) {
        SagaInstance saga = sagaInstanceRepository.findById(sagaId)
                .orElseThrow(() -> new RuntimeException("Shipping saga not found: " + sagaId));

        // Update context with return reason
        ShippingContext context = (ShippingContext) orchestrationService.getSagaContext(sagaId);
        if (context == null) {
            log.warn("No shipping context found for saga: {}", sagaId);
            return;
        }
        
        context.setReturnReason(reason);
        context.setReturnStarted(LocalDateTime.now());
        
        // Trigger return flow
        orchestrationService.sendEvent(sagaId, ShippingEvents.START_RETURN);

        log.info("Started return process for saga {}: {}", sagaId, reason);
    }

    /**
     * Cancel a shipping saga
     */
    public void cancelShippingSaga(String sagaId) {
        // Get the current context
        ShippingContext context = (ShippingContext) orchestrationService.getSagaContext(sagaId);
        if (context == null) {
            log.warn("No shipping context found for saga: {}", sagaId);
            return;
        }
        
        // Send cancel event
        orchestrationService.sendEvent(sagaId, ShippingEvents.CANCEL_SHIPPING);
        log.info("Sent cancel event to shipping saga: {}", sagaId);
    }

    /**
     * Complete a shipping saga
     */
    public void completeShippingSaga(String sagaId) {
        // Get the current context
        ShippingContext context = (ShippingContext) orchestrationService.getSagaContext(sagaId);
        if (context == null) {
            log.warn("No shipping context found for saga: {}", sagaId);
            return;
        }
        
        // Send complete event
        orchestrationService.sendEvent(sagaId, ShippingEvents.COMPLETE_SHIPPING);
        log.info("Sent complete event to shipping saga: {}", sagaId);
    }
} 