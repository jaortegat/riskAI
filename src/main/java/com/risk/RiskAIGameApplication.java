package com.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the RiskAI Board Game application.
 * 
 * Features:
 * - Multiplayer support via WebSockets
 * - CPU players with different strategies
 * - Real-time game updates
 * - Persistent game state
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class RiskAIGameApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskAIGameApplication.class, args);
    }
}
