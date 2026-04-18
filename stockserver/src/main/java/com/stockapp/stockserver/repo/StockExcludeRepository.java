package com.stockapp.stockserver.repo;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockExclude;
import com.stockapp.stockserver.entity.StockExcludeId;

@Repository
public interface StockExcludeRepository extends JpaRepository<StockExclude, StockExcludeId> {
	/**
	 * 查詢StockExclude，資料由舊到新
	 * 
	 * @param stockId
	 * @param afterDate
	 * @return
	 */
	@Query("SELECT s FROM StockExclude s "
			+ "WHERE s.id.stockId = :stockId "
			+ "AND s.excludeDate <= :afterDate "
			+ "ORDER BY s.excludeDate ASC ")
    List<StockExclude> findExcludeDatas(@Param("stockId") String stockId, @Param("afterDate") Date afterDate);
	
}

