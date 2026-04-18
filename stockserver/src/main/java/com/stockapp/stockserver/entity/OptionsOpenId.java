package com.stockapp.stockserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class OptionsOpenId extends BaseEntity {
	/**
	 * 買家類型
	 * 1:自營商
	 * 2:外資
	 * 3:五大月
	 * 4:十大月
	 * 5:五大所有
	 * 6:十大所有
	 * 7:五大特月
	 * 8:十大特月
	 * 9:五大特所有
	 * 10:十大特所有
	 * 11:投信
	 */
	@Column(name = "OwnerType", nullable = false)
	private Integer ownerType;

	//紀錄資料為data的time值(long)，皆以當天早上8:00為時間標準
	@Column(name = "dataTime", nullable = false)
	private Long dataTime;
	
	public OptionsOpenId() {
		
	}

	public Integer getOwnerType() {
		return ownerType;
	}

	public void setOwnerType(Integer ownerType) {
		this.ownerType = ownerType;
	}

	public Long getDataTime() {
		return dataTime;
	}

	public void setDataTime(Long dataTime) {
		this.dataTime = dataTime;
	}
}
