"""A small, fully consistent trading history written straight into the test database.

The simulator below re-states the Java application's bookkeeping (OrderCommandService, CashService,
SettlementService): reserve on placement, settle each fill out of the reservation, release price
improvement at once, write one ledger entry per cash movement with balance_after and reserved_after
snapshots. The data quality checks are tested against this history, and the fault-injection tests
corrupt one row of it at a time.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta
from decimal import Decimal

from sqlalchemy import Connection, text

ZERO = Decimal("0.0000")


def money(value: str | int | Decimal) -> Decimal:
    return Decimal(value).quantize(Decimal("0.0001"))


@dataclass
class Account:
    id: int
    balance: Decimal = ZERO
    reserved: Decimal = ZERO
    # instrument_id -> (quantity, reserved_quantity)
    positions: dict[int, list[int]] = field(default_factory=dict)


@dataclass
class Order:
    id: int
    account_id: int
    instrument_id: int
    side: str
    price: Decimal
    quantity: int
    filled: int = 0
    status: str = "NEW"


@dataclass
class Scenario:
    """Identifiers the tests refer to, plus the final expected state."""

    MARKET_MAKER_SHARES = 1_000

    market_maker: int
    alice: int
    bob: int
    aapl: int
    msft: int
    nvda: int
    tsla: int
    # Orders
    mm_aapl_ask: int
    mm_aapl_bid: int
    mm_tsla_ask: int
    mm_tsla_bid: int
    alice_aapl_buy: int
    bob_msft_buy_cancelled: int
    bob_nvda_buy_rejected: int
    alice_aapl_sell: int
    # Trades
    trade_alice_buys: int
    trade_alice_sells: int
    # Ledger
    ledger_ids: dict[str, int]
    accounts: dict[str, Account]


class Simulator:
    def __init__(self, connection: Connection):
        self.connection = connection
        self.clock = datetime(2026, 11, 11, 9, 30, 0)
        self.accounts: dict[int, Account] = {}
        self.orders: dict[int, Order] = {}
        self.ledger_ids: dict[str, int] = {}

    def tick(self, seconds: int = 1) -> datetime:
        self.clock += timedelta(seconds=seconds)
        return self.clock

    def _scalar(self, sql: str, **params) -> int:
        return int(self.connection.execute(text(sql), params).scalar_one())

    def create_account(self, email: str, status: str, number: str) -> Account:
        user_id = self._scalar(
            "INSERT INTO app_users (email, password_hash, full_name, status, created_at, updated_at) "
            "VALUES (:email, 'x', :name, :status, :at, :at) RETURNING id",
            email=email,
            name=email.split("@")[0],
            status=status,
            at=self.tick(),
        )
        account_id = self._scalar(
            "INSERT INTO accounts (user_id, account_number, status, currency, created_at, updated_at) "
            "VALUES (:user_id, :number, 'ACTIVE', 'USD', :at, :at) RETURNING id",
            user_id=user_id,
            number=number,
            at=self.clock,
        )
        self.connection.execute(
            text(
                "INSERT INTO cash_balances (account_id, currency, balance, reserved, created_at, updated_at) "
                "VALUES (:account_id, 'USD', 0, 0, :at, :at)"
            ),
            {"account_id": account_id, "at": self.clock},
        )
        account = Account(account_id)
        self.accounts[account_id] = account
        return account

    def _ledger(
        self, account: Account, entry_type: str, amount: Decimal, order_id=None, trade_id=None
    ) -> int:
        return self._scalar(
            "INSERT INTO cash_ledger_entries (account_id, entry_type, currency, amount, balance_after, "
            "reserved_after, order_id, trade_id, created_at) VALUES (:account_id, :type, 'USD', :amount, "
            ":balance, :reserved, :order_id, :trade_id, :at) RETURNING id",
            account_id=account.id,
            type=entry_type,
            amount=amount,
            balance=account.balance,
            reserved=account.reserved,
            order_id=order_id,
            trade_id=trade_id,
            at=self.clock,
        )

    def deposit(self, account: Account, amount: str, label: str) -> None:
        self.tick()
        account.balance += money(amount)
        self.ledger_ids[label] = self._ledger(account, "DEPOSIT", money(amount))
        self._flush_cash(account)

    def grant_shares(self, account: Account, instrument_id: int, shares: int) -> None:
        """The market maker seeder credits shares without a trade (MarketMakerAccountSeeder)."""
        self.tick()
        account.positions[instrument_id] = [shares, 0]
        self._flush_position(account, instrument_id)

    def place(self, account: Account, instrument_id: int, side: str, price: str, quantity: int) -> Order:
        """Inserts an accepted order and takes its reservation; fills are applied separately."""
        self.tick()
        limit = money(price)
        order_id = self._insert_order(account, instrument_id, side, limit, quantity, "NEW", None, None)
        order = Order(order_id, account.id, instrument_id, side, limit, quantity)
        self.orders[order_id] = order
        if side == "BUY":
            account.reserved += limit * quantity
            self._ledger(account, "RESERVE", limit * quantity, order_id=order_id)
            self._flush_cash(account)
        else:
            position = account.positions.setdefault(instrument_id, [0, 0])
            position[1] += quantity
            self._flush_position(account, instrument_id)
        return order

    def reject(
        self, account: Account, instrument_id: int, side: str, price: str, quantity: int, code: str
    ) -> Order:
        self.tick()
        order_id = self._insert_order(
            account, instrument_id, side, money(price), quantity, "REJECTED", code, f"Rejected by {code}"
        )
        order = Order(order_id, account.id, instrument_id, side, money(price), quantity, status="REJECTED")
        self.orders[order_id] = order
        return order

    def fill(self, taker: Order, maker: Order, quantity: int) -> int:
        """Settles one fill at the maker's price, the way SettlementService does."""
        self.tick()
        price = maker.price
        buy, sell = (taker, maker) if taker.side == "BUY" else (maker, taker)
        buyer, seller = self.accounts[buy.account_id], self.accounts[sell.account_id]
        trade_id = self._scalar(
            "INSERT INTO trades (instrument_id, price, quantity, buy_order_id, sell_order_id, "
            "buyer_account_id, seller_account_id, aggressor_side, created_at) "
            "VALUES (:instrument_id, :price, :quantity, :buy, :sell, :buyer, :seller, :aggressor, :at) "
            "RETURNING id",
            instrument_id=taker.instrument_id,
            price=price,
            quantity=quantity,
            buy=buy.id,
            sell=sell.id,
            buyer=buyer.id,
            seller=seller.id,
            aggressor=taker.side,
            at=self.clock,
        )
        for order in (taker, maker):
            order.filled += quantity
            order.status = "FILLED" if order.filled == order.quantity else "PARTIALLY_FILLED"
            self._flush_order(order)

        value = money(price * quantity)
        buyer.reserved -= value
        buyer.balance -= value
        self._ledger(buyer, "TRADE_DEBIT", value, order_id=buy.id, trade_id=trade_id)
        improvement = money(buy.price * quantity) - value
        if improvement > 0:
            buyer.reserved -= improvement
            self._ledger(buyer, "RELEASE", improvement, order_id=buy.id)
        self._flush_cash(buyer)
        buyer.positions.setdefault(taker.instrument_id, [0, 0])[0] += quantity
        self._flush_position(buyer, taker.instrument_id)

        seller_position = seller.positions[taker.instrument_id]
        seller_position[0] -= quantity
        seller_position[1] -= quantity
        self._flush_position(seller, taker.instrument_id)
        seller.balance += value
        self._ledger(seller, "TRADE_CREDIT", value, order_id=sell.id, trade_id=trade_id)
        self._flush_cash(seller)

        self.connection.execute(
            text("UPDATE instruments SET last_trade_price = :price WHERE id = :id"),
            {"price": price, "id": taker.instrument_id},
        )
        return trade_id

    def cancel(self, order: Order) -> None:
        self.tick()
        account = self.accounts[order.account_id]
        remaining = order.quantity - order.filled
        if order.side == "BUY":
            amount = money(order.price * remaining)
            account.reserved -= amount
            self._ledger(account, "RELEASE", amount, order_id=order.id)
            self._flush_cash(account)
        else:
            account.positions[order.instrument_id][1] -= remaining
            self._flush_position(account, order.instrument_id)
        order.status = "CANCELLED"
        self._flush_order(order)

    def _insert_order(self, account, instrument_id, side, price, quantity, status, code, reason) -> int:
        return self._scalar(
            "INSERT INTO orders (account_id, instrument_id, side, order_type, time_in_force, price, "
            "quantity, filled_quantity, status, reject_code, reject_reason, created_at, updated_at) "
            "VALUES (:account_id, :instrument_id, :side, 'LIMIT', 'GTC', :price, :quantity, 0, :status, "
            ":code, :reason, :at, :at) RETURNING id",
            account_id=account.id,
            instrument_id=instrument_id,
            side=side,
            price=price,
            quantity=quantity,
            status=status,
            code=code,
            reason=reason,
            at=self.clock,
        )

    def _flush_order(self, order: Order) -> None:
        self.connection.execute(
            text(
                "UPDATE orders SET filled_quantity = :filled, status = :status, updated_at = :at "
                "WHERE id = :id"
            ),
            {"filled": order.filled, "status": order.status, "at": self.clock, "id": order.id},
        )

    def _flush_cash(self, account: Account) -> None:
        self.connection.execute(
            text(
                "UPDATE cash_balances SET balance = :balance, reserved = :reserved, updated_at = :at "
                "WHERE account_id = :id"
            ),
            {"balance": account.balance, "reserved": account.reserved, "at": self.clock, "id": account.id},
        )

    def _flush_position(self, account: Account, instrument_id: int) -> None:
        quantity, reserved = account.positions[instrument_id]
        self.connection.execute(
            text(
                "INSERT INTO positions (account_id, instrument_id, quantity, reserved_quantity, "
                "created_at, updated_at) "
                "VALUES (:account_id, :instrument_id, :quantity, :reserved, :at, :at) "
                "ON CONFLICT (account_id, instrument_id) DO UPDATE SET quantity = EXCLUDED.quantity, "
                "reserved_quantity = EXCLUDED.reserved_quantity, updated_at = EXCLUDED.updated_at"
            ),
            {
                "account_id": account.id,
                "instrument_id": instrument_id,
                "quantity": quantity,
                "reserved": reserved,
                "at": self.clock,
            },
        )


def build_scenario(connection: Connection) -> Scenario:
    sim = Simulator(connection)
    symbols = dict(connection.execute(text("SELECT symbol, id FROM instruments")).all())
    aapl, msft, nvda, tsla = (int(symbols[s]) for s in ("AAPL", "MSFT", "NVDA", "TSLA"))

    mm = sim.create_account("market-maker@system.local", "SYSTEM", "SYS-MM-000001")
    alice = sim.create_account("alice@example.com", "ACTIVE", "ACC-000002")
    bob = sim.create_account("bob@example.com", "ACTIVE", "ACC-000003")

    sim.deposit(mm, "1000000", "mm_deposit")
    for instrument_id in symbols.values():
        sim.grant_shares(mm, int(instrument_id), Scenario.MARKET_MAKER_SHARES)
    sim.deposit(alice, "50000", "alice_deposit")
    sim.deposit(bob, "20000", "bob_deposit")

    # Market maker quotes: one bid and one ask on AAPL and TSLA.
    mm_aapl_ask = sim.place(mm, aapl, "SELL", "191.00", 100)
    mm_aapl_bid = sim.place(mm, aapl, "BUY", "189.00", 100)
    mm_tsla_ask = sim.place(mm, tsla, "SELL", "251.00", 100)
    mm_tsla_bid = sim.place(mm, tsla, "BUY", "249.00", 100)

    # Alice buys 150 AAPL at up to 192: 100 fill against the ask at 191 (price improvement 1.00 x 100),
    # 50 rest on the book.
    alice_buy = sim.place(alice, aapl, "BUY", "192.00", 150)
    sim.tick(4)
    trade_alice_buys = sim.fill(alice_buy, mm_aapl_ask, 100)

    # Bob places a resting MSFT bid and cancels it; then an out-of-band NVDA bid is rejected.
    bob_msft = sim.place(bob, msft, "BUY", "400.10", 10)
    sim.tick(30)
    sim.cancel(bob_msft)
    bob_nvda = sim.reject(bob, nvda, "BUY", "156.00", 1, "PRICE_OUT_OF_BAND")

    # Alice sells 30 AAPL at 189, crossing the market maker's bid (the bid is the maker).
    alice_sell = sim.place(alice, aapl, "SELL", "189.00", 30)
    sim.tick(2)
    trade_alice_sells = sim.fill(alice_sell, mm_aapl_bid, 30)

    return Scenario(
        market_maker=mm.id,
        alice=alice.id,
        bob=bob.id,
        aapl=aapl,
        msft=msft,
        nvda=nvda,
        tsla=tsla,
        mm_aapl_ask=mm_aapl_ask.id,
        mm_aapl_bid=mm_aapl_bid.id,
        mm_tsla_ask=mm_tsla_ask.id,
        mm_tsla_bid=mm_tsla_bid.id,
        alice_aapl_buy=alice_buy.id,
        bob_msft_buy_cancelled=bob_msft.id,
        bob_nvda_buy_rejected=bob_nvda.id,
        alice_aapl_sell=alice_sell.id,
        trade_alice_buys=trade_alice_buys,
        trade_alice_sells=trade_alice_sells,
        ledger_ids=sim.ledger_ids,
        accounts={"market_maker": mm, "alice": alice, "bob": bob},
    )
