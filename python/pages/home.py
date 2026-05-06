"""Home page — stock overview or welcome screen."""

from datetime import date, timedelta

import dash
import dash_bootstrap_components as dbc
import plotly.graph_objects as go
from dash import Input, Output, callback, dcc, html
from loguru import logger
from sqlalchemy import select

from db.models import StockInfo, StockPrice
from db.session import get_session

dash.register_page(__name__, path="/", name="首頁")

# ---------------------------------------------------------------------------
# Layout
# ---------------------------------------------------------------------------

layout = dbc.Container(
    html.Div(id="home-content"),
    fluid=True,
    className="px-0",
)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _empty_prompt():
    return dbc.Row(
        dbc.Col(
            dbc.Card(
                dbc.CardBody(
                    [
                        html.Div("📈", style={"fontSize": "3.5rem", "textAlign": "center"}),
                        html.H3(
                            "歡迎使用股票分析系統",
                            className="text-center text-light mt-2 mb-3",
                        ),
                        html.P(
                            "在上方搜尋框輸入股票代碼，即可開始分析",
                            className="text-center text-muted mb-4",
                        ),
                        html.Hr(style={"borderColor": "#3a3f55"}),
                        dbc.Row(
                            [
                                _hint_card("🇹🇼 台股", "2330.TW、2317.TW", "success"),
                                _hint_card("🇺🇸 美股", "AAPL、NVDA、TSLA", "primary"),
                                _hint_card("🇯🇵 日股", "7203.T、6758.T", "warning"),
                            ],
                            className="g-3",
                        ),
                    ]
                ),
                style={"backgroundColor": "#2b2f3e", "border": "1px solid #3a3f55"},
            ),
            md=8,
            lg=6,
            className="mx-auto mt-5",
        )
    )


def _hint_card(title: str, examples: str, color: str) -> dbc.Col:
    return dbc.Col(
        dbc.Card(
            dbc.CardBody(
                [
                    html.H6(title, className=f"text-{color} mb-1"),
                    html.Small(examples, className="text-muted"),
                ],
                className="py-2 px-3",
            ),
            style={"backgroundColor": "#1e2130", "border": f"1px solid #3a3f55"},
        ),
        xs=12, md=4,
    )


def _stat_card(label: str, value: str, color: str = "light") -> dbc.Col:
    return dbc.Col(
        dbc.Card(
            dbc.CardBody(
                [
                    html.P(label, className="text-muted small mb-1"),
                    html.H5(value, className=f"text-{color} fw-bold mb-0"),
                ],
                className="py-2 px-3",
            ),
            style={"backgroundColor": "#2b2f3e", "border": "1px solid #3a3f55"},
        ),
        xs=6, md=3, className="mb-3",
    )


# ---------------------------------------------------------------------------
# Callback
# ---------------------------------------------------------------------------


@callback(
    Output("home-content", "children"),
    Input("global-symbol-store", "data"),
)
def update_home(store_data):
    symbol = (store_data or {}).get("symbol")
    if not symbol:
        return _empty_prompt()

    # Extract all values inside the session
    try:
        with get_session() as session:
            _lat = session.execute(
                select(StockPrice)
                .where(StockPrice.symbol == symbol)
                .order_by(StockPrice.date.desc())
                .limit(1)
            ).scalars().first()

            _prev = session.execute(
                select(StockPrice)
                .where(StockPrice.symbol == symbol)
                .order_by(StockPrice.date.desc())
                .limit(2)
                .offset(1)
            ).scalars().first()

            _info = session.execute(
                select(StockInfo)
                .where(StockInfo.symbol == symbol)
                .order_by(StockInfo.updated_at.desc())
                .limit(1)
            ).scalars().first()

            # Sparkline: last 90 days
            cutoff = date.today() - timedelta(days=90)
            _spark_rows = session.execute(
                select(StockPrice.date, StockPrice.close)
                .where(StockPrice.symbol == symbol, StockPrice.date >= cutoff)
                .order_by(StockPrice.date)
            ).all()

            if _lat is None:
                return dbc.Alert(f"找不到 {symbol} 的資料。", color="warning")

            lat = {
                "close": _lat.close,
                "open": _lat.open,
                "high": _lat.high,
                "low": _lat.low,
                "volume": _lat.volume,
                "date": str(_lat.date),
            }
            prev_close = _prev.close if _prev else _lat.close
            info = {
                "name": (_info.name if _info else None) or symbol,
                "market": (_info.market if _info else None) or "—",
                "currency": (_info.currency if _info else None) or "",
                "sector": (_info.sector if _info else None) or "—",
                "exchange": (_info.exchange if _info else None) or "—",
            }
            spark_data = [(str(r.date), r.close) for r in _spark_rows if r.close is not None]
    except Exception as exc:
        logger.error(f"[home] DB query failed for {symbol}: {exc}")
        return dbc.Alert(f"資料庫查詢失敗：{exc}", color="danger")

    close = lat["close"] or 0.0
    change = close - (prev_close or close)
    change_pct = (change / prev_close * 100) if prev_close else 0.0
    ch_color = "success" if change >= 0 else "danger"
    arrow = "▲" if change >= 0 else "▼"

    _market_colors = {"TW": "success", "US": "primary", "JP": "warning"}
    market_color = _market_colors.get(info["market"], "secondary")

    # ── Header row ─────────────────────────────────────────────────────────
    page_header = dbc.Row(
        [
            dbc.Col(
                html.Div(
                    [
                        html.H4(
                            [
                                info["name"],
                                dbc.Badge(
                                    info["market"],
                                    color=market_color,
                                    className="ms-2 align-middle",
                                    style={"fontSize": "0.7rem"},
                                ),
                            ],
                            className="text-light mb-1",
                        ),
                        html.Small(
                            f"{symbol}  ·  {info['exchange']}  ·  {info['sector']}",
                            className="text-muted",
                        ),
                    ]
                ),
            ),
            dbc.Col(
                html.Div(
                    [
                        html.H3(
                            f"{close:,.2f} {info['currency']}",
                            className="text-light mb-0",
                        ),
                        html.Span(
                            f"{arrow} {change:+.2f}  ({change_pct:+.2f}%)",
                            className=f"text-{ch_color} fw-semibold",
                        ),
                    ],
                    className="text-end",
                ),
                width="auto",
            ),
        ],
        align="center",
        className="mb-3",
    )

    # ── Stat cards ──────────────────────────────────────────────────────────
    cards = dbc.Row(
        [
            _stat_card("開盤", f"{lat['open']:,.2f}" if lat["open"] else "—"),
            _stat_card("最高", f"{lat['high']:,.2f}" if lat["high"] else "—", "success"),
            _stat_card("最低", f"{lat['low']:,.2f}" if lat["low"] else "—", "danger"),
            _stat_card("成交量", f"{lat['volume']:,}" if lat["volume"] else "—"),
            _stat_card("日期", lat["date"]),
            _stat_card("漲跌幅", f"{change_pct:+.2f}%", ch_color),
            _stat_card("市場", info["market"]),
            _stat_card("幣別", info["currency"] or "—"),
        ],
        className="mb-3",
    )

    # ── Sparkline ───────────────────────────────────────────────────────────
    spark_fig = go.Figure()
    if spark_data:
        dates, closes = zip(*spark_data)
        line_color = "#00bc8c" if change >= 0 else "#e74c3c"
        spark_fig.add_trace(
            go.Scatter(
                x=list(dates),
                y=list(closes),
                mode="lines",
                line=dict(color=line_color, width=2),
                fill="tozeroy",
                fillcolor=line_color.replace(")", ", 0.1)").replace("rgb", "rgba")
                if "rgb" in line_color
                else (
                    "rgba(0,188,140,0.1)" if change >= 0 else "rgba(231,76,60,0.1)"
                ),
                hovertemplate="%{x}<br>%{y:,.2f}<extra></extra>",
            )
        )
    spark_fig.update_layout(
        template="plotly_dark",
        paper_bgcolor="#2b2f3e",
        plot_bgcolor="#2b2f3e",
        height=220,
        margin=dict(t=10, b=30, l=50, r=10),
        xaxis=dict(showgrid=False, zeroline=False),
        yaxis=dict(showgrid=True, gridcolor="#3a3f55", zeroline=False),
        showlegend=False,
        title=dict(text="近 90 日收盤價", font=dict(color="#adb5bd", size=12), x=0),
    )

    return html.Div(
        [
            page_header,
            cards,
            dbc.Card(
                dbc.CardBody(dcc.Graph(figure=spark_fig, config={"displayModeBar": False})),
                style={"backgroundColor": "#2b2f3e", "border": "1px solid #3a3f55"},
            ),
        ]
    )
