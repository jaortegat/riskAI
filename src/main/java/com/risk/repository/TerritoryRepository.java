package com.risk.repository;

import com.risk.model.Territory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Territory entities.
 */
@Repository
public interface TerritoryRepository extends JpaRepository<Territory, String> {

    List<Territory> findByGameId(String gameId);

    Optional<Territory> findByGameIdAndTerritoryKey(String gameId, String territoryKey);

    List<Territory> findByOwnerId(String ownerId);

    @Query("SELECT t FROM Territory t WHERE t.game.id = :gameId AND t.owner IS NULL")
    List<Territory> findUnownedTerritoriesByGameId(String gameId);

    @Query("SELECT t FROM Territory t WHERE t.game.id = :gameId AND t.owner.id = :ownerId AND t.armies > 1")
    List<Territory> findAttackCapableTerritories(String gameId, String ownerId);
}
