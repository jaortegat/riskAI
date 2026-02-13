package com.risk.repository;

import com.risk.model.Continent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Continent entities.
 */
@Repository
public interface ContinentRepository extends JpaRepository<Continent, String> {

    List<Continent> findByGameId(String gameId);

    Optional<Continent> findByGameIdAndContinentKey(String gameId, String continentKey);

    @Query("SELECT c FROM Continent c LEFT JOIN FETCH c.territories WHERE c.game.id = :gameId")
    List<Continent> findByGameIdWithTerritories(String gameId);
}
