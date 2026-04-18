package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockBuySell;
import com.stockapp.stockserver.entity.StockBuySellId;

@Repository
public interface StockBuySellRepository extends JpaRepository<StockBuySell, StockBuySellId> {
	
}

