"""Cross-market correlation page — heatmap and rolling correlation chart."""

from datetime import date, timedelta

import dash
import dash_bootstrap_components as dbc
import plotly.graph_objects as go
from dash import Input, Output, State, callback, dcc, html
from loguru import logger

from analysis.correlation import calculate as calc_corr
from analysis.correlation import rolling_correlation
from collector.price import ensure_up_to_date

dash.register_page(__name__, path="/correlation", name="相關性")

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
                    [
                        dbc.Label("股票代碼（逗號分隔，至少兩支）", className="text-muted small"),
                        dbc.Input(
                            id="corr-symbols",
                            placeholder="例如 AAPL, 2330.TW, 7203.T, NVDA",
                            type="text",
                        ),
                    ],
                    md=5,
                ),
                dbc.Col(
                    [
                        dbc.Label("日期區間", className="text-muted small"),
                        dcc.DatePickerRange(
                            id="corr-daterange",
                            start_date=_DEFAULT_START,
                            end_date=str(_TODAY),
                            display_format="YYYY-MM-DD",
                        ),
                    ],
                    md=5,
                ),
                dbc.Col(
                    dbc.Button("計算相關性", id="corr-btn", color="primary", className="w-100 mt-4"),
                    md=2,
                ),
            ],
            className="mb-3 g-2",
        ),
        dbc.Row(
            [
                dbc.Col(
                    [
                        dbc.Label("滾動相關：股票 A", className="text-muted small"),
                        dbc.Input(id="corr-roll-a", placeholder="例如 AAPL", type="text"),
                    ],
                    md=3,
                ),
                dbc.Col(
                    [
                        dbc.Label("股票 B", className="text-muted small"),
                        dbc.Input(id="corr-roll-b", placeholder="例如 2330.TW", type="text"),
                    ],
                    md=3,
                ),
                dbc.Col(
                    [
                        dbc.Label("滾動視窗（天）", className="text-muted small"),
                        dbc.Input(id="corr-window", type="number", value=30, min=5, max=252),
                    ],
                    md=2,
                ),
            ],
            className="mb-4 g-2",
        ),
        dbc.Spinner(html.Div(id="corr-result"), color="primary"),
    ],
    fluid=True,
    className="px-0",
)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

_CARD_STYLE = {"backgroundColor": "#2b2f3e", "border": "1px solid #3a3f55"}
_CHART_LAYOUT = dict(
    template="plotly_dark",
    paper_bgcolor="#2b2f3e",
    plot_bgcolor="#2b2f3e",
    margin=dict(t=50, b=20, l=60, r=20),
)


def _wrap_graph(fig) -> dbc.Card:
    return dbc.Card(
        dbc.CardBody(
            dcc.Graph(figure=fig, config={"displayModeBar": False}),
            className="p-0",
        ),
        style=_CARD_STYLE,
        className="mb-4",
    )


# ---------------------------------------------------------------------------
# Callback — pre-populate symbols from store on store change
# ---------------------------------------------------------------------------


@callback(
    Output("corr-symbols", "value"),
    Input("global-symbol-store", "data"),
    State("corr-symbols", "value"),
)
def prefill_symbols(store_data, current_value):
    symbol = (store_data or {}).get("symbol")
    if not symbol:
        return current_value or ""
    if current_value and symbol in [s.strip().upper() for s in current_value.split(",")]:
        return current_value
    if current_value and current_value.strip():
        return current_value
    return symbol


@callback(
    Output("corr-result", "children"),
    Input("corr-btn", "n_clicks"),
    State("corr-symbols", "value"),
    State("corr-daterange", "start_date"),
    State("corr-daterange", "end_date"),
    State("corr-roll-a", "value"),
    State("corr-roll-b", "value"),
    State("corr-window", "value"),
    prevent_initial_call=True,
)
def update_correlation(n_clicks, symbols_str, start_date, end_date, roll_a, roll_b, window):
    if not symbols_str:
        return dbc.Alert("請輸入至少兩個股票代碼。", color="warning")

    symbols = [s.strip().upper() for s in symbols_str.split(",") if s.strip()]
    if len(symbols) < 2:
        return dbc.Alert("請輸入至少兩個股票代碼（逗號分隔）。", color="warning")

    start = date.fromisoformat(start_date) if start_date else date.today() - timedelta(days=365)
    end = date.fromisoformat(end_date) if end_date else date.today()

    for sym in symbols:
        try:
            ensure_up_to_date(sym)
        except Exception as exc:
            logger.warning(f"[correlation] ensure_up_to_date({sym}) failed: {exc}")

    results = []

    # ── Correlation matrix heatmap ────────────────────────────────────────────
    try:
        corr_df = calc_corr(symbols, start, end)
    except Exception as exc:
        logger.error(f"[correlation] calc_corr failed: {exc}")
        corr_df = None

    if corr_df is not None and not corr_df.empty:
        text_vals = [[f"{v:.3f}" for v in row] for row in corr_df.values]
        fig_heat = go.Figure(
            go.Heatmap(
                z=corr_df.values,
                x=corr_df.columns.tolist(),
                y=corr_df.index.tolist(),
                text=text_vals,
                texttemplate="%{text}",
                colorscale="RdBu",
                zmid=0, zmin=-1, zmax=1,
                colorbar=dict(title="相關係數", tickfont=dict(color="#dee2e6")),
            )
        )
        fig_heat.update_layout(
            **_CHART_LAYOUT,
            title=dict(
                text="相關係數矩陣（日報酬率 Pearson）",
                font=dict(color="#dee2e6", size=13), x=0,
            ),
            height=max(350, 80 * len(corr_df)),
            xaxis=dict(side="bottom"),
        )
        results.append(_wrap_graph(fig_heat))
    else:
        results.append(
            dbc.Alert("無法計算相關係數矩陣，請確認資料是否存在。", color="warning")
        )

    # ── Rolling correlation ───────────────────────────────────────────────────
    ra = (roll_a or "").strip().upper()
    rb = (roll_b or "").strip().upper()
    win = int(window) if window else 30

    if ra and rb:
        try:
            rolling_df = rolling_correlation(ra, rb, window=win, start_date=start, end_date=end)
        except Exception as exc:
            logger.error(f"[correlation] rolling_correlation failed: {exc}")
            rolling_df = None

        if rolling_df is not None and not rolling_df.empty:
            fig_roll = go.Figure(
                go.Scatter(
                    x=rolling_df.index.astype(str),
                    y=rolling_df["correlation"],
                    mode="lines",
                    name=f"滾動相關性 ({win} 日)",
                    line=dict(color="#3498db", width=1.8),
                    fill="tozeroy",
                    fillcolor="rgba(52,152,219,0.08)",
                    hovertemplate="%{x}<br>相關係數: %{y:.3f}<extra></extra>",
                )
            )
            fig_roll.add_hline(y=0, line_dash="dot", line_color="rgba(255,255,255,0.25)")
            fig_roll.add_hline(y=0.7, line_dash="dot", line_color="rgba(0,188,140,0.35)")
            fig_roll.add_hline(y=-0.7, line_dash="dot", line_color="rgba(231,76,60,0.35)")
            fig_roll.update_layout(
                **_CHART_LAYOUT,
                title=dict(
                    text=f"{ra}  vs  {rb}  滾動相關性（{win} 日窗口）",
                    font=dict(color="#dee2e6", size=13), x=0,
                ),
                yaxis=dict(
                    title="相關係數", range=[-1.05, 1.05],
                    gridcolor="#3a3f55", zeroline=False,
                ),
                xaxis=dict(gridcolor="#3a3f55"),
                height=360,
            )
            results.append(_wrap_graph(fig_roll))
        else:
            results.append(
                dbc.Alert(
                    f"無法計算 {ra} vs {rb} 的滾動相關性（資料不足 {win} 天）。",
                    color="warning",
                )
            )

    return html.Div(results) if results else dbc.Alert("無結果。", color="secondary")
