package com.example.inventoryservice.service;

import com.example.inventoryservice.model.Inventory;
import com.example.inventoryservice.model.OrderItem;
import com.example.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ConcurrentHashMap<String, List<OrderItem>> reservations = new ConcurrentHashMap<>();

    @Transactional
    public String reserveInventory(List<OrderItem> items) {
        for (OrderItem item : items) {
            Inventory inventoryItem = inventoryRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));

            if (inventoryItem.getQuantity() < item.getQuantity()) {
                throw new RuntimeException("Insufficient stock for product: " + item.getProductId());
            }

            inventoryItem.setQuantity(inventoryItem.getQuantity() - item.getQuantity());
            inventoryRepository.save(inventoryItem);
        }

        String reservationId = UUID.randomUUID().toString();
        reservations.put(reservationId, items);
        return reservationId;
    }

    @Transactional
    public void releaseInventory(String reservationId) {
        List<OrderItem> itemsToRelease = reservations.remove(reservationId);
        if (itemsToRelease == null) {
            return; // Or throw an exception if reservation not found
        }

        for (OrderItem item : itemsToRelease) {
            Inventory inventoryItem = inventoryRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));
            inventoryItem.setQuantity(inventoryItem.getQuantity() + item.getQuantity());
            inventoryRepository.save(inventoryItem);
        }
    }
} 