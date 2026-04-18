package com.stockapp.stockserver.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.Webhook;
import com.stockapp.stockserver.entity.WebhookId;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, WebhookId> {

}
