package com.example.saga.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for persisting and retrieving saga instances
 */
@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, String> {
    
    Optional<SagaInstance> findByOrderId(String orderId);
    
    List<SagaInstance> findByStatus(String status);
    
    List<SagaInstance> findByStatusNot(String status);
    
    List<SagaInstance> findByCurrentState(String currentState);
    
    @Query("SELECT s FROM SagaInstance s WHERE s.status = :status " +
           "AND s.updatedAt < :threshold " +
           "AND s.currentState NOT IN ('ORDER_COMPLETED', 'ORDER_CANCELLED', 'COMPENSATION_COMPLETED')")
    List<SagaInstance> findStuckSagas(
        @Param("threshold") LocalDateTime threshold,
        @Param("status") String status
    );
    
    @Query("SELECT s FROM SagaInstance s WHERE s.status = 'IN_PROGRESS' " +
           "AND s.timeoutAt < :now")
    List<SagaInstance> findTimedOutSagas(@Param("now") LocalDateTime now);
    
    @Query("SELECT s FROM SagaInstance s WHERE s.status = 'IN_PROGRESS' " +
           "AND s.nextRetryTime IS NOT NULL " +
           "AND s.nextRetryTime < :now " +
           "AND s.lastRetryCount < :maxRetries")
    List<SagaInstance> findSagasForRetry(
        @Param("now") LocalDateTime now,
        @Param("maxRetries") int maxRetries
    );
    
    @Query("SELECT s FROM SagaInstance s WHERE s.compensationTriggered = true " +
           "AND s.status = 'COMPENSATING'")
    List<SagaInstance> findCompensatingSagas();

    /**
     * Find all sagas of a specific type
     */
    List<SagaInstance> findBySagaType(String sagaType);
    
    /**
     * Find all sagas in a specific state
     */
    List<SagaInstance> findBySagaTypeAndCurrentState(String sagaType, String currentState);
    
    /**
     * Find sagas that have timed out
     */
    List<SagaInstance> findByTimeoutAtBeforeAndStatus(LocalDateTime now, String status);
    
    /**
     * Find sagas by metadata key and value
     */
    @Query("SELECT s FROM SagaInstance s JOIN s.metadata m WHERE KEY(m) = :key AND VALUE(m) = :value")
    List<SagaInstance> findByMetadata(@Param("key") String key, @Param("value") String value);
} 