package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockDataId;
import com.stockapp.stockserver.entity.StockPriceLevel;

@Repository
public interface StockPriceLevelRepository extends JpaRepository<StockPriceLevel, StockDataId> {

}
