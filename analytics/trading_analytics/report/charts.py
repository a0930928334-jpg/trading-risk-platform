"""Matplotlib charts rendered to base64 PNG so the HTML report is a single file."""

from __future__ import annotations

import base64
import io

import matplotlib
import pandas as pd

matplotlib.use("Agg")

import matplotlib.pyplot as plt

# Fixed categorical order; colour identifies a series and never changes with the data.
SERIES = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300"]
STATUS_BAD = "#e34948"
TEXT = "#0b0b0b"
MUTED = "#52514e"
GRID = "#e4e3df"


def _figure(width: float = 8.0, height: float = 3.2) -> tuple[plt.Figure, plt.Axes]:
    figure, axes = plt.subplots(figsize=(width, height), dpi=110)
    axes.spines[["top", "right"]].set_visible(False)
    axes.spines[["left", "bottom"]].set_color(GRID)
    axes.tick_params(colors=MUTED, labelsize=9)
    axes.yaxis.grid(True, color=GRID, linewidth=0.8)
    axes.set_axisbelow(True)
    return figure, axes


def _encode(figure: plt.Figure) -> str:
    buffer = io.BytesIO()
    figure.tight_layout()
    figure.savefig(buffer, format="png")
    plt.close(figure)
    return base64.b64encode(buffer.getvalue()).decode("ascii")


def orders_over_time(over_time: pd.DataFrame, bucket: str) -> str | None:
    if over_time.empty:
        return None
    figure, axes = _figure()
    x = pd.to_datetime(over_time["bucket"])
    axes.plot(x, over_time["orders"], color=SERIES[0], linewidth=2, label="Orders")
    axes.plot(x, over_time["trades"], color=SERIES[2], linewidth=2, label="Trades")
    axes.plot(x, over_time["rejected"], color=SERIES[1], linewidth=2, label="Rejected")
    axes.plot(x, over_time["user_cancelled"], color=SERIES[3], linewidth=2, label="Cancelled by user")
    axes.set_ylabel(f"Per {bucket}", color=MUTED, fontsize=9)
    axes.set_title("Order flow over time", loc="left", color=TEXT, fontsize=11)
    axes.legend(frameon=False, fontsize=9, ncol=4, loc="upper left", bbox_to_anchor=(0, -0.18))
    figure.autofmt_xdate()
    return _encode(figure)


def rates_by_instrument(by_instrument: pd.DataFrame) -> str | None:
    if by_instrument.empty:
        return None
    figure, axes = _figure(height=3.0)
    width = 0.26
    positions = range(len(by_instrument))
    series = [
        ("reject_rate", "Reject rate", SERIES[1]),
        ("fill_rate", "Fill rate", SERIES[2]),
        ("user_cancel_rate", "User cancel rate", SERIES[3]),
    ]
    for offset, (column, label, colour) in zip((-width, 0, width), series, strict=True):
        values = by_instrument[column].astype(float) * 100
        axes.bar([p + offset for p in positions], values, width=width - 0.03, color=colour, label=label)
    axes.set_xticks(list(positions), by_instrument["symbol"])
    axes.set_ylabel("Percent of orders", color=MUTED, fontsize=9)
    axes.set_title("Rates by instrument", loc="left", color=TEXT, fontsize=11)
    axes.legend(frameon=False, fontsize=9, ncol=3, loc="upper left", bbox_to_anchor=(0, -0.12))
    return _encode(figure)


def rejections_by_rule(by_reject_code: pd.DataFrame) -> str | None:
    if by_reject_code.empty:
        return None
    figure, axes = _figure(height=2.2 + 0.3 * len(by_reject_code))
    axes.barh(by_reject_code["reject_code"], by_reject_code["orders"], color=SERIES[1], height=0.55)
    axes.invert_yaxis()
    axes.xaxis.grid(True, color=GRID, linewidth=0.8)
    axes.yaxis.grid(False)
    axes.set_xlabel("Rejected orders", color=MUTED, fontsize=9)
    axes.set_title("Rejections by risk rule", loc="left", color=TEXT, fontsize=11)
    return _encode(figure)


def anomaly_scatter(table: pd.DataFrame) -> str | None:
    """Accounts by the two features the scripted anomalies move most, coloured by detector verdict."""
    if table.empty:
        return None
    figure, axes = _figure(width=7.0, height=4.2)
    axes.xaxis.grid(True, color=GRID, linewidth=0.8)
    both = table["baseline_flag"] & table["isolation_flag"]
    only_baseline = table["baseline_flag"] & ~table["isolation_flag"]
    only_forest = ~table["baseline_flag"] & table["isolation_flag"]
    neither = ~table["baseline_flag"] & ~table["isolation_flag"]
    groups = [
        (neither, "Not flagged", MUTED, "o"),
        (both, "Flagged by both", STATUS_BAD, "o"),
        (only_baseline, "Baseline only", SERIES[0], "s"),
        (only_forest, "IsolationForest only", SERIES[1], "^"),
    ]
    for mask, label, colour, marker in groups:
        subset = table[mask]
        if subset.empty:
            continue
        axes.scatter(
            subset["user_cancel_rate"] * 100,
            subset["reject_rate"] * 100,
            s=48,
            color=colour,
            marker=marker,
            label=label,
            edgecolors="white",
            linewidths=1,
        )
    # One annotation per scripted anomaly type, at its first account, with staggered offsets so that
    # neighbouring labels do not overlap.
    labelled = table.dropna(subset=["label"]) if "label" in table else table.iloc[0:0]
    offsets = [(6, 6), (6, -14), (6, 18)]
    for index, (label, group) in enumerate(
        labelled[labelled["label"] != "normal"].groupby("label", sort=True)
    ):
        first = group.iloc[0]
        axes.annotate(
            f"{label} ({len(group)})",
            (first["user_cancel_rate"] * 100, first["reject_rate"] * 100),
            textcoords="offset points",
            xytext=offsets[index % len(offsets)],
            fontsize=8,
            color=TEXT,
        )
    # Headroom so an annotation on the topmost point does not run into the title.
    axes.margins(x=0.08, y=0.15)
    axes.set_xlabel("User cancel rate, percent of accepted orders", color=MUTED, fontsize=9)
    axes.set_ylabel("Reject rate, percent of orders", color=MUTED, fontsize=9)
    axes.set_title("Accounts by cancel and reject rate", loc="left", color=TEXT, fontsize=11)
    axes.legend(frameon=False, fontsize=9, loc="upper left", bbox_to_anchor=(0, -0.14), ncol=2)
    return _encode(figure)
