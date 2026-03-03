package com.riskai.config;

import com.riskai.mcp.GameMCPService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that registers MCP tool providers.
 * <p>
 * Exposes all {@code @Tool}-annotated methods in {@link GameMCPService}
 * as MCP-callable tools via the Spring AI MCP server.
 */
@Configuration
public class MCPConfig {

    @Bean
    public ToolCallbackProvider gameTools(GameMCPService gameMCPService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(gameMCPService)
                .build();
    }
}
