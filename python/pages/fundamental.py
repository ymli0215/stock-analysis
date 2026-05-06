"""Fundamental analysis page — valuation metrics and income charts."""

import dash
import dash_bootstrap_components as dbc
import plotly.graph_objects as go
from dash import Input, Output, callback, dcc, html
from loguru import logger

from analysis.fundamental import fetch_and_save, get as get_fundamental
from collector.price import ensure_up_to_date

dash.register_page(__name__, path="/fundamental", name="基本面")

# ---------------------------------------------------------------------------
# Layout
# ---------------------------------------------------------------------------

layout = dbc.Container(
    dbc.Spinner(html.Div(id="fund-content"), color="primary"),
    fluid=True,
    className="px-0",
)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _empty_prompt():
    return dbc.Alert(
        "請先在上方搜尋框輸入股票代碼。",
        color="secondary",
        style={"backgroundColor": "#2b2f3e", "border": "1px solid #3a3f55"},
    )


def _metric_card(label: str, value: str, color: str = "light") -> dbc.Col:
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


def _fmt(val, suffix="", mult=1, dec=2):
    if val is None:
        return "—"
    try:
        return f"{float(val) * mult:,.{dec}f}{suffix}"
    except (TypeError, ValueError):
        return "—"


# ---------------------------------------------------------------------------
# Callback
# ---------------------------------------------------------------------------


@callback(
    Output("fund-content", "children"),
    Input("global-symbol-store", "data"),
)
def update_fundamental(store_data):
    symbol = (store_data or {}).get("symbol")
    if not symbol:
        return _empty_prompt()

    try:
        ensure_up_to_date(symbol)
    except Exception as exc:
        logger.warning(f"[fundamental] ensure_up_to_date failed: {exc}")

    try:
        fetch_and_save(symbol)
    except Exception as exc:
        logger.warning(f"[fundamental] fetch_and_save failed: {exc}")

    try:
        data = get_fundamental(symbol)
    except Exception as exc:
        return dbc.Alert(f"資料庫查詢失敗：{exc}", color="danger")

    latest = data.get("latest_annual", {})
    quarterly = data.get("quarterly", [])

    if not latest and not quarterly:
        return dbc.Alert(f"找不到 {symbol} 的基本面資料。", color="warning")

    # ── Metric cards ─────────────────────────────────────────────────────────
    cards = dbc.Row(
        [
            _metric_card("EPS (Trailing)", _fmt(latest.get("eps"), " 元")),
            _metric_card("本益比 (PE)", _fmt(latest.get("pe_ratio"), "x")),
            _metric_card("股價淨值比 (PB)", _fmt(latest.get("pb_ratio"), "x")),
            _metric_card(
                "殖利率 (Yield)",
                _fmt(latest.get("dividend_yield"), "%", mult=100),
            ),
            _metric_card("ROE", _fmt(latest.get("roe"), "%", mult=100)),
            _metric_card("負債比 (D/E)", _fmt(latest.get("debt_to_equity"), "x")),
            _metric_card(
                "年度營收",
                _fmt(latest.get("revenue"), " B", mult=1e-9, dec=1)
                if latest.get("revenue") else "—",
            ),
            _metric_card(
                "年度淨利",
                _fmt(latest.get("net_income"), " B", mult=1e-9, dec=1)
                if latest.get("net_income") else "—",
            ),
        ],
        className="mb-4",
    )

    # ── Revenue / net income chart ────────────────────────────────────────────
    import pandas as pd

    all_records = []
    if latest:
        all_records.append(latest)
    all_records.extend(quarterly)

    chart_section = html.Div()
    records_with_data = [
        r for r in all_records
        if r.get("revenue") is not None or r.get("net_income") is not None
    ]

    if records_with_data:
        df = (
            pd.DataFrame(records_with_data)
            .sort_values("period")
        )
        labels = df["period"].astype(str) + " (" + df["period_type"] + ")"

        fig = go.Figure()
        if df["revenue"].notna().any():
            fig.add_trace(
                go.Bar(
                    x=labels, y=df["revenue"] / 1e9,
                    name="營收 (B)", marker_color="#375a7f",
                )
            )
        if df["net_income"].notna().any():
            fig.add_trace(
                go.Bar(
                    x=labels, y=df["net_income"] / 1e9,
                    name="淨利 (B)", marker_color="#00bc8c",
                )
            )

        fig.update_layout(
            template="plotly_dark",
            paper_bgcolor="#2b2f3e",
            plot_bgcolor="#2b2f3e",
            barmode="group",
            title=dict(text=f"{symbol}  營收與淨利", font=dict(color="#dee2e6", size=13), x=0),
            yaxis=dict(title="金額（十億）", gridcolor="#3a3f55"),
            xaxis=dict(gridcolor="#3a3f55"),
            height=380,
            margin=dict(t=45, b=20, l=55, r=20),
            legend=dict(orientation="h", y=1.08),
        )
        chart_section = dbc.Card(
            dbc.CardBody(
                dcc.Graph(figure=fig, config={"displayModeBar": False}),
                className="p-0",
            ),
            style={"backgroundColor": "#2b2f3e", "border": "1px solid #3a3f55"},
        )

    return html.Div(
        [
            html.H5(f"{symbol}  基本面指標", className="text-light mb-3"),
            cards,
            chart_section,
        ]
    )
