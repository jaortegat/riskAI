package com.risk.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.ArrayList;
import java.util.List;

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
