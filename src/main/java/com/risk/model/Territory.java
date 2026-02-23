package com.risk.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a territory on the RiskAI map.
 */
@Entity
@Table(name = "territories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Territory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String territoryKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Player owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "continent_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Continent continent;

    @Column(nullable = false)
    @Builder.Default
    private int armies = 0;

    @ElementCollection
    @CollectionTable(name = "territory_neighbors", joinColumns = @JoinColumn(name = "territory_id"))
    @Column(name = "neighbor_key")
    @Builder.Default
    private Set<String> neighborKeys = new HashSet<>();

    // Coordinates for map display
    @Column
    private double mapX;

    @Column
    private double mapY;

    public boolean isOwnedBy(Player player) {
        return owner != null && owner.getId().equals(player.getId());
    }

    public boolean canAttackFrom() {
        return armies > 1;
    }

    public boolean isNeighborOf(String territoryKey) {
        return neighborKeys.contains(territoryKey);
    }
}
