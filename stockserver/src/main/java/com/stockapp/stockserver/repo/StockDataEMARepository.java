package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockDataEMA;
import com.stockapp.stockserver.entity.StockDataId;

@Repository
public interface StockDataEMARepository extends JpaRepository<StockDataEMA, StockDataId> {
	
}

