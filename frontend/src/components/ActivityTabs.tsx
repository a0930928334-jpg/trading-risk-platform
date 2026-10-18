import { useState } from 'react';
import { api, LedgerEntry, Order, Position, Trade } from '../api';
import { formatMoney, formatPrice, formatQuantity, formatTime } from '../format';

interface Props {
  openOrders: Order[];
  history: Order[];
  trades: Trade[];
  positions: Position[];
  ledger: LedgerEntry[];
  onCancelled: () => void;
}

type Tab = 'open' | 'history' | 'trades' | 'positions' | 'ledger';

export default function ActivityTabs({ openOrders, history, trades, positions, ledger, onCancelled }: Props) {
  const [tab, setTab] = useState<Tab>('open');
  const [error, setError] = useState<string | null>(null);

  async function cancel(id: number) {
    setError(null);
    try {
      await api.cancelOrder(id);
      onCancelled();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  const tabs: [Tab, string][] = [
    ['open', `Open orders (${openOrders.length})`],
    ['history', 'Order history'],
    ['trades', 'Trades'],
    ['positions', `Positions (${positions.length})`],
    ['ledger', 'Cash ledger'],
  ];

  return (
    <div>
      <div className="tabs">
        {tabs.map(([id, label]) => (
          <button key={id} className={tab === id ? 'active' : ''} onClick={() => setTab(id)}>
            {label}
          </button>
        ))}
      </div>
      {error && <div className="banner error">{error}</div>}
      <div className="table-wrap">
        {tab === 'open' && <OrdersTable orders={openOrders} onCancel={cancel} empty="No open orders." />}
        {tab === 'history' && <OrdersTable orders={history} empty="No orders yet." />}
        {tab === 'trades' && <TradesTable trades={trades} />}
        {tab === 'positions' && <PositionsTable positions={positions} />}
        {tab === 'ledger' && <LedgerTable entries={ledger} />}
      </div>
    </div>
  );
}

function OrdersTable({ orders, onCancel, empty }: { orders: Order[]; onCancel?: (id: number) => void; empty: string }) {
  if (orders.length === 0) return <p className="muted small">{empty}</p>;
  return (
    <table>
      <thead>
        <tr>
          <th>#</th>
          <th>Time</th>
          <th>Symbol</th>
          <th>Side</th>
          <th className="right">Price</th>
          <th className="right">Filled / Qty</th>
          <th>Status</th>
          <th>Reason</th>
          {onCancel && <th />}
        </tr>
      </thead>
      <tbody>
        {orders.map((order) => (
          <tr key={order.id}>
            <td className="muted">{order.id}</td>
            <td className="muted">{formatTime(order.createdAt)}</td>
            <td>{order.symbol}</td>
            <td className={order.side === 'BUY' ? 'up' : 'down'}>{order.side}</td>
            <td className="num right">{formatPrice(order.price)}</td>
            <td className="num right">
              {formatQuantity(order.filledQuantity)} / {formatQuantity(order.quantity)}
            </td>
            <td>
              <span className={`status ${order.status.toLowerCase()}`}>{order.status.replaceAll('_', ' ')}</span>
            </td>
            <td className="muted small">{order.rejectCode ?? ''}</td>
            {onCancel && (
              <td className="right">
                <button className="link" onClick={() => onCancel(order.id)}>
                  Cancel
                </button>
              </td>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function TradesTable({ trades }: { trades: Trade[] }) {
  if (trades.length === 0) return <p className="muted small">No trades yet.</p>;
  return (
    <table>
      <thead>
        <tr>
          <th>Time</th>
          <th>Symbol</th>
          <th>Side</th>
          <th className="right">Price</th>
          <th className="right">Qty</th>
          <th className="right">Value</th>
          <th>Order</th>
          <th>Liquidity</th>
        </tr>
      </thead>
      <tbody>
        {trades.map((trade) => (
          <tr key={trade.id}>
            <td className="muted">{formatTime(trade.executedAt)}</td>
            <td>{trade.symbol}</td>
            <td className={trade.side === 'BUY' ? 'up' : 'down'}>{trade.side}</td>
            <td className="num right">{formatPrice(trade.price)}</td>
            <td className="num right">{formatQuantity(trade.quantity)}</td>
            <td className="num right">{formatMoney(trade.value)}</td>
            <td className="muted">#{trade.orderId}</td>
            <td className="muted small">{trade.liquidity}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function PositionsTable({ positions }: { positions: Position[] }) {
  if (positions.length === 0) return <p className="muted small">No positions. Buy something first.</p>;
  return (
    <table>
      <thead>
        <tr>
          <th>Symbol</th>
          <th className="right">Quantity</th>
          <th className="right">Reserved (open sells)</th>
          <th className="right">Available</th>
        </tr>
      </thead>
      <tbody>
        {positions.map((position) => (
          <tr key={position.symbol}>
            <td>{position.symbol}</td>
            <td className="num right">{formatQuantity(position.quantity)}</td>
            <td className="num right muted">{formatQuantity(position.reservedQuantity)}</td>
            <td className="num right">{formatQuantity(position.availableQuantity)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function LedgerTable({ entries }: { entries: LedgerEntry[] }) {
  if (entries.length === 0) return <p className="muted small">No cash movements yet. Make a deposit.</p>;
  return (
    <table>
      <thead>
        <tr>
          <th>Time</th>
          <th>Type</th>
          <th className="right">Amount</th>
          <th className="right">Balance after</th>
          <th className="right">Reserved after</th>
          <th>Order</th>
          <th>Trade</th>
        </tr>
      </thead>
      <tbody>
        {entries.map((entry) => (
          <tr key={entry.id}>
            <td className="muted">{formatTime(entry.createdAt)}</td>
            <td>{entry.type.replaceAll('_', ' ')}</td>
            <td className="num right">{formatMoney(entry.amount)}</td>
            <td className="num right">{formatMoney(entry.balanceAfter)}</td>
            <td className="num right muted">{formatMoney(entry.reservedAfter)}</td>
            <td className="muted">{entry.orderId ? `#${entry.orderId}` : ''}</td>
            <td className="muted">{entry.tradeId ? `#${entry.tradeId}` : ''}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
