package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.FuturesOpen;

@Repository
public interface FuturesOpenRepository extends JpaRepository<FuturesOpen, Long> {
	
}

