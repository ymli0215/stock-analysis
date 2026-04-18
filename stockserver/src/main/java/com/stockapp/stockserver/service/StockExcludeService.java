package com.stockapp.stockserver.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import com.stockapp.stockserver.entity.StockData;
import com.stockapp.stockserver.entity.StockExclude;
import com.stockapp.stockserver.entity.StockExcludeId;
import com.stockapp.stockserver.entity.Stocks;
import com.stockapp.stockserver.enums.StockDataTypeType;
import com.stockapp.stockserver.repo.StockDataRepository;
import com.stockapp.stockserver.repo.StockExcludeRepository;
import com.stockapp.stockserver.repo.StocksRepository;
import com.stockapp.stockserver.utils.DateUtils;

@Service
@EnableAsync
public class StockExcludeService extends AbstractService {
	// 初始化 SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(StockExcludeService.class);

    /** 上市股票除權息資料 除息英文是EXCLUDE DIVIDEND，簡稱XD。除權英文是EXCLUDE RIGHT，簡稱XR。如果又除息又除權，則英文簡稱為DR*/
	public static final String stockExcludeUrl = "https://www.twse.com.tw/exchangeReport/TWT48U?response=csv";
	
	/** 上櫃除權息資料*/
	public static final String stockExcludeUrl2 = "https://www.tpex.org.tw/web/stock/exright/preAnnounce/PrePost_download.php?l=zh-tw&s=";

	@Autowired
	StocksRepository stocksRepository;

	@Autowired
	StockDataRepository stockDataRepository;
	
	@Autowired
	StockExcludeRepository stockExcludeRepository;
	
    public List<StockExclude> queryExcludeData(@RequestParam(value="si",required=false)String stockId) {
		try {
			//先更新
			updateStockExcludde();

			List<StockExclude> ses = stockExcludeRepository.findExcludeDatas(stockId, DateUtils.truncateTime(new Date()));
			for(StockExclude se : ses) {
				Optional<Stocks> optionalStocks = stocksRepository.findById(se.getId().getStockId());
				if(optionalStocks.isPresent()) {
					se.setFutures(optionalStocks.get().getFutures());
					se.setWants(optionalStocks.get().getWants());
				}
			}
			return ses;
		}
		catch(Exception e) {
			logger.error("{}", e);
		}
		
		return null;
	}
    
    public void updateStockExcludde() {
		update1();
		update2();
	}
	
    private void update1() {
		try {
			SSLContext ctx = SSLContext.getInstance("TLSv1.2");
	        ctx.init(new KeyManager[0], new TrustManager[] {new DefaultTrustManager()}, new SecureRandom());
	        SSLContext.setDefault(ctx);
	        
			//先設定測試資料，該url為測試url
			String urlStr = stockExcludeUrl;
			
			logger.debug("start updateStockExclude " + stockExcludeUrl);
			

			URL url = new URL(urlStr);
	        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
	        conn.setHostnameVerifier(new HostnameVerifier() {
	            @Override
	            public boolean verify(String arg0, SSLSession arg1) {
	                return true;
	            }
	        });
	        if (conn.getResponseCode() == 200) {
				List<StockExclude> ses = new ArrayList<StockExclude>();
				String responseString = getContent(conn.getInputStream());
				int year = new Date().getYear();
				String[] datas = responseString.split("\n");
				for(int i=2;i<datas.length;i++) {
					//除權除息日期	股票代號	名稱	除權息	無償配股率	現金增資配股率	現金增資認購價	現金股利	詳細資料	參考價試算	最近一次申報資料季別/日期	每股(單位)淨值	每股(單位)盈餘
					String[] contents = datas[i].split(",");
					if(contents.length >= 13) {
//						System.out.println("do update excludde " + contents[0].replaceAll("\"", "") + "-" +
//								contents[1].replaceAll("\"", "") + "-" + contents[4].replaceAll("\"", "") + "-" +
//								contents[7].replaceAll("\"", ""));
						//除權息日
						Date excludeDate = DateUtils.parseRocDate(
								contents[0].replaceAll("年", "/").replaceAll("月", "/").replaceAll("日", "").replaceAll("\"", ""), DateUtils.defaultRocPatten);
						
						String stockId = contents[1].replaceAll("\"", "").replaceAll("=", "");
						
						Double excRight = 0D;
						try {
							excRight = Double.parseDouble(contents[4].replaceAll("\"", ""));
						}
						catch(Exception e) {}
						Double excDiv = 0D;
						try {
							excDiv = Double.parseDouble(contents[7].replaceAll("\"", ""));
						}
						catch(Exception e) {}
						
						StockExcludeId id = new StockExcludeId();
						id.setStockId(stockId);
						id.setYear(year);
						
						StockExclude se = null;
						
						StockExcludeId stockExcludeId = new StockExcludeId();
						stockExcludeId.setStockId(stockId);
						stockExcludeId.setYear(year);
						Optional<StockExclude> optionalSe = stockExcludeRepository.findById(stockExcludeId);
						if(optionalSe.isEmpty()) {
							se = new StockExclude();
						}
						se.setId(id);
						se.setExcludeDate(excludeDate);
						//除息值
						se.setExcludeDividend(excDiv);
						//除權值
						se.setExcludeRight(excRight);
						
						//找出目前現股資料
						Optional<Stocks> optionalStocks = stocksRepository.findById(se.getId().getStockId());
						if(optionalStocks.isEmpty()) {
							logger.debug("股票 " + se.getId().getStockId() + " 不存在");
							continue;
						}
						else {
							se.setStockName(optionalStocks.get().getStockName().trim());
						}

						List<StockData> stockDatas = stockDataRepository.findDataDesc(stockId, StockDataTypeType.DAYILY.getCode(), 1);
						
						if(!stockDatas.isEmpty()) {
							StockData data = stockDatas.get(0);

							se.setPrice(data.getClose());
								
							//現金殖利率
							Double value1 = Math.round(excDiv / data.getClose() * 10000) / 100D;
							se.setCashYields(value1);
							
							//殖利率
							value1 = Math.round((excDiv + excRight) / data.getClose() * 10000) / 100D;
							se.setYields(value1);

							//除權後價格
							value1 = (data.getClose() - excDiv) / (1 + excRight);
							se.setAfterExPrice(value1);
							
							//除權趴數
							se.setExcludeRightRate(excRight * 10);
							
							//除息趴數
							value1 = Math.round(excDiv / data.getClose() * 10000) / 100D;
							se.setExcludeDividendRate(value1);
							
							//除權息趴數
							value1 = se.getExcludeDividend() + se.getExcludeRight();
							se.setRdRate(value1);
							
							//權息黃金比例
							if(se.getExcludeDividendRate() != null && se.getExcludeDividendRate().doubleValue() != 0D) {
								value1 = Math.round(excDiv / data.getClose() * 10000) / 100D;
								se.setRdGoldRate(value1);
							}
							else {
								se.setRdGoldRate(0D);
							}
						}
						
						stockExcludeRepository.save(se);
					}
				}
			}
		}
		catch(Exception e) {
			logger.error("{}", e);
		}
	}
	
    private void update2() {
		try {SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(new KeyManager[0], new TrustManager[] {new DefaultTrustManager()}, new SecureRandom());
        SSLContext.setDefault(ctx);
			//先設定測試資料，該url為測試url
			String urlStr = stockExcludeUrl2;
			
			logger.debug("start updateStockExclude " + stockExcludeUrl2);
			
			URL url = new URL(urlStr);
	        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
	        conn.setHostnameVerifier(new HostnameVerifier() {
	            @Override
	            public boolean verify(String arg0, SSLSession arg1) {
	                return true;
	            }
	        });
	        if (conn.getResponseCode() == 200) {
				List<StockExclude> ses = new ArrayList<StockExclude>();
				String responseString = getContent(conn.getInputStream());

				int year = new Date().getYear();
				String[] datas = responseString.split("\n");
				for(int i=2;i<datas.length;i++) {
					//除權息日期	 股票代號	 名稱	 除權息	無償配股率	 現金增資配股率 	現金增資認購價	 現金股利	 公開承銷股數	  員工認購股數	  原股東認購股數	  按持股比例仟股認購
					String[] contents = datas[i].split(",");
					if(contents.length >= 11) {
						//除權息日
						Date excludeDate = DateUtils.parseRocDate(
								contents[0].replaceAll("年", "/").replaceAll("月", "/").replaceAll("日", "").replaceAll("\"", ""), DateUtils.defaultRocPatten);
						
						String stockId = contents[1].replaceAll("\"", "").replaceAll("=", "");
						
						Double excRight = 0D;
						try {
							excRight = Double.parseDouble(contents[4].replaceAll("\"", ""));
						}
						catch(Exception e) {}
						Double excDiv = 0D;
						try {
							excDiv = Double.parseDouble(contents[7].replaceAll("\"", ""));
						}
						catch(Exception e) {}
						
						StockExcludeId id = new StockExcludeId();
						id.setStockId(stockId);
						id.setYear(year);
						
						StockExclude se = null;
						
						StockExcludeId stockExcludeId = new StockExcludeId();
						stockExcludeId.setStockId(stockId);
						stockExcludeId.setYear(year);
						Optional<StockExclude> optionalSe = stockExcludeRepository.findById(stockExcludeId);
						if(optionalSe.isEmpty()) {
							se = new StockExclude();
						}
						se.setId(id);
						se.setExcludeDate(excludeDate);
						//除息值
						se.setExcludeDividend(excDiv);
						//除權值
						se.setExcludeRight(excRight);
						
						//找出目前現股資料
						Optional<Stocks> optionalStocks = stocksRepository.findById(se.getId().getStockId());
						if(optionalStocks.isEmpty()) {
							logger.debug("股票 " + se.getId().getStockId() + " 不存在");
							continue;
						}
						else {
							se.setStockName(optionalStocks.get().getStockName().trim());
						}

						List<StockData> stockDatas = stockDataRepository.findDataDesc(stockId, StockDataTypeType.DAYILY.getCode(), 1);
						
						if(!stockDatas.isEmpty()) {
							StockData data = stockDatas.get(0);
							se.setPrice(data.getClose());
								
							//現金殖利率
							Double value1 = Math.round(excDiv / data.getClose() * 10000) / 100D;
							se.setCashYields(value1);
							
							//殖利率
							value1 = Math.round((excDiv + excRight) / data.getClose() * 10000) / 100D;
							se.setYields(value1);

							//除權後價格
							value1 = (data.getClose() - excDiv) / (1 + excRight);
							se.setAfterExPrice(value1);
							
							//除權趴數
							se.setExcludeRightRate(excRight * 10);
							
							//除息趴數
							value1 = Math.round(excDiv / data.getClose() * 10000) / 100D;
							se.setExcludeDividendRate(value1);
							
							//除權息趴數
							value1 = se.getExcludeDividend() + se.getExcludeRight();
							se.setRdRate(value1);
							
							//權息黃金比例
							if(se.getExcludeDividendRate() != null && se.getExcludeDividendRate().doubleValue() != 0D) {
								value1 = Math.round(excDiv / data.getClose() * 10000) / 100D;
								se.setRdGoldRate(value1);
							}
							else {
								se.setRdGoldRate(0D);
							}
						}
						
						stockExcludeRepository.save(se);
					}
				}
			}
		}
		catch(Exception e) {
			logger.error("{}", e);
		}
	}
    
    public static class DefaultTrustManager implements X509TrustManager {

		@Override
		public void checkClientTrusted(X509Certificate[] arg0, String arg1)
				throws CertificateException {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] arg0, String arg1)
				throws CertificateException {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
	}
    
    public String getContent(InputStream is) {
		String result = "";
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is,
					"utf-8"));
			String input;
			while ((input = br.readLine()) != null) {
				result += input;
			}
			br.close();
		} catch (Exception e) {
			logger.error("fail : ", e);
		}

		return result;
	}
}
