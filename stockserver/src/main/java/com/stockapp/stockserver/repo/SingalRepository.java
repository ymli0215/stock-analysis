package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.Singal;

@Repository
public interface SingalRepository extends JpaRepository<Singal, String> {
	
}

