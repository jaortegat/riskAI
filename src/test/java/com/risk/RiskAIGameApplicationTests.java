package com.risk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RiskAIGameApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void mainMethodRunsSuccessfully() {
        // Use a random port to avoid conflict with the @SpringBootTest context
        RiskAIGameApplication.main(new String[]{"--server.port=0"});
    }
}
