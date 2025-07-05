package com.example.common.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Service để xử lý idempotency
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedMessageRepository processedMessageRepository;
    
    /**
     * Thực hiện một operation với idempotency
     * @param requestId ID của request
     * @param sagaId ID của saga
     * @param serviceName Tên service
     * @param operation Tên operation
     * @param supplier Function thực hiện operation
     * @return Kết quả của operation
     */
    @Transactional
    public <T> T executeWithIdempotency(
            String requestId,
            String sagaId,
            String serviceName,
            String operation,
            Supplier<T> supplier) {
        
        // Kiểm tra xem request đã được xử lý chưa
        Optional<ProcessedMessage> existingMessage = 
                processedMessageRepository.findByRequestId(requestId);
        
        if (existingMessage.isPresent()) {
            log.info("Duplicate request detected: {} for operation: {}", requestId, operation);
            // Trả về null nếu đã xử lý trước đó
            return null;
        }
        
        try {
            // Thực hiện operation
            T result = supplier.get();
            
            // Lưu thông tin request đã xử lý
            ProcessedMessage message = ProcessedMessage.builder()
                    .requestId(requestId)
                    .sagaId(sagaId)
                    .serviceName(serviceName)
                    .operation(operation)
                    .status("SUCCESS")
                    .resultId(result != null ? result.toString() : null)
                    .build();
            
            processedMessageRepository.save(message);
            
            return result;
        } catch (Exception e) {
            // Lưu thông tin request đã xử lý với lỗi
            ProcessedMessage message = ProcessedMessage.builder()
                    .requestId(requestId)
                    .sagaId(sagaId)
                    .serviceName(serviceName)
                    .operation(operation)
                    .status("FAILED")
                    .build();
            
            processedMessageRepository.save(message);
            
            throw e;
        }
    }
    
    /**
     * Kiểm tra xem một request đã được xử lý chưa
     */
    public boolean isProcessed(String requestId) {
        return processedMessageRepository.existsByRequestId(requestId);
    }
    
    /**
     * Kiểm tra xem một saga và operation đã được xử lý chưa
     */
    public boolean isProcessed(String sagaId, String operation) {
        return processedMessageRepository.findBySagaIdAndOperation(sagaId, operation).isPresent();
    }
} 