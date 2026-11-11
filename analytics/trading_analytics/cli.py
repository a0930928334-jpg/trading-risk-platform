"""Command line entry point: python -m trading_analytics <command>."""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

from trading_analytics import anomaly, checks, metrics, report
from trading_analytics.config import DatabaseSettings, DomainSettings
from trading_analytics.db import make_engine
from trading_analytics.extract import extract

EXIT_OK = 0
EXIT_CHECKS_FAILED = 1
EXIT_ERROR = 2


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="python -m trading_analytics", description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    run = commands.add_parser(
        "run", help="run the checks, metrics and anomaly detection and write the report"
    )
    run.add_argument("--out", type=Path, required=True, help="directory for report.html and summary.json")
    run.add_argument("--labels", type=Path, help="labels file written by the load generator, for evaluation")

    check = commands.add_parser("check", help="run only the data quality checks and print the findings")
    check.add_argument("--max-findings", type=int, default=20, help="findings to print per failed check")

    loadgen = commands.add_parser(
        "loadgen", help="drive the REST API with normal and scripted anomalous accounts"
    )
    loadgen.add_argument("--base-url", default="http://localhost:8080")
    loadgen.add_argument("--seed", type=int, default=20261111)
    loadgen.add_argument("--normal", type=int, default=40, help="number of normal accounts")
    loadgen.add_argument("--anomalous", type=int, default=2, help="accounts per scripted anomaly type")
    loadgen.add_argument("--labels-out", type=Path, required=True, help="where to write the labels file")
    return parser


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    args = build_parser().parse_args(argv)
    if args.command == "loadgen":
        return _loadgen(args)
    if args.command == "check":
        return _check(args)
    return _run(args)


def _run(args: argparse.Namespace) -> int:
    settings = DatabaseSettings.from_env()
    domain = DomainSettings.from_env()
    dataset = extract(make_engine(settings))
    results = checks.run_all(dataset, domain)
    computed = metrics.compute(dataset)
    anomalies = anomaly.analyse(dataset, domain, args.labels)
    result = report.build(dataset, settings.name, results, computed, anomalies)
    html_path, json_path = report.write(result, args.out)
    _print_checks(results, max_findings=5)
    print(f"Report: {html_path}\nSummary: {json_path}")
    return EXIT_OK if result.passed else EXIT_CHECKS_FAILED


def _check(args: argparse.Namespace) -> int:
    settings = DatabaseSettings.from_env()
    dataset = extract(make_engine(settings))
    results = checks.run_all(dataset, DomainSettings.from_env())
    _print_checks(results, args.max_findings)
    return EXIT_OK if not checks.failed(results) else EXIT_CHECKS_FAILED


def _loadgen(args: argparse.Namespace) -> int:
    from trading_analytics import loadgen

    profile = loadgen.LoadProfile(
        seed=args.seed,
        normal=args.normal,
        cancel_burst=args.anomalous,
        risk_repeater=args.anomalous,
        notional_prober=args.anomalous,
    )
    document = loadgen.run(args.base_url, profile, args.labels_out)
    print(f"Labels: {args.labels_out} ({len(document['accounts'])} accounts, {document['totals']})")
    return EXIT_OK


def _print_checks(results: list[checks.CheckResult], max_findings: int) -> None:
    for result in results:
        verdict = "PASS" if result.passed else "FAIL"
        print(
            f"{verdict} {result.name} ({result.severity}, {result.rows_checked} rows, "
            f"{len(result.findings)} findings)"
        )
        for finding in result.findings[:max_findings]:
            print(f"     {finding.message}")
        if len(result.findings) > max_findings:
            print(f"     ... {len(result.findings) - max_findings} more")


if __name__ == "__main__":
    sys.exit(main())
