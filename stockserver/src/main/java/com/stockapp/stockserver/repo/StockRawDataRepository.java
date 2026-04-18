package com.stockapp.stockserver.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockRawData;

@Repository
public interface StockRawDataRepository extends JpaRepository<StockRawData, Long> {

    /**
     * 找出所有特定狀態的資料 (例如 n8n 要撈出 status=0 待處理的資料)
     */
    List<StockRawData> findByStatus(Integer status);

    /**
     * (選用) 根據 chat_id 找出最新的一筆資料
     * 這可以用來檢查上一筆資料是否在 4 分鐘滑動窗口內
     */
    StockRawData findTopByChatIdOrderByCreatedAtDesc(String chatId);
}