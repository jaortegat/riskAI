package com.riskai.repository;

import com.riskai.model.Territory;
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

    @Query("SELECT DISTINCT t FROM Territory t LEFT JOIN FETCH t.neighborKeys WHERE t.game.id = :gameId")
    List<Territory> findByGameId(String gameId);

    @Query("SELECT t FROM Territory t LEFT JOIN FETCH t.neighborKeys WHERE t.game.id = :gameId AND t.territoryKey = :territoryKey")
    Optional<Territory> findByGameIdAndTerritoryKey(String gameId, String territoryKey);

    @Query("SELECT DISTINCT t FROM Territory t LEFT JOIN FETCH t.neighborKeys WHERE t.owner.id = :ownerId")
    List<Territory> findByOwnerId(String ownerId);

    @Query("SELECT DISTINCT t FROM Territory t LEFT JOIN FETCH t.neighborKeys WHERE t.game.id = :gameId AND t.owner IS NULL")
    List<Territory> findUnownedTerritoriesByGameId(String gameId);

    @Query("SELECT DISTINCT t FROM Territory t LEFT JOIN FETCH t.neighborKeys WHERE t.game.id = :gameId AND t.owner.id = :ownerId AND t.armies > 1")
    List<Territory> findAttackCapableTerritories(String gameId, String ownerId);
}

