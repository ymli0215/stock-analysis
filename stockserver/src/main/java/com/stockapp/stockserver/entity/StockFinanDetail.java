package com.stockapp.stockserver.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 損益表
 */
@Entity
@Table(name = "StockFinanDetail")
public class StockFinanDetail extends BaseEntity {
	@EmbeddedId
	private StockFinanId id;

	//  營業利益
	@Column(name = "val1", precision=10, scale=2)
	private BigDecimal val1;

	//  業外收入合計
	@Column(name = "val2", precision=10, scale=2)
	private BigDecimal val2;

	//  業外支出合計
	@Column(name = "val3", precision=10, scale=2)
	private BigDecimal val3;

	//  業外損益合計
	@Column(name = "val4", precision=10, scale=2)
	private BigDecimal val4;

	//  稅前淨利
	@Column(name = "val5", precision=10, scale=2)
	private BigDecimal val5;

	//  繼續營業單位稅後損益
	@Column(name = "val6", precision=10, scale=2)
	private BigDecimal val6;

	//  繼續營業單位稅後損益
	@Column(name = "val7", precision=10, scale=2)
	private BigDecimal val7;

	//  合併稅後淨利
	@Column(name = "val8", precision=10, scale=2)
	private BigDecimal val8;

	//  歸屬於共同控制下前手權益之淨利
	@Column(name = "val9", precision=10, scale=2)
	private BigDecimal val9;

	//  歸屬於非控制權益之淨利
	@Column(name = "val10", precision=10, scale=2)
	private BigDecimal val10;

	//  稅後淨利
	@Column(name = "val11", precision=10, scale=2)
	private BigDecimal val11;

	//  每股稅後盈餘(元)
	@Column(name = "val12", precision=10, scale=2)
	private BigDecimal val12;

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

	public BigDecimal getVal7() {
		return val7;
	}

	public void setVal7(BigDecimal val7) {
		this.val7 = val7;
	}

	public BigDecimal getVal8() {
		return val8;
	}

	public void setVal8(BigDecimal val8) {
		this.val8 = val8;
	}

	public BigDecimal getVal9() {
		return val9;
	}

	public void setVal9(BigDecimal val9) {
		this.val9 = val9;
	}

	public BigDecimal getVal10() {
		return val10;
	}

	public void setVal10(BigDecimal val10) {
		this.val10 = val10;
	}

	public BigDecimal getVal11() {
		return val11;
	}

	public void setVal11(BigDecimal val11) {
		this.val11 = val11;
	}

	public BigDecimal getVal12() {
		return val12;
	}

	public void setVal12(BigDecimal val12) {
		this.val12 = val12;
	}
}
