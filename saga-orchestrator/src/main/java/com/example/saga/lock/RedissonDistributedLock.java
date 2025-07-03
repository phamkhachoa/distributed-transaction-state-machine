package com.example.saga.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonDistributedLock implements DistributedLock {

    private final RedissonClient redisson;
    private static final String LOCK_PREFIX = "saga:lock:";
    private static final String INSTANCE_ID = System.getenv().getOrDefault("HOSTNAME", "unknown");

    @Override
    public boolean tryLock(String lockKey, long timeout, TimeUnit timeUnit) {
        String key = LOCK_PREFIX + lockKey;
        RLock lock = redisson.getLock(key);
        
        try {
            boolean acquired = lock.tryLock(timeout, timeUnit);
            if (acquired) {
                log.debug("Acquired lock for key: {} by instance: {}", lockKey, INSTANCE_ID);
            } else {
                log.debug("Failed to acquire lock for key: {} by instance: {}", lockKey, INSTANCE_ID);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring lock for key: {}", lockKey, e);
            return false;
        } catch (Exception e) {
            log.error("Error acquiring lock for key: {}", lockKey, e);
            return false;
        }
    }

    @Override
    public void unlock(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        RLock lock = redisson.getLock(key);
        
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released lock for key: {} by instance: {}", lockKey, INSTANCE_ID);
            }
        } catch (Exception e) {
            log.error("Error releasing lock for key: {}", lockKey, e);
        }
    }

    @Override
    public boolean isLocked(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        RLock lock = redisson.getLock(key);
        
        try {
            return lock.isLocked();
        } catch (Exception e) {
            log.error("Error checking lock status for key: {}", lockKey, e);
            return true; // Assume locked on error to be safe
        }
    }

    /**
     * Force unlock a lock regardless of who owns it.
     * Use with caution! Only for administrative purposes.
     */
    public void forceUnlock(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        RLock lock = redisson.getLock(key);
        
        try {
            if (lock.isLocked()) {
                lock.forceUnlock();
                log.warn("Force unlocked key: {} by instance: {}", lockKey, INSTANCE_ID);
            }
        } catch (Exception e) {
            log.error("Error force unlocking key: {}", lockKey, e);
        }
    }

    /**
     * Get information about the current lock holder
     */
    public String getLockInfo(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        RLock lock = redisson.getLock(key);
        
        try {
            if (lock.isLocked()) {
                return String.format("Locked (Thread: %s, Lease time remaining: %dms)",
                    lock.isHeldByCurrentThread() ? "current" : "other",
                    lock.remainTimeToLive());
            }
            return "Unlocked";
        } catch (Exception e) {
            log.error("Error getting lock info for key: {}", lockKey, e);
            return "Error getting lock info";
        }
    }
} 