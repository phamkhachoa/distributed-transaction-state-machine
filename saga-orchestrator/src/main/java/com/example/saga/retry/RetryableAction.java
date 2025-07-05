package com.example.saga.retry;

import com.example.saga.persistence.SagaInstance;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

@Slf4j
@Data
@Builder
public class RetryableAction<T> {
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_INITIAL_DELAY = 1000; // 1 second
    private static final double DEFAULT_MULTIPLIER = 2.0;
    private static final long DEFAULT_MAX_DELAY = 60000; // 1 minute
    
    @Builder.Default
    private int maxRetries = DEFAULT_MAX_RETRIES;
    
    @Builder.Default
    private long initialDelay = DEFAULT_INITIAL_DELAY;
    
    @Builder.Default
    private double multiplier = DEFAULT_MULTIPLIER;
    
    @Builder.Default
    private long maxDelay = DEFAULT_MAX_DELAY;
    
    private Callable<T> action;
    private Consumer<Exception> onError;
    private Consumer<T> onSuccess;
    private Runnable onExhausted;
    private SagaInstance sagaInstance;
    
    public T execute() throws Exception {
        int attempts = sagaInstance != null ? (sagaInstance.getLastRetryCount() != null ? sagaInstance.getLastRetryCount() : 0) : 0;
        Exception lastException = null;
        
        while (attempts < maxRetries) {
            try {
                T result = action.call();
                if (onSuccess != null) {
                    onSuccess.accept(result);
                }
                return result;
                
            } catch (Exception e) {
                lastException = e;
                attempts++;
                
                if (sagaInstance != null) {
                    sagaInstance.incrementRetryCount();
                }
                
                if (onError != null) {
                    onError.accept(e);
                }
                
                if (attempts < maxRetries) {
                    long delay = calculateDelay(attempts);
                    
                    if (sagaInstance != null) {
                        sagaInstance.updateNextRetryTime(delay / 1000); // Convert to seconds
                    }
                    
                    log.warn("Attempt {} failed, retrying in {} ms", attempts, delay, e);
                    Thread.sleep(delay);
                }
            }
        }
        
        log.error("All {} retry attempts exhausted", maxRetries);
        if (onExhausted != null) {
            onExhausted.run();
        }
        throw lastException;
    }
    
    private long calculateDelay(int attempt) {
        double exponentialDelay = initialDelay * Math.pow(multiplier, attempt - 1);
        return Math.min(Math.round(exponentialDelay), maxDelay);
    }
    
    public static <T> RetryableActionBuilder<T> builder() {
        return new RetryableActionBuilder<>();
    }
    
    public static class RetryableActionBuilder<T> {
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private long initialDelay = DEFAULT_INITIAL_DELAY;
        private double multiplier = DEFAULT_MULTIPLIER;
        private long maxDelay = DEFAULT_MAX_DELAY;
        private Callable<T> action;
        private Consumer<Exception> onError;
        private Consumer<T> onSuccess;
        private Runnable onExhausted;
        private SagaInstance sagaInstance;
        
        public RetryableActionBuilder<T> maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public RetryableActionBuilder<T> initialDelay(long initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }
        
        public RetryableActionBuilder<T> multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }
        
        public RetryableActionBuilder<T> maxDelay(long maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }
        
        public RetryableActionBuilder<T> action(Callable<T> action) {
            this.action = action;
            return this;
        }
        
        public RetryableActionBuilder<T> onError(Consumer<Exception> onError) {
            this.onError = onError;
            return this;
        }
        
        public RetryableActionBuilder<T> onSuccess(Consumer<T> onSuccess) {
            this.onSuccess = onSuccess;
            return this;
        }
        
        public RetryableActionBuilder<T> onExhausted(Runnable onExhausted) {
            this.onExhausted = onExhausted;
            return this;
        }
        
        public RetryableActionBuilder<T> sagaInstance(SagaInstance sagaInstance) {
            this.sagaInstance = sagaInstance;
            return this;
        }
        
        public RetryableAction<T> build() {
            return new RetryableAction<>(maxRetries, initialDelay, multiplier, maxDelay,
                    action, onError, onSuccess, onExhausted, sagaInstance);
        }
    }
} 