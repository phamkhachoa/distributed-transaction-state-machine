package com.example.saga.lock;

import java.util.concurrent.TimeUnit;

public interface DistributedLock {
    
    /**
     * Try to acquire a lock with timeout
     * @param lockKey the key to lock
     * @param timeout the maximum time to wait for the lock
     * @param timeUnit the time unit of the timeout argument
     * @return true if lock was acquired, false otherwise
     */
    boolean tryLock(String lockKey, long timeout, TimeUnit timeUnit);
    
    /**
     * Release the lock
     * @param lockKey the key to unlock
     */
    void unlock(String lockKey);
    
    /**
     * Check if the lock is held by any process
     * @param lockKey the key to check
     * @return true if the lock is held
     */
    boolean isLocked(String lockKey);
} 