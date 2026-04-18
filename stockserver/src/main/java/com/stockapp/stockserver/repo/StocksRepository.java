package com.stockapp.stockserver.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.stockapp.stockserver.entity.Stocks;

public interface StocksRepository extends JpaRepository<Stocks, String> {
	@Query("SELECT s FROM Stocks s "
			+ "WHERE s.status = 1 ")
	public List<Stocks> findAll();
}

