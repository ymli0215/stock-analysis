package com.stockapp.stockserver.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.stockapp.stockserver.entity.BizDate;
import com.stockapp.stockserver.enums.BizDateTypeType;
import com.stockapp.stockserver.repo.BizDateRepository;

@Service
public class BizDateService {
	@Autowired
	BizDateRepository bizDateRepository;
	
	@Cacheable(value="bizDateFindCache", key="#date")
	public BizDate findBizDate(LocalDate date) {
		Optional<BizDate> bizDate = bizDateRepository.findById(date);
		return bizDate.isPresent()?bizDate.get():null;
	}
	
	public boolean isBizDate(LocalDate date) {
		boolean result = true;
		try {
			BizDate bizDate = findBizDate(date);
			//空的有可能是因為不需要
			if(bizDate != null) {
				//判斷六日是否要上班
				if(date.getDayOfWeek() == DayOfWeek.SUNDAY ||
						date.getDayOfWeek() == DayOfWeek.SATURDAY) {
					if(bizDate.getDayType().intValue() == BizDateTypeType.WORKDAY.getCode()) {
						result = true;
					}
				}
				else {
					if(bizDate.getDayType().intValue() == BizDateTypeType.HOLIDAY.getCode()) {
						result = false;
					}
				}
			}
			else {
				//沒有資料就單純判斷星期六日
				if(date.getDayOfWeek() == DayOfWeek.SUNDAY ||
						date.getDayOfWeek() == DayOfWeek.SATURDAY) {
					result = false;
				}
				else {
					result = true;
				}
			}
		}
		catch(Exception e) {
			
		}
		
		return result;
	}
}
