package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.SystemParam;

@Repository
public interface SystemParamRepository extends JpaRepository<SystemParam, String> {
	
}

