package com.stockapp.stockserver.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockapp.stockserver.entity.StockPageRaw;

public interface StockPageRawRepository extends JpaRepository<StockPageRaw, Long> {

	List<StockPageRaw> findByAiStatusOrderByCreatedAtAsc(Integer aiStatus);
}
