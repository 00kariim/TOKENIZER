package com.simulator.mdes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Entry point for the MDES TSP Simulator microservice.
 *
 * <p>This service simulates Mastercard's Digital Enablement Service (MDES),
 * acting as the Token Service Provider (TSP) in the EMVCo tokenization flow.
 * It is responsible for:
 * <ul>
 *   <li>Token provisioning (DPAN issuance) — {@code POST /api/v1/mdes/provisioning/tokenize}</li>
 *   <li>OTP-based token activation    — {@code POST /api/v1/mdes/provisioning/activate}</li>
 *   <li>Transaction authorisation      — {@code POST /api/v1/mdes/transaction/authorize}</li>
 *   <li>Token lifecycle management     — {@code PUT  /api/v1/mdes/token/{tokenValue}/lifecycle}</li>
 * </ul>
 *
 * <p>Upstream dependencies (Core Banking) are called via Spring Cloud OpenFeign.
 *
 * <p>Port: {@code 8081}
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.simulator.mdes.client")
public class MdesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MdesApplication.class, args);
    }
}
