package com.example.saga.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SagaHistoryRepository extends JpaRepository<SagaHistory, Long> {
    
    List<SagaHistory> findBySagaIdOrderByCreatedAtAsc(String sagaId);
    
    List<SagaHistory> findBySagaTypeOrderByCreatedAtDesc(String sagaType);
    
    List<SagaHistory> findBySourceStateAndTargetStateOrderByCreatedAtDesc(
            String sourceState, String targetState);
} 