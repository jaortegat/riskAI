package com.risk.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

/**
 * Represents a continent containing multiple territories.
 */
@Entity
@Table(name = "continents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Continent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String continentKey;

    @Column(nullable = false)
    private int bonusArmies;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Game game;

    @OneToMany(mappedBy = "continent", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<Territory> territories = new ArrayList<>();

    @Column
    private String color;

    /**
     * Check if a player controls all territories in this continent.
     */
    public boolean isControlledBy(Player player) {
        if (territories.isEmpty()) return false;
        return territories.stream()
                .allMatch(t -> t.getOwner() != null && t.getOwner().getId().equals(player.getId()));
    }
}
