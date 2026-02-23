package com.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for joining an existing game.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinGameRequest {

    @NotBlank(message = "Player name is required")
    @Size(min = 2, max = 30, message = "Player name must be between 2 and 30 characters")
    private String playerName;
}
