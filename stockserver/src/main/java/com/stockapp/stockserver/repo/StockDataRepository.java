package com.stockapp.stockserver.repo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockData;
import com.stockapp.stockserver.entity.StockDataId;

@Repository
public interface StockDataRepository extends JpaRepository<StockData, StockDataId> {
	/**
	 * 查詢StockData，資料由新到舊
	 * 
	 * @param stockId
	 * @param dataType
	 * @param limit
	 * @return
	 */
	@Query("SELECT s FROM StockData s "
			+ "WHERE s.id.stockId = :stockId "
			+ "AND s.id.dataType = :dataType "
			+ "ORDER BY s.id.dataTime DESC LIMIT :limit")
    List<StockData> findDataDesc(@Param("stockId") String stockId, @Param("dataType") String dataType, @Param("limit") int limit);
	
	/**
	 * 查詢StockData，資料由新到舊
	 * 
	 * @param stockId
	 * @param dataType
	 * @return
	 */
	@Query("SELECT s FROM StockData s "
			+ "WHERE s.id.stockId = :stockId "
			+ "AND s.id.dataType = :dataType "
			+ "ORDER BY s.id.dataTime DESC ")
    List<StockData> findDataDesc(@Param("stockId") String stockId, @Param("dataType") String dataType);
	
	/**
	 * 查詢指定日期區間內的StockData，資料由舊到新
	 * 
	 * @param stockId
	 * @param dataType
	 * @param startDate
	 * @param endDate
	 * @return
	 */
	@Query("SELECT s FROM StockData s "
			+ "WHERE s.id.stockId = :stockId "
			+ "AND s.id.dataType = :dataType "
			+ "AND s.id.dataTime BETWEEN :startDate AND :endDate "
			+ "ORDER BY s.id.dataTime ASC ")
    List<StockData> findDatasBetweenDate(@Param("stockId") String stockId, @Param("dataType") String dataType, @Param("startDate") Long startDate, @Param("endDate") Long endDate);

	@Query("SELECT s FROM StockData s WHERE EXTRACT(HOUR FROM s.dataDate) = 8")
    List<StockData> findByDataDateHourEight();
}

