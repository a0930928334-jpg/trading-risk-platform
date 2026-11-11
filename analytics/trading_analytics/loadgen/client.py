"""Minimal REST client for the trading API, standard library only."""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any


class ApiError(Exception):
    def __init__(self, status: int, body: dict[str, Any] | str):
        super().__init__(f"HTTP {status}: {body}")
        self.status = status
        self.body = body


@dataclass
class Session:
    base_url: str
    token: str | None = None
    account_id: int | None = None

    def request(self, method: str, path: str, body: dict[str, Any] | None = None) -> Any:
        data = json.dumps(body).encode("utf-8") if body is not None else None
        request = urllib.request.Request(self.base_url + path, data=data, method=method)
        request.add_header("Content-Type", "application/json")
        request.add_header("Accept", "application/json")
        if self.token:
            request.add_header("Authorization", f"Bearer {self.token}")
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = response.read()
        except urllib.error.HTTPError as error:
            payload = error.read()
            try:
                parsed: dict[str, Any] | str = json.loads(payload)
            except json.JSONDecodeError:
                parsed = payload.decode("utf-8", errors="replace")
            raise ApiError(error.code, parsed) from None
        return json.loads(payload) if payload else None

    def register_and_login(self, email: str, password: str, full_name: str) -> None:
        self.request(
            "POST", "/api/auth/register", {"email": email, "password": password, "fullName": full_name}
        )
        login = self.request("POST", "/api/auth/login", {"email": email, "password": password})
        self.token = login["accessToken"]
        self.account_id = int(self.request("GET", "/api/me")["accountId"])

    def deposit(self, amount: float) -> None:
        self.request("POST", "/api/account/deposits", {"amount": amount})

    def instruments(self) -> list[dict[str, Any]]:
        return self.request("GET", "/api/instruments?size=100")["content"]

    def book(self, symbol: str, depth: int = 1) -> dict[str, Any]:
        return self.request("GET", f"/api/instruments/{symbol}/book?depth={depth}")

    def place(self, symbol: str, side: str, price: float, quantity: int) -> dict[str, Any]:
        return self.request(
            "POST", "/api/orders", {"symbol": symbol, "side": side, "price": price, "quantity": quantity}
        )

    def cancel(self, order_id: int) -> dict[str, Any] | None:
        try:
            return self.request("DELETE", f"/api/orders/{order_id}")
        except ApiError as error:
            # 409: the order filled or was cancelled by self-trade prevention in the meantime.
            if error.status == 409:
                return None
            raise

    def open_orders(self) -> list[dict[str, Any]]:
        new = self.request("GET", "/api/orders?status=NEW&size=100")["content"]
        partial = self.request("GET", "/api/orders?status=PARTIALLY_FILLED&size=100")["content"]
        return [*new, *partial]

    def positions(self) -> dict[str, int]:
        return {
            position["symbol"]: int(position["availableQuantity"])
            for position in self.request("GET", "/api/positions?size=100")["content"]
        }

    def available_cash(self) -> float:
        return float(self.request("GET", "/api/account")["available"])


def wait_for_health(base_url: str, attempts: int, delay_seconds: float) -> None:
    import time

    last_error: Exception | None = None
    for _ in range(attempts):
        try:
            with urllib.request.urlopen(base_url + "/actuator/health", timeout=5) as response:
                if json.loads(response.read()).get("status") == "UP":
                    return
        except (urllib.error.URLError, json.JSONDecodeError, TimeoutError) as error:
            last_error = error
        time.sleep(delay_seconds)
    raise RuntimeError(f"Application at {base_url} did not become healthy: {last_error}")
