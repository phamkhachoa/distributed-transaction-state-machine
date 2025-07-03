package com.example.inventoryservice.service;

import com.example.common.dto.SagaCommand;
import com.example.inventoryservice.entity.Inventory;
import com.example.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional
    public void deductInventory(SagaCommand command) {
        Map<String, Object> payload = command.getPayload();
        Integer productId = (Integer) payload.get("productId");
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));

        if (inventory.getQuantity() <= 0) {
            throw new RuntimeException("Insufficient inventory for product: " + productId);
        }

        inventory.setQuantity(inventory.getQuantity() - 1);
        inventoryRepository.save(inventory);
        log.info("Inventory deducted for product: {}", productId);
    }

    @Transactional
    public void addInventory(SagaCommand command) {
        Map<String, Object> payload = command.getPayload();
        Integer productId = (Integer) payload.get("productId");
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));

        inventory.setQuantity(inventory.getQuantity() + 1);
        inventoryRepository.save(inventory);
        log.info("Inventory compensated for product: {}", productId);
    }
} 