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
@Table(name = "log_audit")
@ExcludeSuperclassListeners
public class LogAudit extends BaseEntity implements Loggable {
	public static final String TYPE_INSERT = "I";
	public static final String TYPE_UPDATE = "U";
	public static final String TYPE_DELETE = "D";
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long serial;

	@Column(name = "user_id", length = 20)
	private String userId;
	
	@Column(name = "type", length = 1)
	private String type;

	@Column(name = "execute_time")
	@Temporal(TemporalType.TIMESTAMP)
	private Date executeTime;

	@Column(name = "table_name", length = 100)
	private String tableName;

	@Column(name = "old_value", length = 4096)
	private String oldValue;

	@Column(name = "new_value", length = 4096)
	private String newValue;

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

	public Date getExecuteTime() {
		return executeTime;
	}

	public void setExecuteTime(Date executeTime) {
		this.executeTime = executeTime;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public String getOldValue() {
		return oldValue;
	}

	public void setOldValue(String beforeValue) {
		this.oldValue = beforeValue;
	}

	public String getNewValue() {
		return newValue;
	}

	public void setNewValue(String afterValue) {
		this.newValue = afterValue;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}
