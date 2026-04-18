package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.MailTarget;

@Repository
public interface MailTargetRepository extends JpaRepository<MailTarget, String> {
	
}

