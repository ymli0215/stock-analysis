package com.stockapp.stockserver.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Embeddable
public class StockFundamentalId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "stockId", length = 20, nullable = false)
    private String stockId;

    @Temporal(TemporalType.DATE)
    @Column(name = "fiscalDate", nullable = false)
    private Date fiscalDate;

    @Column(name = "period", length = 5, nullable = false)
    private String period;

    public StockFundamentalId() {}

    public StockFundamentalId(String stockId, Date fiscalDate, String period) {
        this.stockId = stockId;
        this.fiscalDate = fiscalDate;
        this.period = period;
    }

    public String getStockId()      { return stockId; }
    public void   setStockId(String stockId) { this.stockId = stockId; }

    public Date   getFiscalDate()   { return fiscalDate; }
    public void   setFiscalDate(Date fiscalDate) { this.fiscalDate = fiscalDate; }

    public String getPeriod()       { return period; }
    public void   setPeriod(String period) { this.period = period; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockFundamentalId)) return false;
        StockFundamentalId that = (StockFundamentalId) o;
        return Objects.equals(stockId, that.stockId)
            && Objects.equals(fiscalDate, that.fiscalDate)
            && Objects.equals(period, that.period);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stockId, fiscalDate, period);
    }
}
