package com.simulator.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Core Banking Simulator microservice.
 *
 * <p>This service simulates the Card Issuer (bank) in the EMVCo tokenization flow.
 * It is responsible for:
 * <ul>
 *   <li>Card identity verification (ID&V) and OTP delivery</li>
 *   <li>Token activation confirmation</li>
 *   <li>Payment authorisation — balance check and ledger debit</li>
 *   <li>Account and card management</li>
 * </ul>
 *
 * <p>All endpoints are exclusively accessible from the MDES TSP Simulator service
 * via internal JWT authentication. External callers are rejected at the security layer.
 *
 * <p>Port: {@code 8082} — NOT published to the host in production mode.
 */
@SpringBootApplication
public class BankApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankApplication.class, args);
    }
}
