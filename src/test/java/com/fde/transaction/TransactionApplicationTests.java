package com.fde.transaction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic context loading test — verifies the Spring application context starts.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionApplicationTests {

    @Test
    void contextLoads() {
        // If we get here, the context loaded successfully
    }
}
