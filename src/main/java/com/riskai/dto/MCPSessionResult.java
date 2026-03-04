package com.riskai.dto;

/**
 * Result returned when an AI agent joins a game via MCP.
 * <p>
 * Contains the player details plus a unique session token that the agent must
 * include in all subsequent tool calls to prove its identity. This prevents
 * one agent from impersonating another player.
 *
 * @param player       the player details (id, name, color, etc.)
 * @param sessionToken a unique token binding this agent to the player — include it in every tool call
 * @param hint         guidance on what to do next
 */
public record MCPSessionResult(
        PlayerDTO player,
        String sessionToken,
        String hint
) {}
