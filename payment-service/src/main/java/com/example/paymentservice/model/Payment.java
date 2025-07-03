package com.example.paymentservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue
    private Long id;

    @Column(unique = true)
    private String orderId;

    private BigDecimal amount;
    
    private String status; // e.g., "COMPLETED", "FAILED", "REFUNDED"

    private LocalDateTime timestamp;
} 