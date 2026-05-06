"""Backtest page — strategy selection, performance metrics, equity curve."""

from datetime import date, timedelta

import dash
import dash_bootstrap_components as dbc
import plotly.graph_objects as go
from dash import Input, Output, State, callback, dcc, html
from loguru import logger

from analysis.technical import calculate as calc_indicators
from analysis.technical import get as get_ind
from backtest.engine import _signals_ma_cross, _signals_rsi
from backtest.engine import run as run_backtest
from collector.price import ensure_up_to_date

dash.register_page(__name__, path="/backtest", name="回測")

_TODAY = date.today()
_DEFAULT_START = str(_TODAY - timedelta(days=365 * 3))

# ---------------------------------------------------------------------------
# Layout
# ---------------------------------------------------------------------------

layout = dbc.Container(
    [
        dbc.Row(
            [
                dbc.Col(
                    [
                        dbc.Label("策略", className="text-muted small"),
                        dbc.Select(
                            id="bt-strategy",
                            options=[
                                {"label": "📊 MA 黃金交叉  (MA5 / MA20)", "value": "ma_cross"},
                                {"label": "📉 RSI 超買超賣  (30 / 70)", "value": "rsi"},
                            ],
                            value="ma_cross",
                        ),
                    ],
                    md=3,
                ),
                dbc.Col(
                    [
                        dbc.Label("回測區間", className="text-muted small"),
                        dcc.DatePickerRange(
                            id="bt-daterange",
                            start_date=_DEFAULT_START,
                            end_date=str(_TODAY),
                            display_format="YYYY-MM-DD",
                        ),
                    ],
                    md=5,
                ),
                dbc.Col(
                    dbc.Button(
                        "執行回測",
                        id="bt-btn",
                        color="success",
                        className="w-100 mt-4",
                    ),
                    md=2,
                ),
            ],
            className="mb-4 g-2",
        ),
        dbc.Spinner(html.Div(id="bt-result"), color="success"),
    ],
    fluid=True,
    className="px-0",
)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

_CARD_STYLE = {"backgroundColor": "#2b2f3e", "border": "1px solid #3a3f55"}


def _empty_prompt():
    return dbc.Alert(
        "請先在上方搜尋框輸入股票代碼。",
        color="secondary",
        style=_CARD_STYLE,
    )


def _metric_card(label: str, value: str, color: str = "light") -> dbc.Col:
    return dbc.Col(
        dbc.Card(
            dbc.CardBody(
                [
                    html.P(label, className="text-muted small mb-1"),
                    html.H4(value, className=f"text-{color} fw-bold mb-0"),
                ],
                className="py-2 px-3",
            ),
            style=_CARD_STYLE,
        ),
        xs=6, md=3, className="mb-3",
    )


def _fmt_pct(val) -> tuple[str, str]:
    if val is None:
        return "—", "light"
    pct = float(val) * 100
    return f"{pct:+.2f}%", "success" if pct >= 0 else "danger"


# ---------------------------------------------------------------------------
# Callback
# ---------------------------------------------------------------------------


@callback(
    Output("bt-result", "children"),
    Input("bt-btn", "n_clicks"),
    State("global-symbol-store", "data"),
    State("bt-strategy", "value"),
    State("bt-daterange", "start_date"),
    State("bt-daterange", "end_date"),
    prevent_initial_call=True,
)
def run_bt(n_clicks, store_data, strategy, start_date, end_date):
    symbol = (store_data or {}).get("symbol")
    if not symbol:
        return _empty_prompt()

    start = date.fromisoformat(start_date) if start_date else date.today() - timedelta(days=365 * 3)
    end = date.fromisoformat(end_date) if end_date else date.today()

    try:
        ensure_up_to_date(symbol)
    except Exception as exc:
        logger.warning(f"[backtest] ensure_up_to_date failed: {exc}")

    try:
        calc_indicators(symbol)
    except Exception as exc:
        logger.warning(f"[backtest] calc_indicators failed: {exc}")

    result = run_backtest(symbol, strategy, start_date=start, end_date=end)

    if "error" in result:
        return dbc.Alert(f"回測失敗：{result['error']}", color="danger")

    ret_str, ret_color = _fmt_pct(result.get("total_return"))
    dd_str, dd_color = _fmt_pct(result.get("max_drawdown"))
    wr_str, wr_color = _fmt_pct(result.get("win_rate"))
    sharpe = result.get("sharpe_ratio")
    sharpe_str = f"{sharpe:.3f}" if sharpe is not None else "—"
    sharpe_color = "success" if (sharpe or 0) >= 1 else "warning" if (sharpe or 0) >= 0 else "danger"

    strategy_label = "MA 黃金交叉" if strategy == "ma_cross" else "RSI 超買超賣"

    cards = dbc.Row(
        [
            _metric_card("總報酬率", ret_str, ret_color),
            _metric_card("最大回撤", dd_str, dd_color),
            _metric_card("夏普比率", sharpe_str, sharpe_color),
            _metric_card("勝率", wr_str, wr_color),
        ]
    )

    # ── Equity curve ─────────────────────────────────────────────────────────
    equity_graph = html.Div()
    try:
        import pandas as pd
        import vectorbt as vbt
        from sqlalchemy import select

        from db.models import StockPrice
        from db.session import get_session

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
            price_vals = {pd.Timestamp(r.date): float(r.close) for r in rows if r.close}

        closes = pd.Series(price_vals, name="close")
        ind = get_ind(symbol, start, end)

        if not closes.empty and not ind.empty:
            common = closes.index.intersection(ind.index)
            closes_a = closes.loc[common]
            ind_a = ind.loc[common]
            entries, exits = (
                _signals_ma_cross(ind_a) if strategy == "ma_cross" else _signals_rsi(ind_a)
            )
            entries = entries.reindex(closes_a.index, fill_value=False).astype(bool)
            exits = exits.reindex(closes_a.index, fill_value=False).astype(bool)

            portfolio = vbt.Portfolio.from_signals(
                close=closes_a, entries=entries, exits=exits,
                init_cash=100_000.0, fees=0.001425, freq="D",
            )
            equity = portfolio.value()

            # Buy-and-hold baseline
            bh = closes_a / closes_a.iloc[0] * 100_000.0

            fig = go.Figure()
            fig.add_trace(
                go.Scatter(
                    x=equity.index.astype(str), y=equity.values,
                    mode="lines", name="策略資產淨值",
                    line=dict(color="#00bc8c", width=2),
                    fill="tozeroy", fillcolor="rgba(0,188,140,0.06)",
                    hovertemplate="%{x}<br>%{y:,.0f}<extra></extra>",
                )
            )
            fig.add_trace(
                go.Scatter(
                    x=bh.index.astype(str), y=bh.values,
                    mode="lines", name="Buy & Hold",
                    line=dict(color="#adb5bd", width=1.2, dash="dot"),
                    hovertemplate="%{x}<br>%{y:,.0f}<extra></extra>",
                )
            )
            fig.add_hline(y=100_000, line_dash="dot", line_color="rgba(255,255,255,0.15)")
            fig.update_layout(
                template="plotly_dark",
                paper_bgcolor="#2b2f3e",
                plot_bgcolor="#2b2f3e",
                title=dict(
                    text=f"{symbol}  {strategy_label}  資產曲線  vs  Buy & Hold",
                    font=dict(color="#dee2e6", size=13), x=0,
                ),
                yaxis=dict(title="資產淨值", gridcolor="#3a3f55", zeroline=False),
                xaxis=dict(gridcolor="#3a3f55"),
                height=390,
                margin=dict(t=50, b=20, l=65, r=20),
                legend=dict(orientation="h", y=1.07),
            )
            equity_graph = dbc.Card(
                dbc.CardBody(
                    dcc.Graph(figure=fig, config={"scrollZoom": True}),
                    className="p-0",
                ),
                style=_CARD_STYLE,
                className="mb-4",
            )
    except Exception as exc:
        logger.warning(f"[backtest] equity curve failed: {exc}")

    # ── Trades table ─────────────────────────────────────────────────────────
    trades_section = html.Div()
    trades = result.get("trades", [])
    if trades:
        import pandas as pd

        tdf = pd.DataFrame(trades)
        display_cols = [c for c in tdf.columns if not c.startswith("Column")]
        pnl_col = next(
            (c for c in display_cols if "pnl" in c.lower() or "profit" in c.lower()), None
        )

        header_cells = [
            html.Th(c, className="text-muted small", style={"whiteSpace": "nowrap"})
            for c in display_cols
        ]
        body_rows = []
        for _, row in tdf[display_cols].iterrows():
            cells = []
            for c in display_cols:
                val = row[c]
                cls = ""
                if c == pnl_col:
                    try:
                        cls = "text-success" if float(val) >= 0 else "text-danger"
                    except (TypeError, ValueError):
                        pass
                cells.append(html.Td(str(val) if val is not None else "—", className=cls))
            body_rows.append(html.Tr(cells))

        trades_section = dbc.Card(
            dbc.CardBody(
                [
                    html.H6(f"交易明細（共 {len(trades)} 筆）", className="text-light mb-2"),
                    dbc.Table(
                        [html.Thead(html.Tr(header_cells)), html.Tbody(body_rows)],
                        bordered=False,
                        hover=True,
                        responsive=True,
                        size="sm",
                        className="text-light mb-0",
                        style={"fontSize": "0.8rem"},
                    ),
                ]
            ),
            style=_CARD_STYLE,
        )

    return html.Div(
        [
            html.H5(
                f"{symbol}  —  {strategy_label}  （{start} → {end}）",
                className="text-light mb-3",
            ),
            cards,
            equity_graph,
            trades_section,
        ]
    )
