package com.stockapp.stockserver.entity;

import java.math.BigDecimal;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "stock_fundamental")
public class StockFundamental extends BaseEntity {

    @EmbeddedId
    private StockFundamentalId id;

    @Column(name = "market", length = 5, nullable = false)
    private String market;

    @Column(name = "revenue", precision = 20, scale = 2)
    private BigDecimal revenue;

    @Column(name = "netIncome", precision = 20, scale = 2)
    private BigDecimal netIncome;

    @Column(name = "eps", precision = 10, scale = 4)
    private BigDecimal eps;

    @Column(name = "totalAssets", precision = 20, scale = 2)
    private BigDecimal totalAssets;

    @Column(name = "totalDebt", precision = 20, scale = 2)
    private BigDecimal totalDebt;

    @Column(name = "pe", precision = 10, scale = 4)
    private BigDecimal pe;

    @Column(name = "pb", precision = 10, scale = 4)
    private BigDecimal pb;

    @Column(name = "roe", precision = 10, scale = 4)
    private BigDecimal roe;

    @Column(name = "divYield", precision = 10, scale = 4)
    private BigDecimal divYield;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updatedAt", insertable = false, updatable = false)
    private Date updatedAt;

    // ── getters / setters ──────────────────────────────────────────────────────

    public StockFundamentalId getId()            { return id; }
    public void setId(StockFundamentalId id)     { this.id = id; }

    public String     getMarket()                { return market; }
    public void       setMarket(String market)   { this.market = market; }

    public BigDecimal getRevenue()               { return revenue; }
    public void       setRevenue(BigDecimal v)   { this.revenue = v; }

    public BigDecimal getNetIncome()             { return netIncome; }
    public void       setNetIncome(BigDecimal v) { this.netIncome = v; }

    public BigDecimal getEps()                   { return eps; }
    public void       setEps(BigDecimal v)       { this.eps = v; }

    public BigDecimal getTotalAssets()           { return totalAssets; }
    public void       setTotalAssets(BigDecimal v){ this.totalAssets = v; }

    public BigDecimal getTotalDebt()             { return totalDebt; }
    public void       setTotalDebt(BigDecimal v) { this.totalDebt = v; }

    public BigDecimal getPe()                    { return pe; }
    public void       setPe(BigDecimal v)        { this.pe = v; }

    public BigDecimal getPb()                    { return pb; }
    public void       setPb(BigDecimal v)        { this.pb = v; }

    public BigDecimal getRoe()                   { return roe; }
    public void       setRoe(BigDecimal v)       { this.roe = v; }

    public BigDecimal getDivYield()              { return divYield; }
    public void       setDivYield(BigDecimal v)  { this.divYield = v; }

    public Date       getUpdatedAt()             { return updatedAt; }
    public void       setUpdatedAt(Date v)       { this.updatedAt = v; }
}
