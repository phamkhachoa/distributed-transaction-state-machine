package com.example.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSucceededEvent implements Serializable {
    private String orderId;
    private String customerId; // Assuming we can get this from the command
    private BigDecimal amount;
} 