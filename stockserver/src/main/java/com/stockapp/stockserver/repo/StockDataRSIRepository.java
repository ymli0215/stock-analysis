package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockDataId;
import com.stockapp.stockserver.entity.StockDataRSI;

@Repository
public interface StockDataRSIRepository extends JpaRepository<StockDataRSI, StockDataId> {

}
