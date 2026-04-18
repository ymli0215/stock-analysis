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
@Table(name = "log_action")	
@ExcludeSuperclassListeners
public class LogAction extends BaseEntity implements Loggable {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long serial;

	@Column(name = "user_id", length = 20)
	private String userId;

	@Column(name = "action_name", length = 50)
	private String actionName;

	@Column(name = "execute_time")
	@Temporal(TemporalType.TIMESTAMP)
	private Date executeTime;

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

	public String getActionName() {
		return actionName;
	}

	public void setActionName(String actionName) {
		this.actionName = actionName;
	}

	public Date getExecuteTime() {
		return executeTime;
	}

	public void setExecuteTime(Date executeTime) {
		this.executeTime = executeTime;
	}

}
