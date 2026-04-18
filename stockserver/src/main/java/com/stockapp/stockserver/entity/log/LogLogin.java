package com.stockapp.stockserver.entity.log;

import java.util.Date;

import com.stockapp.stockserver.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ExcludeSuperclassListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name="log_login")
@ExcludeSuperclassListeners
public class LogLogin extends BaseEntity implements Loggable  {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long serial;

	@Column(name = "user_id", length = 20)
	private String userId;
	
	@Column(name = "login_time")
	@Temporal(TemporalType.TIMESTAMP)
	private Date loginTime;
	
	@Column(name = "success")
	private boolean success;
	
	@Column(name = "address", length = 20)
	private String address;
	
	@Column(name = "ip")
	private boolean ip;

	public Long getSerial() {
		return serial;
	}

	public void setSerial(Long serial) {
		this.serial = serial;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public Date getLoginTime() {
		return loginTime;
	}

	public void setLoginTime(Date loginTime) {
		this.loginTime = loginTime;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public boolean isIp() {
		return ip;
	}

	public void setIp(boolean ip) {
		this.ip = ip;
	}
}
