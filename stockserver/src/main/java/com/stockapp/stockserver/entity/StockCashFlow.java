package com.stockapp.stockserver.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 現金流量表
 */
@Entity
@Table(name = "StockCashFlow")
public class StockCashFlow extends BaseEntity {
	@EmbeddedId
	private StockFinanId id;

	//  應收帳款（增加）減少－CFO = 應收帳款(增加)減少
	@Column(name = "val1", precision=10, scale=2)
	private BigDecimal val1;

	//  存貨（增加）減少－CFO = 存貨(增加)減少
	@Column(name = "val2", precision=10, scale=2)
	private BigDecimal val2;

	//  應付帳款增加（減少）－CFO = 應付帳款增加(減少)
	@Column(name = "val3", precision=10, scale=2)
	private BigDecimal val3;

	//來自營運之現金流量 = 營業活動之淨現金流入(出)
	@Column(name = "val4", precision=10, scale=2)
	private BigDecimal val4;

	//投資活動之現金流量 = 投資活動之淨現金流入(出)
	@Column(name = "val5", precision=10, scale=2)
	private BigDecimal val5;

	//籌資活動之現金流量 = 融資活動之淨現金流入(出)
	@Column(name = "val6", precision=10, scale=2)
	private BigDecimal val6;

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

	public BigDecimal getVal4() {
		return val4;
	}

	public void setVal4(BigDecimal val4) {
		this.val4 = val4;
	}

	public BigDecimal getVal5() {
		return val5;
	}

	public void setVal5(BigDecimal val5) {
		this.val5 = val5;
	}

	public BigDecimal getVal6() {
		return val6;
	}

	public void setVal6(BigDecimal val6) {
		this.val6 = val6;
	}

}
