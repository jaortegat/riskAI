package com.risk.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a RiskAI game session.
 */
@Entity
@Table(name = "games")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GamePhase currentPhase;

    @Column(nullable = false)
    private int currentPlayerIndex;

    @Column(nullable = false)
    private int turnNumber;

    @Column(nullable = false)
    private int reinforcementsRemaining;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("turnOrder ASC")
    @Builder.Default
    private List<Player> players = new ArrayList<>();

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Territory> territories = new LinkedHashSet<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime endedAt;

    @Column
    private String winnerId;

    @Column(nullable = false)
    private String mapId;

    @Column(nullable = false)
    private int maxPlayers;

    @Column(nullable = false)
    private int minPlayers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GameMode gameMode = GameMode.CLASSIC;

    /** For DOMINATION mode: percentage of territories needed to win (e.g. 70) */
    @Column
    @Builder.Default
    private int dominationPercent = 70;

    /** For TURN_LIMIT mode: max turns before game ends */
    @Column
    @Builder.Default
    private int turnLimit = 20;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = GameStatus.WAITING_FOR_PLAYERS;
        }
        if (currentPhase == null) {
            currentPhase = GamePhase.SETUP;
        }
    }

    public Player getCurrentPlayer() {
        if (players.isEmpty()) return null;
        return players.get(currentPlayerIndex);
    }

    public void nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    public boolean canStart() {
        return players.size() >= minPlayers && status == GameStatus.WAITING_FOR_PLAYERS;
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }
}
