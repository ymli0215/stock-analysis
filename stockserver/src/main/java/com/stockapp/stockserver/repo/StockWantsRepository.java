package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockWants;

import jakarta.transaction.Transactional;

@Repository
public interface StockWantsRepository extends JpaRepository<StockWants, String> {
	@Modifying
    @Transactional
    @Query(value = "TRUNCATE TABLE warrants", nativeQuery = true)
    void truncateTable();
}

