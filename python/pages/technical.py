"""Technical analysis page — candlestick + MA + RSI + MACD."""

from datetime import date, timedelta

import dash
import dash_bootstrap_components as dbc
import plotly.graph_objects as go
from dash import Input, Output, State, callback, dcc, html
from loguru import logger
from plotly.subplots import make_subplots
from sqlalchemy import select

from analysis.technical import calculate as calc_indicators
from analysis.technical import get as get_indicators
from collector.price import ensure_up_to_date
from db.models import StockPrice
from db.session import get_session

dash.register_page(__name__, path="/technical", name="技術分析")

_TODAY = date.today()
_DEFAULT_START = str(_TODAY - timedelta(days=365))

# ---------------------------------------------------------------------------
# Layout
# ---------------------------------------------------------------------------

layout = dbc.Container(
    [
        dbc.Row(
            [
                dbc.Col(
                    dcc.DatePickerRange(
                        id="tech-daterange",
                        start_date=_DEFAULT_START,
                        end_date=str(_TODAY),
                        display_format="YYYY-MM-DD",
                    ),
                    md="auto",
                ),
                dbc.Col(
                    dbc.Button("重新查詢", id="tech-btn", color="primary", size="sm"),
                    md="auto",
                    className="d-flex align-items-center",
                ),
            ],
            className="mb-3 g-2 align-items-center",
        ),
        dbc.Spinner(html.Div(id="tech-chart"), color="primary"),
    ],
    fluid=True,
    className="px-0",
)

# ---------------------------------------------------------------------------
# Callback
# ---------------------------------------------------------------------------


def _empty_prompt():
    return dbc.Alert(
        "請先在上方搜尋框輸入股票代碼。",
        color="secondary",
        style={"backgroundColor": "#2b2f3e", "border": "1px solid #3a3f55"},
    )


@callback(
    Output("tech-chart", "children"),
    Input("global-symbol-store", "data"),
    Input("tech-btn", "n_clicks"),
    State("tech-daterange", "start_date"),
    State("tech-daterange", "end_date"),
)
def update_chart(store_data, n_clicks, start_date, end_date):
    symbol = (store_data or {}).get("symbol")
    if not symbol:
        return _empty_prompt()

    start = date.fromisoformat(start_date) if start_date else date.today() - timedelta(days=365)
    end = date.fromisoformat(end_date) if end_date else date.today()

    try:
        ensure_up_to_date(symbol)
    except Exception as exc:
        logger.warning(f"[technical] ensure_up_to_date failed: {exc}")

    try:
        calc_indicators(symbol)
    except Exception as exc:
        logger.warning(f"[technical] calc_indicators failed: {exc}")

    # ── Load OHLCV ────────────────────────────────────────────────────────────
    try:
        import pandas as pd
        with get_session() as session:
            rows = session.execute(
                select(StockPrice)
                .where(
                    StockPrice.symbol == symbol,
                    StockPrice.date >= start,
                    StockPrice.date <= end,
                )
                .order_by(StockPrice.date)
            ).scalars().all()
            price_data = [
                {
                    "date": str(r.date),
                    "open": r.open,
                    "high": r.high,
                    "low": r.low,
                    "close": r.close,
                    "volume": r.volume,
                }
                for r in rows
            ]
    except Exception as exc:
        return dbc.Alert(f"資料庫查詢失敗：{exc}", color="danger")

    if not price_data:
        return dbc.Alert(f"找不到 {symbol} 在指定區間內的價格資料。", color="warning")

    import pandas as pd
    price_df = pd.DataFrame(price_data)

    try:
        ind = get_indicators(symbol, start, end)
    except Exception as exc:
        logger.warning(f"[technical] get_indicators failed: {exc}")
        ind = pd.DataFrame()

    # ── Chart ─────────────────────────────────────────────────────────────────
    fig = make_subplots(
        rows=4, cols=1,
        shared_xaxes=True,
        row_heights=[0.50, 0.14, 0.18, 0.18],
        vertical_spacing=0.025,
        subplot_titles=("K 線 + 均線", "成交量", "RSI (14)", "MACD (12/26/9)"),
    )

    fig.add_trace(
        go.Candlestick(
            x=price_df["date"],
            open=price_df["open"],
            high=price_df["high"],
            low=price_df["low"],
            close=price_df["close"],
            name="K線",
            increasing_line_color="#00bc8c",
            decreasing_line_color="#e74c3c",
            increasing_fillcolor="#00bc8c",
            decreasing_fillcolor="#e74c3c",
        ),
        row=1, col=1,
    )

    if not ind.empty:
        for col, name, color in [
            ("ma5", "MA5", "#f6c90e"),
            ("ma20", "MA20", "#f39c12"),
            ("ma60", "MA60", "#9b59b6"),
        ]:
            if col in ind.columns and ind[col].notna().any():
                fig.add_trace(
                    go.Scatter(
                        x=ind.index.astype(str), y=ind[col],
                        name=name, mode="lines",
                        line=dict(color=color, width=1.3),
                    ),
                    row=1, col=1,
                )

    vol_colors = [
        "#00bc8c" if c >= o else "#e74c3c"
        for c, o in zip(price_df["close"].fillna(0), price_df["open"].fillna(0))
    ]
    fig.add_trace(
        go.Bar(
            x=price_df["date"], y=price_df["volume"],
            name="成交量", marker_color=vol_colors, showlegend=False,
        ),
        row=2, col=1,
    )

    if not ind.empty and "rsi14" in ind.columns and ind["rsi14"].notna().any():
        fig.add_trace(
            go.Scatter(
                x=ind.index.astype(str), y=ind["rsi14"],
                name="RSI", line=dict(color="#3498db", width=1.5),
            ),
            row=3, col=1,
        )
        for level, color in [(70, "rgba(231,76,60,0.35)"), (30, "rgba(0,188,140,0.35)")]:
            fig.add_hline(y=level, line_dash="dot", line_color=color, row=3, col=1)

    if not ind.empty and "macd" in ind.columns and ind["macd"].notna().any():
        fig.add_trace(
            go.Scatter(
                x=ind.index.astype(str), y=ind["macd"],
                name="MACD", line=dict(color="#3498db", width=1.5),
            ),
            row=4, col=1,
        )
        fig.add_trace(
            go.Scatter(
                x=ind.index.astype(str), y=ind["macd_signal"],
                name="Signal", line=dict(color="#f39c12", width=1.5),
            ),
            row=4, col=1,
        )
        hist = ind["macd_hist"].fillna(0)
        fig.add_trace(
            go.Bar(
                x=ind.index.astype(str), y=hist,
                name="Histogram",
                marker_color=["#00bc8c" if v >= 0 else "#e74c3c" for v in hist],
                showlegend=False,
            ),
            row=4, col=1,
        )

    fig.update_layout(
        template="plotly_dark",
        paper_bgcolor="#222",
        plot_bgcolor="#222",
        height=820,
        margin=dict(t=40, b=20, l=55, r=20),
        legend=dict(orientation="h", y=1.02, x=0, bgcolor="rgba(0,0,0,0)"),
        xaxis_rangeslider_visible=False,
        title=dict(
            text=f"{symbol}  技術分析",
            font=dict(color="#dee2e6", size=14),
            x=0,
        ),
    )
    for row_n, title in [(1, "價格"), (2, "量"), (3, "RSI"), (4, "MACD")]:
        fig.update_yaxes(
            title_text=title,
            gridcolor="#2b2f3e",
            zeroline=False,
            row=row_n, col=1,
        )
    if not ind.empty:
        fig.update_yaxes(range=[0, 100], row=3, col=1)

    return dbc.Card(
        dbc.CardBody(
            dcc.Graph(figure=fig, config={"scrollZoom": True, "displayModeBar": True}),
            className="p-0",
        ),
        style={"backgroundColor": "#2b2f3e", "border": "1px solid #3a3f55"},
    )
