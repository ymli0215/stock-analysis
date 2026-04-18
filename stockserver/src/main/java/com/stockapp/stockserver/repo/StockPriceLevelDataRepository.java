package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockPriceLevelData;

@Repository
public interface StockPriceLevelDataRepository extends JpaRepository<StockPriceLevelData, Long> {
	
}

