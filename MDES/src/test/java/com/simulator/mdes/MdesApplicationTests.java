package com.simulator.mdes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies the Spring application context loads successfully.
 *
 * <p>Uses the {@code test} profile which provides an in-memory H2 database
 * and dummy secret values so no real infrastructure is needed.
 */
@SpringBootTest
@ActiveProfiles("test")
class MdesApplicationTests {

    @Test
    void contextLoads() {
        // Passes if the application context starts without errors.
    }
}
