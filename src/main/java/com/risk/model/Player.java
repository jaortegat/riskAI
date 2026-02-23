package com.risk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a player in the game (human or CPU).
 */
@Entity
@Table(name = "players")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlayerColor color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlayerType type;

    @Enumerated(EnumType.STRING)
    private CPUDifficulty cpuDifficulty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Game game;

    @Column(nullable = false)
    private int turnOrder;

    @Column(nullable = false)
    @Builder.Default
    private boolean eliminated = false;

    @Column(nullable = false)
    @Builder.Default
    private int cardsHeld = 0;

    @Column
    private String sessionId;

    @Column
    private LocalDateTime lastActiveAt;

    @OneToMany(mappedBy = "owner")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<Territory> territories = new ArrayList<>();

    public int getTerritoryCount() {
        return territories.size();
    }

    public int getTotalArmies() {
        return territories.stream()
                .mapToInt(Territory::getArmies)
                .sum();
    }

    public boolean isCPU() {
        return type == PlayerType.CPU;
    }

    public boolean isHuman() {
        return type == PlayerType.HUMAN;
    }

    public void eliminate() {
        this.eliminated = true;
    }
}
