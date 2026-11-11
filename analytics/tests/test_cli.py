"""End to end through the command line: report files are written and the exit code reflects the checks."""

from __future__ import annotations

import json

from sqlalchemy import text

from tests.conftest import TEST_DB_NAME
from trading_analytics import cli
from trading_analytics.checks import CHECK_NAMES


def _point_cli_at_test_database(monkeypatch) -> None:
    monkeypatch.setenv("TRADING_DB_NAME", TEST_DB_NAME)
    monkeypatch.setenv("TRADING_MARKET_MAKER_SHARES_PER_INSTRUMENT", "1000")


def test_run_writes_report_and_summary_and_exits_zero(write_engine, scenario, monkeypatch, tmp_path, capsys):
    _point_cli_at_test_database(monkeypatch)
    out = tmp_path / "report"

    exit_code = cli.main(["run", "--out", str(out)])

    assert exit_code == cli.EXIT_OK
    html = (out / "report.html").read_text(encoding="utf-8")
    summary = json.loads((out / "summary.json").read_text(encoding="utf-8"))
    assert summary["passed"] is True
    assert [check["name"] for check in summary["checks"]] == list(CHECK_NAMES)
    assert summary["row_counts"]["orders"] == 8
    assert summary["metrics"]["overview"]["trades"] == 2
    assert summary["anomalies"]["accounts_scored"] == 2  # Alice and Bob; the market maker is excluded
    for name in CHECK_NAMES:
        assert name in html
    assert "data:image/png;base64," in html
    assert "All checks passed" in html
    assert "PASS ledger_replay" in capsys.readouterr().out


def test_run_exits_non_zero_when_a_check_fails(write_engine, scenario, monkeypatch, tmp_path):
    _point_cli_at_test_database(monkeypatch)
    with write_engine.connect() as connection:
        connection.execute(text(f"UPDATE trades SET price = 200 WHERE id = {scenario.trade_alice_buys}"))
    out = tmp_path / "report"

    exit_code = cli.main(["run", "--out", str(out)])

    assert exit_code == cli.EXIT_CHECKS_FAILED
    summary = json.loads((out / "summary.json").read_text(encoding="utf-8"))
    failed = [check for check in summary["checks"] if not check["passed"]]
    assert [check["name"] for check in failed] == ["trades_are_valid"]
    assert failed[0]["findings"][0]["ids"]["trade_id"] == scenario.trade_alice_buys
    assert "Checks failed" in (out / "report.html").read_text(encoding="utf-8")


def test_check_command_prints_findings(write_engine, scenario, monkeypatch, capsys):
    _point_cli_at_test_database(monkeypatch)
    with write_engine.connect() as connection:
        connection.execute(text(f"UPDATE orders SET filled_quantity = 0 WHERE id = {scenario.mm_aapl_ask}"))

    exit_code = cli.main(["check"])

    assert exit_code == cli.EXIT_CHECKS_FAILED
    output = capsys.readouterr().out
    assert "FAIL order_fills_match_trades" in output
    assert f"Order {scenario.mm_aapl_ask}" in output
