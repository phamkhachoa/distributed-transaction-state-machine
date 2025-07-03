package com.example.inventoryservice.service;

import com.example.inventoryservice.model.Inventory;
import com.example.inventoryservice.model.OrderItem;
import com.example.inventoryservice.repository.InventoryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void reserveInventory(String orderId, String itemsJson) throws IOException {
        List<OrderItem> items = objectMapper.readValue(itemsJson, new TypeReference<>() {});

        for (OrderItem item : items) {
            Inventory inventoryItem = inventoryRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));
            
            if (inventoryItem.getQuantity() < item.getQuantity()) {
                throw new RuntimeException("Insufficient stock for product: " + item.getProductId());
            }
            
            inventoryItem.setQuantity(inventoryItem.getQuantity() - item.getQuantity());
            inventoryRepository.save(inventoryItem);
        }
    }

    @Transactional
    public void releaseInventory(String orderId, String itemsJson) throws IOException {
        List<OrderItem> items = objectMapper.readValue(itemsJson, new TypeReference<>() {});

        for (OrderItem item : items) {
            inventoryRepository.findById(item.getProductId()).ifPresent(inventoryItem -> {
                inventoryItem.setQuantity(inventoryItem.getQuantity() + item.getQuantity());
                inventoryRepository.save(inventoryItem);
            });
        }
    }
} 