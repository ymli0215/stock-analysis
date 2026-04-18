package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.OptionsOpen;
import com.stockapp.stockserver.entity.OptionsOpenId;

@Repository
public interface OptionsOpenRepository extends JpaRepository<OptionsOpen, OptionsOpenId> {
	
}

