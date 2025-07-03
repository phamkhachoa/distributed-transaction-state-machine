package com.example.saga.controller;

import com.example.saga.lock.DistributedLockImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/locks")
@RequiredArgsConstructor
public class LockManagementController {

    private final DistributedLockImpl lockService;

    @GetMapping("/{lockKey}/status")
    public ResponseEntity<Map<String, Object>> getLockStatus(@PathVariable String lockKey) {
        try {
            boolean isLocked = lockService.isLocked(lockKey);
            String lockInfo = lockService.getLockInfo(lockKey);
            
            Map<String, Object> status = new HashMap<>();
            status.put("key", lockKey);
            status.put("locked", isLocked);
            status.put("info", lockInfo);
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting lock status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{lockKey}/force-unlock")
    public ResponseEntity<Map<String, Object>> forceUnlock(
            @PathVariable String lockKey,
            @RequestParam(required = false) String reason) {
        try {
            log.warn("Force unlocking {} (Reason: {})", lockKey, reason);
            lockService.forceUnlock(lockKey);
            
            Map<String, Object> result = new HashMap<>();
            result.put("key", lockKey);
            result.put("action", "force-unlocked");
            result.put("reason", reason);
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error force unlocking", e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 