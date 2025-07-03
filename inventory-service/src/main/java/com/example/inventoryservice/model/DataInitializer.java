package com.example.inventoryservice.model;

import com.example.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final InventoryRepository inventoryRepository;

    @Override
    public void run(String... args) throws Exception {
        inventoryRepository.save(Inventory.builder().productId("1").quantity(100).build());
        inventoryRepository.save(Inventory.builder().productId("2").quantity(100).build());
        inventoryRepository.save(Inventory.builder().productId("3").quantity(100).build());
    }
} 