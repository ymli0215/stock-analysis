package com.stockapp.stockserver.repo;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.stockapp.stockserver.entity.YoutubeTranscribeQueue;

public interface YoutubeTranscribeQueueRepository extends JpaRepository<YoutubeTranscribeQueue, Long> {

	List<YoutubeTranscribeQueue> findByAiStatusOrderByCreatedAtAsc(Integer aiStatus, Pageable pageable);
}
