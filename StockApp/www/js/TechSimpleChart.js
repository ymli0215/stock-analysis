
function initalTechSimpleChart(mid, stockNo, chartTitle, chartVolumeTitle) {
    var originalDrawPoints = Highcharts.seriesTypes.column.prototype.drawPoints;
    Highcharts.seriesTypes.column.prototype.drawPoints = function () {
        var merge = Highcharts.merge,
            series = this,
            chart = this.chart,
            points = series.points,
            i = 0;
        if ((chart.series[7]) && (typeof (chart.series[7].points) !== 'undefined') && (chart.series[7].points !== null)) {
            i = chart.series[7].points.length;
            while (i--) {
                var candlePoint = chart.series[7].points[i];
                var color = (candlePoint.open < candlePoint.close) ? '#FF0000' : '#008000';
                var seriesPointAttr = merge(series.pointAttr);
                seriesPointAttr[''].fill = color;
                seriesPointAttr.hover.fill = Highcharts.Color(color).brighten(0.3).get();
                seriesPointAttr.select.fill = color;
                if (points[i] && points[i].pointAttr) points[i].pointAttr = seriesPointAttr;
            }
        }
        originalDrawPoints.call(this);
    }

    var options = {
        chart: {
            renderTo: 'jChart1',
            plotBorderWidth: 2,
            alignTicks: false,
            animation: false,
            events: {
                load: function () {
                    try {
                        var maxLength = this.series[7].points.length - 1;
                        var day = this.series[7].points[maxLength].x;
                        var s = '<table width="98%" class="charttable" cellpadding="0" cellspacing="0"><tr><td>' + Highcharts.dateFormat(' %Y/%m/%d', day);
                        var p = this.series[7].options;
                        var volume = '成交量';
                        if (p.series && p.series.name == "摩台指 (STW)") volume = '未平倉';
                        s += '</td><td class="name" width="40px">開盤</td><td width="70px">' + this.series[7].points[maxLength].open +
                                '</td><td class="name" width="40px">最高</td><td width="70px">' + this.series[7].points[maxLength].high +
                                '</td><td class="name" width="40px">最低</td><td width="70px">' + this.series[7].points[maxLength].low +
                                '</td><td class="name" width="40px">收盤</td><td width="70px">' + this.series[7].points[maxLength].close +
                                '</td><td class="name" width="45px">' + volume + '</td><td width="70px">' + Highcharts.numberFormat(this.series[8].points[maxLength].y, 0) +
                                '</td></tr><tr><td>' + p.name +
                                '</td><td class="d5">5日</td><td>' + this.series[1].points[this.series[1].points.length - 1].y +
                                '</td><td class="d10">10日</td><td>' + this.series[2].points[this.series[2].points.length - 1].y +
                                '</td><td class="d20">20日</td><td>' + this.series[3].points[this.series[3].points.length - 1].y +
                                '</td><td class="d60">60日</td><td>' + this.series[4].points[this.series[4].points.length - 1].y +
                                '</td><td class="d120">120日</td><td>' + this.series[5].points[this.series[5].points.length - 1].y;
                        s += '</td></tr></table>'
                        $('#info').html(s);
                        var s1 = '<table width="350px" class="charttable1" cellpadding="0" cellspacing="0"><tr>';
                        if (parseInt(this.series[9].points[this.series[9].points.length - 1].y) > 99999)
                            s1 = '<table width="400px" class="charttable1" cellpadding="0" cellspacing="0"><tr>';
                        if (parseInt(this.series[9].points[this.series[9].points.length - 1].y) > 999999)
                            s1 = '<table width="450px" class="charttable1" cellpadding="0" cellspacing="0"><tr>';
                        if (parseInt(this.series[9].points[this.series[9].points.length - 1].y) > 9999999)
                            s1 = '<table width="550px" class="charttable1" cellpadding="0" cellspacing="0"><tr>';
                        s1 += '<td class="volume">' + volume + '</td><td>' + Highcharts.numberFormat(this.series[8].points[maxLength].y, 0) +
                                '</td><td class="d9">MV5 ' + Highcharts.numberFormat(this.series[9].points[this.series[9].points.length - 1].y, 0) +
                                '</td><td class="k9">MV20 ' + Highcharts.numberFormat(this.series[10].points[this.series[10].points.length - 1].y, 0) +
                                '</td></tr></table>';
                        $('#info1').html(s1);
                        var s2 = '<table class="charttable1" cellpadding="0" cellspacing="0"><tr>' +
                                '<td class="k9">K9 ' + Highcharts.numberFormat(this.series[11].points[this.series[11].points.length - 1].y, 2) +
                                '</td><td class="d9">D9 ' + Highcharts.numberFormat(this.series[12].points[this.series[12].points.length - 1].y, 2) +
                                '</td></tr></table>';
                        $('#info2').html(s2);
                        var s3 = '<table class="charttable1" cellpadding="0" cellspacing="0"><tr>' +
                                '<td class="RSI6">RSI6 ' + Highcharts.numberFormat(this.series[13].points[this.series[13].points.length - 1].y, 2) +
                                '</td><td class="RSI12">RSI12 ' + Highcharts.numberFormat(this.series[14].points[this.series[14].points.length - 1].y, 2) +
                                '</td></tr></table>';
                        $('#info4').html(s3);
                        var s4 = '<table class="charttable1" cellpadding="0" cellspacing="0"><tr>' +
                                '<td class="William20">William20 ' + Highcharts.numberFormat(this.series[15].points[this.series[15].points.length - 1].y, 2) +
                                '</td></tr></table>';
                        $('#info5').html(s4);
                    }
                    catch (e) {
                        console.log(e);
                    }
                }
            }
        },
        credits: { enabled: false },
        colors: [
            'red',
            'blue',
            'Green',
            '#FF00FF',
            '#996600',
            '#ff8705',
            'red',
            'blue',
            'blue',
            'red',
            'blue'
        ],
        legend: {
            enabled: false,
            floating: true,
            x: -5,
            y: -198,
            borderWidth: 0
        },
        scrollbar: { enabled: false },
        rangeSelector: {
            enabled: false
        },
        navigator: { enabled: false },
        exporting: { enabled: false },
        title: {
            text: chartTitle,
            floating: true,
            top: 15
        },
        subtitle: {
            text: '嗨投資 histock.tw',
            floating: true,
            x: 250,
            y: 15
        },
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
                marker: { states: { hover: { enabled: false } } }
            },
            series: {
                dataGrouping: {
                    enabled: false
                }
            }
        },
        xAxis: {
            labels: {
                formatter: function () { return Highcharts.dateFormat('%m/%d', this.value); }
            },
            gridLineWidth: 1
        },
        tooltip: {
            pointFormat: '',
            yDecimals: 2,
            crosshairs: [{
                width: 1,
                color: '#C85A17',
                dashStyle: 'LongDash'
            }, {
                width: 1,
                color: '#C85A17',
                dashStyle: 'LongDash'
            }],
            formatter: function () {
                var s = '<table width="98%" class="charttable" cellpadding="0" cellspacing="0"><tr><td>' + Highcharts.dateFormat(' %Y/%m/%d', this.x);
                var p = this.points[6];
                var volume = '成交量';
                if (p && p.series && p.series.name == "摩台指 (STW)") volume = '未平倉';
                s += '</td><td class="name" width="40px">開盤</td><td width="70px">' + p.point.open +
                        '</td><td class="name" width="40px">最高</td><td width="70px">' + p.point.high +
                        '</td><td class="name" width="40px">最低</td><td width="70px">' + p.point.low +
                        '</td><td class="name" width="40px">收盤</td><td width="70px">' + p.point.close +
                        '</td><td class="name" width="45px">' + volume + '</td><td width="70px">' + Highcharts.numberFormat(this.points[7].y, 0) +
                        '</td></tr><tr><td>' + p.series.name +
                        '</td><td class="d5">5日</td><td>' + this.points[1].y +
                        '</td><td class="d10">10日</td><td>' + this.points[2].y +
                        '</td><td class="d20">20日</td><td>' + this.points[3].y +
                        '</td><td class="d60">60日</td><td>' + this.points[4].y +
                        '</td><td class="d120">120日</td><td>' + this.points[5].y;
                s += '</td></tr></table>'
                $('#info').html(s);
                var s1 = '<table width="350px" class="charttable1" cellpadding="0" cellspacing="0"><tr>';
                if (parseInt(this.points[8].y) > 99999)
                    s1 = '<table width="400px" class="charttable1" cellpadding="0" cellspacing="0"><tr>';
                if (parseInt(this.points[8].y) > 999999)
                    s1 = '<table width="450px" class="charttable1" cellpadding="0" cellspacing="0"><tr>';
                if (parseInt(this.points[8].y) > 9999999)
                    s1 = '<table width="550px" class="charttable1" cellpadding="0" cellspacing="0"><tr>';
                s1 += '<td class="volume">' + volume + '</td><td>' + Highcharts.numberFormat(this.points[7].y, 0) +
                        '</td><td class="d9">MV5 ' + Highcharts.numberFormat(this.points[8].y, 0) +
                        '</td><td class="k9">MV20 ' + Highcharts.numberFormat(this.points[9].y, 0) +
                        '</td></tr></table>';
                $('#info1').html(s1);
                var s2 = '<table class="charttable1" cellpadding="0" cellspacing="0"><tr>' +
                        '<td class="k9">K9 ' + Highcharts.numberFormat(this.points[10].y, 2) +
                        '</td><td class="d9">D9 ' + Highcharts.numberFormat(this.points[11].y, 2) +
                        '</td></tr></table>';
                $('#info2').html(s2);
                var s3 = '<table class="charttable1" cellpadding="0" cellspacing="0"><tr>' +
                        '<td class="RSI6">RSI6 ' + Highcharts.numberFormat(this.points[12].y, 2) +
                        '</td><td class="RSI12">RSI12 ' + Highcharts.numberFormat(this.points[13].y, 2) +
                        '</td></tr></table>';
                $('#info4').html(s3);
                var s4 = '<table class="charttable1" cellpadding="0" cellspacing="0"><tr>' +
                        '<td class="William20">William20 ' + Highcharts.numberFormat(this.points[14].y, 2) +
                        '</td></tr></table>';
                $('#info5').html(s4);
                return false;
            }
        },
        yAxis: [{
            height: 300,
            opposite: true
        }, {
            top: 335,
            height: 120,
            title: { margin: -65, text: chartVolumeTitle, rotation: 0, align: 'high', y: 15 }
        }, {//KD
            top: 455,
            height: 65,
            plotLines:
            [{
                value: 100, color: 'gray', dashStyle: 'Solid', width: 1
            }, {
                value: 80, color: '#b0b0b0', dashStyle: 'shortdash', width: 1
            }, {
                value: 50, color: '#b0b0b0', dashStyle: 'shortdash', width: 1
            }, {
                value: 20, color: '#b0b0b0', dashStyle: 'shortdash', width: 1
            }],
            gridLineWidth: 0,
            max: 100,
            min: 0,
            labels: { enabled: false },
            title: { margin: -20, text: 'KD', rotation: 0, align: 'high', y: 15 }
        }, {// RSI
            top: 520,
            height: 65,
            plotLines: [{
                value: 100, color: 'gray', dashStyle: 'Solid', width: 1
            }, {
                value: 80, color: '#b0b0b0', dashStyle: 'shortdash', width: 1
            }, {
                value: 50, color: '#b0b0b0', dashStyle: 'shortdash', width: 1
            }, {
                value: 20, color: '#b0b0b0', dashStyle: 'shortdash', width: 1
            }],
            gridLineWidth: 0,
            max: 100,
            min: 0,
            labels: { enabled: false },
            title: { margin: -25, text: 'RSI', rotation: 0, align: 'high', y: 15 }
        }, {// William
            top: 585,
            height: 65,
            plotLines: [{
                value: 0, color: 'gray', dashStyle: 'Solid', width: 1
            }, {
                value: 80, color: '#b0b0b0', dashStyle: 'shortdash', width: 1
            }, {
                value: 50, color: '#b0b0b0', dashStyle: 'shortdash', width: 1
            }, {
                value: 20, color: '#b0b0b0', dashStyle: 'shortdash', width: 1
            }],
            gridLineWidth: 0,
            max: 100,
            min: 0,
            reversed: true,
            labels: { enabled: false },
            title: { margin: -50, text: 'William', rotation: 0, align: 'high', y: 15 }
        }, {
            height: 325,
            max: 1,
            min: 0,
            labels: { enabled: false },
            gridLineWidth: 0
        }, {
            top: 580,
            height: 0,
            labels: { enabled: false },
            gridLineWidth: 0
        }],		    
        series: [{
            name: 'Close',
            color: '#ffffff'
        }, {
            name: '5MA'
        }, {
            name: '10MA'
        }, {
            name: '20MA'
        }, {
            name: '60MA',
            yAxis: 6
        }, {
            name: '120MA',
            yAxis: 6
        }, {
            name: '240MA',
            visible: false
        }, {
            type: 'candlestick',
            name: chartTitle,
            id: 'a'
        },{
            type: 'column',
            name: chartVolumeTitle,
            turboThreshold: Number.MAX_VALUE,
            yAxis: 1
        },{
            name: 'MV5',
            yAxis: 1
        },{
            name: 'MV20',
            yAxis: 1
        }, {
            name: 'K9',
            yAxis: 2
        },{
            name: 'D9',
            yAxis: 2
        },{
            name: 'RSI6',
            yAxis: 3
        }, {
            name: 'RSI12',
            yAxis: 3
        }, {
            name: 'William20',
            yAxis: 4
        },{
            name: 'chg',
            yAxis: 5
        },{
            type: 'flags',
            name: '均線扣抵',	   
            onSeries: 'a',
            y:100,
            shape: 'squarepin'
        }]}
       
    $('#dayKLoadingIMG').show();
    initalChart(options, '/stock/module/stockdata.aspx?m=dayk&time=0.25&no=' + stockNo + '&mid=' + mid);
}

function initalChart(options, paraUrl) {
    $.ajax({
        url: paraUrl, type: 'get', dataType: "json",
        complete: function () { $('#dayKLoadingIMG').hide(); },
        success: function (data) {
            options.series[0].data = eval(data['Close']);
            options.series[1].data = eval(data['Mean5']);
            options.series[2].data = eval(data['Mean10']);
            options.series[3].data = eval(data['Mean20']);
            options.series[4].data = eval(data['Mean60']);
            options.series[5].data = eval(data['Mean120']);
            options.series[6].data = eval(data['Mean240']);
            options.series[7].data = eval(data['Data']);
            options.series[8].data = eval(data['Volume']);
            options.series[9].data = eval(data['Mean5Volume']);
            options.series[10].data = eval(data['Mean20Volume']);
            options.series[11].data = eval(data['K9']);
            options.series[12].data = eval(data['D9']);
            options.series[13].data = eval(data['RSI6']);
            options.series[14].data = eval(data['RSI12']);
            options.series[15].data = eval(data['William20']);
            options.series[16].data = eval(data['FlagY']);
            var chart = new Highcharts.StockChart(options);
            
        }
    });
}
