package com.stockapp.stockserver.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stockapp.stockserver.entity.Stockai;

public interface StockaiRepository extends JpaRepository<Stockai, Long> {
	@Query("SELECT s FROM Stockai s "
			+ "WHERE s.stockId = :stockId AND s.market = :market "
			+ "ORDER BY datetime DESC ")
	public List<Stockai> findByMarketAndId(@Param("market")String market, @Param("stockId")String stockId);
}

