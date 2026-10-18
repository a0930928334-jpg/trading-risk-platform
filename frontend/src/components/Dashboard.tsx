import { useState } from 'react';
import { api } from '../api';
import { usePolling } from '../usePolling';
import AccountPanel from './AccountPanel';
import ActivityTabs from './ActivityTabs';
import OrderBookPanel from './OrderBookPanel';
import OrderForm from './OrderForm';
import Watchlist from './Watchlist';

interface Props {
  onLogout: () => void;
}

export default function Dashboard({ onLogout }: Props) {
  const [symbol, setSymbol] = useState('AAPL');
  const [pickedPrice, setPickedPrice] = useState<{ price: number; at: number } | null>(null);

  const account = usePolling(api.account, 3000);
  const instruments = usePolling(api.instruments, 5000);
  const book = usePolling(() => api.book(symbol), 1500, [symbol]);
  const positions = usePolling(api.positions, 3000);
  const openOrders = usePolling(
    async () => {
      const [fresh, partial] = await Promise.all([api.orders('NEW'), api.orders('PARTIALLY_FILLED')]);
      return [...fresh.content, ...partial.content].sort((a, b) => b.id - a.id);
    },
    3000,
  );
  const history = usePolling(() => api.orders().then((page) => page.content), 3000);
  const trades = usePolling(() => api.trades().then((page) => page.content), 3000);
  const ledger = usePolling(() => api.ledger().then((page) => page.content), 5000);

  // After placing or cancelling an order, refresh everything at once instead of waiting for the next poll.
  const refreshAll = () => {
    for (const source of [account, book, positions, openOrders, history, trades, ledger, instruments]) {
      void source.refresh();
    }
  };

  const instrument = instruments.data?.content.find((i) => i.symbol === symbol);
  const position = positions.data?.content.find((p) => p.symbol === symbol);

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">◆</span> Trading &amp; Risk Platform
          <span className="tag">simulated</span>
        </div>
        <AccountPanel account={account.data} onDeposited={refreshAll} />
        <button className="ghost" onClick={onLogout}>
          Log out
        </button>
      </header>

      <main className="grid">
        <section className="panel watchlist-panel">
          <h2>Instruments</h2>
          <Watchlist instruments={instruments.data?.content ?? []} selected={symbol} onSelect={setSymbol} />
        </section>

        <section className="panel book-panel">
          <h2>
            Order book <span className="muted">{symbol}</span>
          </h2>
          <OrderBookPanel
            book={book.data}
            error={book.error}
            onPickPrice={(price) => setPickedPrice({ price, at: Date.now() })}
          />
        </section>

        <section className="panel form-panel">
          <h2>Place order</h2>
          <OrderForm
            symbol={symbol}
            instrument={instrument}
            account={account.data}
            position={position}
            pickedPrice={pickedPrice}
            onPlaced={refreshAll}
          />
        </section>

        <section className="panel activity-panel">
          <ActivityTabs
            openOrders={openOrders.data ?? []}
            history={history.data ?? []}
            trades={trades.data ?? []}
            positions={positions.data?.content ?? []}
            ledger={ledger.data ?? []}
            onCancelled={refreshAll}
          />
        </section>
      </main>
    </div>
  );
}
