"""Dash application entry point — sidebar layout with global symbol store."""

import atexit

import dash
import dash_bootstrap_components as dbc
from dash import Input, Output, State, dcc, html
from loguru import logger
from sqlalchemy import select

from collector.price import ensure_up_to_date
from collector.scheduler import init_scheduler, shutdown_scheduler
from db.models import StockInfo, StockPrice
from db.session import check_connection, get_session, init_db

# ---------------------------------------------------------------------------
# App
# ---------------------------------------------------------------------------

app = dash.Dash(
    __name__,
    use_pages=True,
    external_stylesheets=[dbc.themes.DARKLY],
    suppress_callback_exceptions=True,
)

# ---------------------------------------------------------------------------
# Styles
# ---------------------------------------------------------------------------

_SIDEBAR_W = "220px"

_SIDEBAR_STYLE = {
    "position": "fixed",
    "top": "0",
    "left": "0",
    "bottom": "0",
    "width": _SIDEBAR_W,
    "backgroundColor": "#2b2f3e",
    "borderRight": "1px solid #3a3f55",
    "zIndex": "200",
    "overflowY": "auto",
    "display": "flex",
    "flexDirection": "column",
}

_CONTENT_STYLE = {
    "marginLeft": _SIDEBAR_W,
    "minHeight": "100vh",
    "display": "flex",
    "flexDirection": "column",
    "backgroundColor": "#222",
}

_HEADER_STYLE = {
    "backgroundColor": "#2b2f3e",
    "padding": "0.55rem 1.25rem",
    "borderBottom": "1px solid #3a3f55",
    "position": "sticky",
    "top": "0",
    "zIndex": "150",
}

# ---------------------------------------------------------------------------
# Navigation items
# ---------------------------------------------------------------------------

_NAV_ITEMS = [
    ("🏠  首頁", "/"),
    ("📈  技術分析", "/technical"),
    ("📊  基本面", "/fundamental"),
    ("🔗  相關性", "/correlation"),
    ("⚡  回測", "/backtest"),
]

# ---------------------------------------------------------------------------
# Sidebar
# ---------------------------------------------------------------------------

_sidebar = html.Div(
    [
        # Logo / brand
        html.Div(
            [
                html.Div("📈", style={"fontSize": "2rem", "lineHeight": "1"}),
                html.H6("股票分析系統", className="text-white mb-0 mt-1 fw-bold"),
                html.Small("Stock Dashboard", className="text-muted"),
            ],
            className="text-center py-3 px-2",
            style={"borderBottom": "1px solid #3a3f55"},
        ),
        # Nav links
        html.Div(
            dbc.Nav(
                [
                    dbc.NavLink(
                        label,
                        href=href,
                        active="exact",
                        style={"color": "#adb5bd", "padding": "0.65rem 0.9rem",
                               "borderRadius": "0.4rem", "marginBottom": "2px"},
                    )
                    for label, href in _NAV_ITEMS
                ],
                vertical=True,
                pills=True,
                className="px-2 mt-2",
            ),
        ),
        # Spacer
        html.Div(style={"flex": "1"}),
        # Current symbol badge
        html.Div(
            id="sidebar-symbol-display",
            style={
                "borderTop": "1px solid #3a3f55",
                "padding": "0.75rem 1rem",
            },
        ),
    ],
    style=_SIDEBAR_STYLE,
)

# ---------------------------------------------------------------------------
# Header
# ---------------------------------------------------------------------------

_header = html.Div(
    dbc.Row(
        [
            # Search box + button (centre)
            dbc.Col(
                dbc.InputGroup(
                    [
                        dcc.Input(
                            id="header-symbol-input",
                            type="text",
                            placeholder="輸入股票代碼  AAPL / 2330.TW / 7203.T …",
                            n_submit=0,
                            debounce=False,
                            className="form-control",
                            style={
                                "backgroundColor": "#1e2130",
                                "border": "1px solid #4a4f6a",
                                "color": "#f8f9fa",
                                "borderRight": "none",
                            },
                        ),
                        dbc.Button(
                            "搜尋",
                            id="header-search-btn",
                            color="primary",
                            size="sm",
                        ),
                    ],
                ),
                md=6,
                lg=5,
            ),
            # Price display (right)
            dbc.Col(
                dbc.Spinner(
                    html.Div(id="header-price-display"),
                    color="light",
                    size="sm",
                ),
                className="d-flex align-items-center justify-content-end",
            ),
        ],
        align="center",
        className="g-2",
    ),
    style=_HEADER_STYLE,
)

# ---------------------------------------------------------------------------
# Root layout
# ---------------------------------------------------------------------------

app.layout = html.Div(
    [
        dcc.Store(id="global-symbol-store", storage_type="session"),
        _sidebar,
        html.Div(
            [
                _header,
                html.Div(dash.page_container, style={"padding": "1.5rem", "flex": "1"}),
            ],
            style=_CONTENT_STYLE,
        ),
    ],
    style={"backgroundColor": "#222"},
)

# ---------------------------------------------------------------------------
# App-level callbacks
# ---------------------------------------------------------------------------


def _fetch_price_vals(symbol: str) -> dict | None:
    """Return plain-value dict of latest + prev price inside a session."""
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

            if _lat is None:
                return None

            return {
                "close": _lat.close,
                "prev_close": _prev.close if _prev else _lat.close,
                "name": (_info.name if _info else None) or symbol,
                "currency": (_info.currency if _info else None) or "",
            }
    except Exception as exc:
        logger.error(f"[header] DB fetch failed for {symbol}: {exc}")
        return None


def _price_display(symbol: str, vals: dict):
    close = vals["close"] or 0.0
    prev = vals["prev_close"] or close
    change = close - prev
    pct = (change / prev * 100) if prev else 0.0
    color = "success" if change >= 0 else "danger"
    arrow = "▲" if change >= 0 else "▼"
    return html.Div(
        [
            html.Span(f"{vals['name']}  ", className="text-muted small"),
            html.Span(
                f"{close:,.2f} {vals['currency']}",
                className="text-white fw-bold mx-2",
            ),
            html.Span(
                f"{arrow} {abs(pct):.2f}%",
                className=f"text-{color} small fw-semibold",
            ),
        ],
        className="d-flex align-items-center justify-content-end gap-1",
    )


@app.callback(
    Output("global-symbol-store", "data"),
    Output("header-price-display", "children"),
    Input("header-search-btn", "n_clicks"),
    Input("header-symbol-input", "n_submit"),
    State("header-symbol-input", "value"),
    prevent_initial_call=True,
)
def handle_search(n_clicks, n_submit, raw):
    if not raw or not raw.strip():
        return dash.no_update, dash.no_update

    symbol = raw.strip().upper()

    try:
        ensure_up_to_date(symbol)
    except Exception as exc:
        logger.warning(f"[header] ensure_up_to_date({symbol}) failed: {exc}")

    vals = _fetch_price_vals(symbol)
    if vals is None:
        return {"symbol": symbol}, html.Span(
            f"找不到 {symbol} 的資料", className="text-warning small"
        )

    return {"symbol": symbol}, _price_display(symbol, vals)


@app.callback(
    Output("sidebar-symbol-display", "children"),
    Input("global-symbol-store", "data"),
)
def update_sidebar_symbol(data):
    symbol = (data or {}).get("symbol")
    if not symbol:
        return html.P("尚未選擇股票", className="text-muted small text-center mb-0")
    return html.Div(
        [
            html.P("當前股票", className="text-muted small mb-1"),
            html.Div(
                symbol,
                className="text-center fw-bold",
                style={
                    "color": "#375a7f",
                    "backgroundColor": "#1e2130",
                    "borderRadius": "0.4rem",
                    "padding": "0.35rem 0.5rem",
                    "fontSize": "1rem",
                    "letterSpacing": "0.05em",
                },
            ),
        ]
    )


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    if check_connection():
        logger.info("Database connection OK.")
        init_db()
    else:
        logger.warning("Database unreachable — running without DB.")

    import os
    if not os.environ.get("WERKZEUG_RUN_MAIN"):
        init_scheduler()
        atexit.register(shutdown_scheduler)

    app.run(host="0.0.0.0", port=8050, debug=True, use_reloader=True)
