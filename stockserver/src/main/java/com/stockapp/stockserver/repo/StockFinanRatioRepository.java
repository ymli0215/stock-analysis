package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockFinanId;
import com.stockapp.stockserver.entity.StockFinanRatio;

@Repository
public interface StockFinanRatioRepository extends JpaRepository<StockFinanRatio, StockFinanId> {
	
}

