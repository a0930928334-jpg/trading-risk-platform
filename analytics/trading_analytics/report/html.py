"""Self-contained HTML report: inline CSS, inline PNG charts, no external resources."""

from __future__ import annotations

from html import escape
from typing import TYPE_CHECKING, Any

import pandas as pd

from trading_analytics.anomaly.features import FEATURE_DESCRIPTIONS
from trading_analytics.report import charts

if TYPE_CHECKING:
    from trading_analytics.report import RunResult

MAX_FINDINGS_SHOWN = 25
MAX_ACCOUNTS_SHOWN = 15

STYLE = """
:root { color-scheme: light; }
body { font-family: -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; color: #0b0b0b;
       background: #fcfcfb;
       margin: 0; padding: 24px 16px; line-height: 1.45; }
main { max-width: 1040px; margin: 0 auto; }
h1 { font-size: 24px; margin: 0 0 4px; }
h2 { font-size: 18px; margin: 36px 0 8px; border-bottom: 1px solid #e4e3df; padding-bottom: 4px; }
h3 { font-size: 14px; margin: 20px 0 6px; color: #52514e; font-weight: 600; }
p, li { font-size: 14px; }
.meta { color: #52514e; font-size: 13px; }
.status { display: inline-block; padding: 2px 10px; border-radius: 4px; font-weight: 600; font-size: 13px; }
.pass { background: #e3f4ec; color: #0b5c38; }
.fail { background: #fbe3e3; color: #8f1d1d; }
.tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px;
         margin: 16px 0; }
.tile { background: #ffffff; border: 1px solid #e4e3df; border-radius: 6px; padding: 10px 12px; }
.tile .value { font-size: 22px; font-weight: 600; }
.tile .label { font-size: 12px; color: #52514e; }
table { border-collapse: collapse; width: 100%; font-size: 13px; margin: 8px 0 16px; background: #ffffff; }
th, td { text-align: left; padding: 5px 8px; border-bottom: 1px solid #e4e3df; vertical-align: top; }
th { color: #52514e; font-weight: 600; background: #f5f5f3; }
td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; }
.flag { color: #8f1d1d; font-weight: 600; }
figure { margin: 12px 0 20px; }
figure img { max-width: 100%; height: auto; border: 1px solid #e4e3df; border-radius: 6px;
             background: #ffffff; }
code { font-size: 12px; background: #f5f5f3; padding: 1px 4px; border-radius: 3px; }
.wrap { overflow-x: auto; }
"""


def render(result: RunResult) -> str:
    sections = [
        _header(result),
        _checks(result),
        _metrics(result),
        _anomalies(result),
    ]
    body = "\n".join(sections)
    return (
        '<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
        "<title>Trading analytics report</title>\n"
        f"<style>{STYLE}</style>\n</head>\n<body>\n<main>\n{body}\n</main>\n</body>\n</html>\n"
    )


def _header(result: RunResult) -> str:
    status = (
        '<span class="status pass">All checks passed</span>'
        if result.passed
        else ('<span class="status fail">Checks failed</span>')
    )
    failed = sum(not check.passed for check in result.checks)
    counts = result.row_counts
    tiles = [
        ("Checks failed", f"{failed} / {len(result.checks)}"),
        ("Orders", f"{counts['orders']:,}"),
        ("Trades", f"{counts['trades']:,}"),
        ("Ledger entries", f"{counts['cash_ledger_entries']:,}"),
        ("Accounts", f"{counts['accounts']:,}"),
        (
            "Accounts flagged",
            f"{int((result.anomalies.baseline.flagged | result.anomalies.forest.flagged).sum())}",
        ),
    ]
    return (
        "<h1>Trading analytics report</h1>\n"
        f'<p class="meta">Generated {escape(result.generated_at.strftime("%Y-%m-%d %H:%M:%S UTC"))} '
        f"from database <code>{escape(result.database)}</code>. {status}</p>\n" + _tiles(tiles)
    )


def _tiles(items: list[tuple[str, str]]) -> str:
    cells = "".join(
        f'<div class="tile"><div class="value">{escape(value)}</div>'
        f'<div class="label">{escape(label)}</div></div>'
        for label, value in items
    )
    return f'<div class="tiles">{cells}</div>'


def _checks(result: RunResult) -> str:
    rows = []
    for check in result.checks:
        status = (
            '<span class="status pass">pass</span>'
            if check.passed
            else '<span class="status fail">fail</span>'
        )
        rows.append(
            "<tr>"
            f"<td><code>{escape(check.name)}</code></td>"
            f"<td>{escape(check.description)}</td>"
            f"<td>{escape(str(check.severity))}</td>"
            f'<td class="num">{check.rows_checked:,}</td>'
            f'<td class="num">{len(check.findings):,}</td>'
            f"<td>{status}</td>"
            "</tr>"
        )
    table = (
        '<div class="wrap"><table><thead><tr><th>Check</th><th>Invariant</th><th>Severity</th>'
        '<th class="num">Rows</th><th class="num">Findings</th><th>Result</th></tr></thead>'
        f"<tbody>{''.join(rows)}</tbody></table></div>"
    )
    details = []
    for check in result.checks:
        if check.passed:
            continue
        finding_rows = "".join(
            "<tr>"
            f"<td>{escape(finding.entity)}</td>"
            f"<td>{escape(', '.join(f'{key}={value}' for key, value in finding.ids.items()))}</td>"
            f"<td><code>{escape(finding.field)}</code></td>"
            f"<td>{escape(str(finding.expected))}</td>"
            f"<td>{escape(str(finding.actual))}</td>"
            f"<td>{escape(finding.message)}</td>"
            "</tr>"
            for finding in check.findings[:MAX_FINDINGS_SHOWN]
        )
        more = (
            f'<p class="meta">Showing {MAX_FINDINGS_SHOWN} of {len(check.findings)} findings; '
            "the JSON summary has up to 200.</p>"
            if len(check.findings) > MAX_FINDINGS_SHOWN
            else ""
        )
        details.append(
            f"<h3>{escape(check.name)}: {len(check.findings)} finding(s)</h3>"
            '<div class="wrap"><table><thead><tr><th>Entity</th><th>Identifiers</th><th>Field</th>'
            f"<th>Expected</th><th>Actual</th><th>Detail</th></tr></thead><tbody>{finding_rows}</tbody></table></div>{more}"
        )
    return "<h2>Data quality checks</h2>\n" + table + "\n".join(details)


def _metrics(result: RunResult) -> str:
    metrics = result.metrics
    overview = metrics.overview
    tiles = [
        ("Reject rate", _pct(overview["reject_rate"])),
        ("Fill rate (accepted orders fully filled)", _pct(overview["fill_rate"])),
        ("Traded rate (accepted orders with a fill)", _pct(overview["traded_rate"])),
        ("User cancel rate", _pct(overview["user_cancel_rate"])),
        ("Self-trade cancellations", f"{overview['self_trade_cancellations']:,}"),
        ("Open orders", f"{overview['open_orders']:,}"),
        ("Traded shares", f"{overview['traded_shares']:,}"),
        ("Traded value (USD)", f"{overview['traded_notional']:,.0f}"),
    ]
    parts = ["<h2>Quality metrics</h2>", _tiles(tiles)]
    parts.append(
        _figure(
            charts.orders_over_time(metrics.over_time, metrics.bucket),
            "Orders, trades, rejections and user cancellations per time bucket",
        )
    )
    parts.append("<h3>By instrument</h3>")
    parts.append(
        _table(
            metrics.by_instrument,
            {
                "symbol": "Symbol",
                "orders": "Orders",
                "reject_rate": "Reject rate",
                "fill_rate": "Fill rate",
                "user_cancel_rate": "User cancel rate",
                "open_orders": "Open",
                "trades": "Trades",
                "traded_shares": "Shares traded",
                "traded_notional": "Value traded",
            },
            percent={"reject_rate", "fill_rate", "user_cancel_rate"},
        )
    )
    parts.append(
        _figure(
            charts.rates_by_instrument(metrics.by_instrument),
            "Reject, fill and user cancel rates by instrument",
        )
    )
    parts.append("<h3>Rejections by risk rule</h3>")
    parts.append(
        _table(
            metrics.by_reject_code,
            {
                "reject_code": "Reject code",
                "orders": "Orders",
                "share_of_rejections": "Share of rejections",
                "rate_of_all_orders": "Rate of all orders",
            },
            percent={"share_of_rejections", "rate_of_all_orders"},
        )
    )
    parts.append(_figure(charts.rejections_by_rule(metrics.by_reject_code), "Rejected orders per risk rule"))
    parts.append("<h3>Time from placement to first fill</h3>")
    parts.append(
        _table(
            metrics.time_to_first_fill,
            {
                "role": "Role",
                "orders": "Orders that traded",
                "p50_seconds": "p50 (s)",
                "p90_seconds": "p90 (s)",
                "max_seconds": "max (s)",
            },
        )
    )
    parts.append("<h3>Accounts with the most orders per trade</h3>")
    by_account = metrics.by_account[~metrics.by_account["system_account"]]
    by_account = by_account.sort_values(["orders_per_trade", "orders"], ascending=False).head(
        MAX_ACCOUNTS_SHOWN
    )
    parts.append(
        _table(
            by_account,
            {
                "account_id": "Account",
                "orders": "Orders",
                "accepted": "Accepted",
                "rejected": "Rejected",
                "user_cancelled": "User cancelled",
                "trades": "Trades",
                "orders_per_trade": "Accepted orders per trade",
                "reject_rate": "Reject rate",
                "user_cancel_rate": "User cancel rate",
            },
            percent={"reject_rate", "user_cancel_rate"},
        )
    )
    return "\n".join(parts)


def _anomalies(result: RunResult) -> str:
    anomalies = result.anomalies
    table = anomalies.table()
    parts = ["<h2>Anomaly detection</h2>"]
    parts.append(
        "<p>Per-account features are scored by two detectors: a robust z-score baseline (median and MAD, "
        f"threshold {anomalies.baseline.threshold}) and scikit-learn's IsolationForest "
        f"(contamination {anomalies.forest.contamination}, fixed random seed). "
        "System accounts are excluded.</p>"
    )
    if table.empty:
        parts.append("<p>No user accounts have placed orders, so there is nothing to score.</p>")
        return "\n".join(parts)

    if anomalies.evaluation:
        rows = []
        for name, scores in anomalies.evaluation.items():
            rows.append(
                "<tr>"
                f"<td>{escape(name)}</td>"
                f'<td class="num">{scores["flagged_accounts"]}</td>'
                f'<td class="num">{scores["true_positives"]}</td>'
                f'<td class="num">{scores["false_positives"]}</td>'
                f'<td class="num">{scores["false_negatives"]}</td>'
                f'<td class="num">{scores["precision"]:.2f}</td>'
                f'<td class="num">{scores["recall"]:.2f}</td>'
                f'<td class="num">{scores["f1"]:.2f}</td>'
                "</tr>"
            )
        first = next(iter(anomalies.evaluation.values()))
        parts.append(
            f"<h3>Evaluation against {first['labelled_accounts']} labelled accounts "
            f"({first['anomalous_accounts']} scripted anomalies)</h3>"
            '<div class="wrap"><table><thead><tr><th>Detector</th><th class="num">Flagged</th>'
            '<th class="num">True positives</th><th class="num">False positives</th>'
            '<th class="num">False negatives</th><th class="num">Precision</th><th class="num">Recall</th>'
            f'<th class="num">F1</th></tr></thead><tbody>{"".join(rows)}</tbody></table></div>'
        )
        type_rows = []
        for name, scores in anomalies.evaluation.items():
            for label, counts in scores["recall_by_type"].items():
                type_rows.append(
                    f"<tr><td>{escape(name)}</td><td>{escape(label)}</td>"
                    f'<td class="num">{counts["caught"]} / {counts["accounts"]}</td></tr>'
                )
        if type_rows:
            parts.append(
                '<div class="wrap"><table><thead><tr><th>Detector</th><th>Scripted anomaly</th>'
                f'<th class="num">Caught</th></tr></thead><tbody>{"".join(type_rows)}</tbody></table></div>'
            )

    parts.append(
        _figure(
            charts.anomaly_scatter(table), "Accounts by user cancel rate and reject rate, by detector verdict"
        )
    )

    flagged = table[table["baseline_flag"] | table["isolation_flag"]].copy()
    flagged["baseline_score"] = flagged["baseline_score"].round(1)
    flagged["isolation_score"] = flagged["isolation_score"].round(3)
    flagged = flagged.sort_values("baseline_score", ascending=False)
    flagged["top_features"] = [
        ", ".join(f"{name} ({score:.1f})" for name, score in anomalies.baseline.top_features(int(account_id)))
        for account_id in flagged["account_id"]
    ]
    parts.append(f"<h3>Flagged accounts ({len(flagged)})</h3>")
    parts.append(
        _table(
            flagged,
            {
                "account_id": "Account",
                "label": "Label",
                "baseline_flag": "Baseline",
                "isolation_flag": "IsolationForest",
                "baseline_score": "Max |robust z|",
                "isolation_score": "Isolation score",
                "top_features": "Most deviating features (robust z)",
                "orders": "Orders",
                "reject_rate": "Reject rate",
                "user_cancel_rate": "User cancel rate",
                "orders_per_trade": "Orders per trade",
                "max_notional_ratio": "Max value / cap",
            },
            percent={"reject_rate", "user_cancel_rate"},
        )
    )
    parts.append("<h3>Features</h3><ul>")
    for name, description in FEATURE_DESCRIPTIONS.items():
        parts.append(f"<li><code>{escape(name)}</code>: {escape(description)}</li>")
    parts.append("</ul>")
    return "\n".join(parts)


def _figure(image: str | None, caption: str) -> str:
    if image is None:
        return ""
    return f'<figure><img src="data:image/png;base64,{image}" alt="{escape(caption)}"></figure>'


def _table(frame: pd.DataFrame, columns: dict[str, str], percent: set[str] | None = None) -> str:
    percent = percent or set()
    if frame.empty:
        return '<p class="meta">No rows.</p>'
    present = [column for column in columns if column in frame.columns]
    head = "".join(
        f'<th class="{"num" if _numeric(frame[column]) else ""}">{escape(columns[column])}</th>'
        for column in present
    )
    rows = []
    for record in frame[present].itertuples(index=False):
        cells = []
        for column, value in zip(present, record, strict=True):
            numeric = _numeric(frame[column])
            text = _format(value, column in percent)
            css = ' class="num"' if numeric else ""
            if isinstance(value, bool | pd.BooleanDtype) and value:
                text = f'<span class="flag">{text}</span>'
            cells.append(f"<td{css}>{text}</td>")
        rows.append(f"<tr>{''.join(cells)}</tr>")
    return (
        f'<div class="wrap"><table><thead><tr>{head}</tr></thead><tbody>{"".join(rows)}</tbody></table></div>'
    )


def _numeric(series: pd.Series) -> bool:
    return pd.api.types.is_numeric_dtype(series) and not pd.api.types.is_bool_dtype(series)


def _format(value: Any, as_percent: bool) -> str:
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return "-"
    if isinstance(value, bool):
        return "yes" if value else "no"
    if as_percent:
        return f"{float(value) * 100:.1f}%"
    if isinstance(value, float):
        if value.is_integer():
            return f"{int(value):,}"
        return f"{value:,.2f}" if abs(value) < 1000 else f"{value:,.0f}"
    if isinstance(value, int) or hasattr(value, "item"):
        return f"{int(value):,}"
    if isinstance(value, pd.Timestamp):
        return value.strftime("%Y-%m-%d %H:%M")
    return escape(str(value))


def _pct(value: float) -> str:
    return f"{value * 100:.1f}%"
