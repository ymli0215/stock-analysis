package com.stockapp.stockserver.entity;

import java.io.Serializable;

import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class BaseEntity implements Serializable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2498782066754674408L;
}
