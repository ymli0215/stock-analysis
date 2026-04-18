package com.stockapp.stockserver.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
@Data
@Entity
@Table(name = "stocks_raw_data")
public class StockRawData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private String chatId;

    @Column(name = "message_type", nullable = false)
    private String messageType; // TEXT, IMAGE, DOCUMENT

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "telegram_file_id")
    private String telegramFileId;

    @Column(name = "status")
    private Integer status = 0;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "batch_id")
    private String batchId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}