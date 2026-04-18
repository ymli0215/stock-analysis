package com.stockapp.stockserver.repo;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.BizDate;

@Repository
public interface BizDateRepository extends JpaRepository<BizDate, LocalDate> {
	
}

