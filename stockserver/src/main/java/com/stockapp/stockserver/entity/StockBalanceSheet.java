package com.stockapp.stockserver.entity;

import java.math.BigDecimal;

import org.apache.commons.lang3.builder.ToStringBuilder;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 資產負債表
 */
@Entity
@Table(name = "StockBalanceSheet")
public class StockBalanceSheet extends BaseEntity {
	@EmbeddedId
	private StockFinanId id;

	// 應收帳款及票據 = 所有應收款項合計
	@Column(name = "val1", precision = 10, scale = 2)
	private BigDecimal val1;

	// 存貨 = 存貨
	@Column(name = "val2", precision = 10, scale = 2)
	private BigDecimal val2;

	// 應付帳款及票據 = 應付帳款+應付帳款–關係人
	@Column(name = "val3", precision = 10, scale = 2)
	private BigDecimal val3;

	public StockFinanId getId() {
		return id;
	}

	public void setId(StockFinanId id) {
		this.id = id;
	}

	public BigDecimal getVal1() {
		return val1;
	}

	public void setVal1(BigDecimal val1) {
		this.val1 = val1;
	}

	public BigDecimal getVal2() {
		return val2;
	}

	public void setVal2(BigDecimal val2) {
		this.val2 = val2;
	}

	public BigDecimal getVal3() {
		return val3;
	}

	public void setVal3(BigDecimal val3) {
		this.val3 = val3;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this);
	}
}
