package com.stockapp.stockserver.entity;

import java.math.BigDecimal;

import org.apache.commons.lang3.builder.ToStringBuilder;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 股息股利
 */
@Entity
@Table(name = "StockDividend")
public class StockDividend extends BaseEntity {
	@EmbeddedId
	private StockFinanId id;

	//現金
	@Column(name = "value", precision = 10, scale = 2, nullable=true)
	private BigDecimal value;

	//股票
	@Column(name = "value2", precision = 10, scale = 2, nullable=true)
	private BigDecimal value2;

	public StockFinanId getId() {
		return id;
	}

	public void setId(StockFinanId id) {
		this.id = id;
	}

	public BigDecimal getValue() {
		return value;
	}

	public void setValue(BigDecimal value) {
		this.value = value;
	}

	public BigDecimal getValue2() {
		return value2;
	}

	public void setValue2(BigDecimal value2) {
		this.value2 = value2;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this);
	}
}
