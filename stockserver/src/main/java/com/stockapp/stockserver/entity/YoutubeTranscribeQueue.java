package com.stockapp.stockserver.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "youtube_transcribe_queue")
public class YoutubeTranscribeQueue extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "url", nullable = false, length = 500)
	private String url;

	@Column(name = "ai_status", nullable = false)
	private Integer aiStatus = 0;

	@Column(name = "ai_result", columnDefinition = "TEXT")
	private String aiResult;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getUrl() { return url; }
	public void setUrl(String url) { this.url = url; }

	public Integer getAiStatus() { return aiStatus; }
	public void setAiStatus(Integer aiStatus) { this.aiStatus = aiStatus; }

	public String getAiResult() { return aiResult; }
	public void setAiResult(String aiResult) { this.aiResult = aiResult; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
