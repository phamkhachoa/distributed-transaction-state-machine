package com.example.saga.controller;

import com.example.saga.persistence.SagaHistory;
import com.example.saga.persistence.SagaHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/saga/history")
@RequiredArgsConstructor
public class SagaHistoryController {

    private final SagaHistoryRepository historyRepository;

    @GetMapping("/{sagaId}")
    public ResponseEntity<List<SagaHistory>> getSagaHistory(@PathVariable String sagaId) {
        try {
            List<SagaHistory> history = historyRepository.findBySagaIdOrderByCreatedAtAsc(sagaId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error getting saga history", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/type/{sagaType}")
    public ResponseEntity<List<SagaHistory>> getSagaHistoryByType(
            @PathVariable String sagaType,
            @RequestParam(required = false) Integer limit) {
        try {
            List<SagaHistory> history = historyRepository.findBySagaTypeOrderByCreatedAtDesc(sagaType);
            if (limit != null && limit > 0) {
                history = history.stream()
                        .limit(limit)
                        .collect(Collectors.toList());
            }
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error getting saga history by type", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/transition")
    public ResponseEntity<List<SagaHistory>> getTransitionHistory(
            @RequestParam String sourceState,
            @RequestParam String targetState) {
        try {
            List<SagaHistory> history = historyRepository
                    .findBySourceStateAndTargetStateOrderByCreatedAtDesc(sourceState, targetState);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error getting transition history", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/summary/{sagaId}")
    public ResponseEntity<Map<String, Object>> getSagaSummary(@PathVariable String sagaId) {
        try {
            List<SagaHistory> history = historyRepository.findBySagaIdOrderByCreatedAtAsc(sagaId);
            
            if (history.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Calculate summary
            long totalExecutionTime = history.stream()
                    .mapToLong(h -> h.getExecutionTimeMs() != null ? h.getExecutionTimeMs() : 0)
                    .sum();
            
            String currentState = history.get(history.size() - 1).getTargetState();
            
            boolean hasErrors = history.stream()
                    .anyMatch(h -> h.getErrorMessage() != null);
            
            Map<String, Object> summary = Map.of(
                "sagaId", sagaId,
                "sagaType", history.get(0).getSagaType(),
                "totalSteps", history.size(),
                "totalExecutionTime", totalExecutionTime,
                "currentState", currentState,
                "startTime", history.get(0).getCreatedAt(),
                "lastUpdateTime", history.get(history.size() - 1).getCreatedAt(),
                "hasErrors", hasErrors,
                "transitions", history
            );

            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            log.error("Error getting saga summary", e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 