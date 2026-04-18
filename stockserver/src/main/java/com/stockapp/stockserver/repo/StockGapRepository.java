package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockGap;
import com.stockapp.stockserver.entity.StockGapId;

@Repository
public interface StockGapRepository extends JpaRepository<StockGap, StockGapId> {
	
}

