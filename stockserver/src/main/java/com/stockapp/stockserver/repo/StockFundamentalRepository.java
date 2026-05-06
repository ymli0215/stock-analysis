package com.stockapp.stockserver.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stockapp.stockserver.entity.StockFundamental;
import com.stockapp.stockserver.entity.StockFundamentalId;

@Repository
public interface StockFundamentalRepository
        extends JpaRepository<StockFundamental, StockFundamentalId> {

    /** 最新估值快照（period='SNAP'，取最新一筆） */
    @Query("SELECT f FROM StockFundamental f "
         + "WHERE f.id.stockId = :stockId AND f.id.period = 'SNAP' "
         + "ORDER BY f.id.fiscalDate DESC")
    List<StockFundamental> findSnapshots(@Param("stockId") String stockId);

    default Optional<StockFundamental> findLatestSnapshot(String stockId) {
        List<StockFundamental> list = findSnapshots(stockId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 最近 8 季季報（period != 'SNAP'，由新到舊） */
    @Query("SELECT f FROM StockFundamental f "
         + "WHERE f.id.stockId = :stockId AND f.id.period != 'SNAP' "
         + "ORDER BY f.id.fiscalDate DESC")
    List<StockFundamental> findAllQuarters(@Param("stockId") String stockId);

    default List<StockFundamental> findRecentQuarters(String stockId) {
        List<StockFundamental> all = findAllQuarters(stockId);
        return all.size() > 8 ? all.subList(0, 8) : all;
    }
}
