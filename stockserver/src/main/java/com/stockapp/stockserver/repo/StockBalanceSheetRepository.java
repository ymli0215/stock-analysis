package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockBalanceSheet;
import com.stockapp.stockserver.entity.StockFinanId;

@Repository
public interface StockBalanceSheetRepository extends JpaRepository<StockBalanceSheet, StockFinanId> {
	
}

