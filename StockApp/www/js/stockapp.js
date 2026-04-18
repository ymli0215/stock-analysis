/**
 * 建立可過濾的股票下拉
 */
var stockIds = new Array();
function initIds(id) {
	var url = 'https://stock.bignoodle.net/StockServer/stock/findStocks?callback=call';

	return $.ajax({
        url: url,
		dataType: "jsonp",
		jsonpCallback: "call",
        success: function (data) {
        	for(var i=0;i<data.length;i++) {
        		$("#"+id).append('<option data-subtext="' + data[i].stockName + '">' + data[i].stockId + '</option>');
        	}
        	
        	$('#' + id).selectpicker();
        },
		error: function(jqXHR, textStatus, errorThrown) {
		    console.error('JSONP 請求失敗:', textStatus, errorThrown);
		},
        complete: function (request,textStatus) {
        	
        }
    });
}

function resetMenu() {
	var menu = '<a href="future-bbi.html">多空趨勢</a>'+
							'<a href="stock-data.html">k線資料</a>'+
						    '<a href="stock-gate.html">多空戰K</a>'+
						    '<a href="stock-turn.html">多空中軸</a>' +
                            //'<a href="stock-gate2.html">簡易多空轉折查詢</a>'
                            //'<a href="options-open.html">外資選擇權均價資料</a>'+
                            //'<a href="future-open.html">外資特定人未沖銷資料</a>'+
                            //'<a href="options-open2.html">五大十大未沖銷資料</a>'+
                            '<a href="stock-exclude.html">除權息查詢</a>'+
                            //'<a href="find-stocks-long.html">季線多空查詢</a>'+
                            //'<a href="find-stocks-rsi.html">RSI多空查詢</a>'+
                            //'<a href="stock-buysell2.html">買賣點</a>'+
                            //'<a href="stock-oldwang.html">老王多空</a>'+
                            //'<a href="find-stocks-gap.html">跳空</a>'
                            //'<a href="stock-buysell3.html">多排、空排、金叉、死叉</a>'
                            '';

	$("#side-menu").find("li").html(menu);
}

/**
 * 比對兩個傳入價格，讓前者顏色進行變化
 */
function printColor(price, closePrice) {
	if(price > closePrice) {
		return "<font color=red>"+price+"</font>";
	}
	else if(price < closePrice) {
		return "<font color=green>"+price+"</font>";
	}
	
	return price;
}

/** ******* start ajax loading ********** */
$(document).ajaxStart($.blockUI).ajaxStop($.unblockUI);
/** ******* end ajax loading *********** */

/**
 * 建立highchart
 */


var colors = {'3': '#FF8000',
                      '5': '#FF8000',
                      '7': '#FFFFFF',
                      '8': '#FFFFFF',
                      '10': '#EA20EA',
                      '13': '#0000FF',
                      '21': '#FFFF00',
                      '34': '#FF8000',
                      '53': '#00FF00',
                      '55': '#00FF00',
                      '89': '#FF8000',
                      '144': '#FF0080',
                      '233': '#8000FF'};


function crateHighchart(dateType, containerId, title, serialData) {
	if(containerId == "") {
		alert("無法產生圖，找不到框架");
		return ;
	}
	
	$("#" + containerId).highcharts('StockChart', {
    	chart: {
    		reflow: true,
            height: 500
        },
        tooltip: {
        	xDateFormat:'%Y-%m-%d',
        	shared:true,//true才可以抓到同組資料
        	//crosshairs: true,
        	/*crosshairs: [{
                width: 1,
                color: '#C85A17',
                dashStyle: 'LongDash'
            }, {
                width: 1,
                color: '#C85A17',
                dashStyle: 'LongDash'
            }],
            */
        	formatter: function () {
            	if(!this.points) {
            		
            		return false;
            	}
            	//var day = this.series[0].data[maxLength].x;
            	var digital = 2;
            	$("#" + dateType + "data td").remove();
            	$("#" + dateType + "date").text(Highcharts.dateFormat('%Y/%m/%d', this.x));
            	$("#" + dateType + "open").text(this.points[0].point.open);
                $("#" + dateType + "high").text(this.points[0].point.high);
                $("#" + dateType + "low").text(this.points[0].point.low);
                $("#" + dateType + "close").text(this.points[0].point.close);
                for(var i=1;i<this.points.length;i++) {
                	var td = '<td width="90px"><nobr>' + this.points[i].series.name + '：<b style="color:'  + this.points[i].series.color + '">' + Highcharts.numberFormat(this.points[i].y, digital) + '</b>&nbsp;&nbsp;</nobr></td>';
                	if(this.points[i].series.name == 'close') {
                		td = "";
                	}
                	else if(this.points[i].series.name == 'Volume'){
                		$("#" + dateType + "vol").text(Highcharts.numberFormat(this.points[i].y, digital));
                		td = "";
                	}
                	$("#" + dateType + "data").append(td);
                }
                
                
                return false;
            }
        },
        colors: colors,
        plotOptions: {
            candlestick: {
                color: '#005500',
                lineColor: '#005500',
                upColor: '#D52B00',
                upLineColor: '#D52B00'
            },
            column: {
                color: '#FF9900'
            },
            line: {
                lineWidth: 1,
                marker: { states: { hover: { enabled: false }, select: { enabled: false } } }
            },
            dataLabels: {
                enabled: false
            }
        },
        rangeSelector : {
            // selected : 1
        	enabled: true,
        	inputEnabled:false,
        	buttonPosition: {
        		align: 'right',
        		x: 600,
        		y: 0
        	}
        },
        title : {
            text : title
        },
        xAxis: {
        	crosshair: {
                width: 1,
                color: '#C85A17',
                dashStyle: 'LongDash'
            },
	        type: 'datetime',
	        labels: {
						    format: '{value:%Y-%m-%d}'
						  }
	        },
        yAxis: [{
        	crosshair: {
                width: 1,
                color: '#C85A17',
                dashStyle: 'LongDash'
            },
            labels: {
                align: 'left',
                formatter: function () {
			                return Highcharts.numberFormat(this.value,2);
			            }
            },
            opposite: true,
            height: '100%',
            lineWidth: 2,
            resize: {
                enabled: true
            }
        }, {
            labels: {
                align: 'left',
                formatter: function () {
			                return Highcharts.numberFormat(this.value,0);
			            }
            },
            title: {
                text: 'Volume'
            },
            top: '70%',
            height: '25%',
            offset: 0,
            lineWidth: 2
        }],
        series : serialData
    });
}