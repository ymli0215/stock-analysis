package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockFutures;

@Repository
public interface StockFuturesRepository extends JpaRepository<StockFutures, String> {
	
}

