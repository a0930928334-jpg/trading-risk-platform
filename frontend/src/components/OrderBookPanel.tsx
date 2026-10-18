import { OrderBook, PriceLevel } from '../api';
import { depthPercent, formatPrice, formatQuantity, spread } from '../format';

interface Props {
  book: OrderBook | null;
  error: string | null;
  onPickPrice: (price: number) => void;
}

export default function OrderBookPanel({ book, error, onPickPrice }: Props) {
  if (error && !book) return <div className="banner error">{error}</div>;
  if (!book) return <p className="muted small">Loading…</p>;

  const maxQuantity = Math.max(1, ...book.bids.map((l) => l.quantity), ...book.asks.map((l) => l.quantity));
  // Asks are listed best (lowest) first by the API; show them highest at the top so the spread sits in the middle.
  const asks = [...book.asks].reverse();
  const bookSpread = spread(book.bids[0]?.price, book.asks[0]?.price);

  return (
    <div className="book">
      <div className="book-row head">
        <span>Price</span>
        <span>Quantity</span>
        <span>Orders</span>
      </div>
      {asks.length === 0 && <div className="book-empty muted small">No asks</div>}
      {asks.map((level) => (
        <Row key={`a${level.price}`} level={level} side="ask" max={maxQuantity} onPick={onPickPrice} />
      ))}
      <div className="book-mid">
        <span>
          Last <strong className="num">{formatPrice(book.lastTradePrice)}</strong>
        </span>
        <span className="muted small">
          Spread {bookSpread == null ? '—' : formatPrice(bookSpread)} · Ref {formatPrice(book.referencePrice)}
        </span>
      </div>
      {book.bids.map((level) => (
        <Row key={`b${level.price}`} level={level} side="bid" max={maxQuantity} onPick={onPickPrice} />
      ))}
      {book.bids.length === 0 && <div className="book-empty muted small">No bids</div>}
      <p className="muted small hint">Click a level to use its price.</p>
    </div>
  );
}

function Row({ level, side, max, onPick }: { level: PriceLevel; side: 'bid' | 'ask'; max: number; onPick: (p: number) => void }) {
  return (
    <button
      className={`book-row ${side}`}
      onClick={() => onPick(level.price)}
      aria-label={`${side === 'bid' ? 'Bid' : 'Ask'} ${formatPrice(level.price)}, ${formatQuantity(level.quantity)} shares. Use this price`}
    >
      <span className="bar" style={{ width: `${depthPercent(level.quantity, max)}%` }} />
      <span className="num price">{formatPrice(level.price)}</span>
      <span className="num">{formatQuantity(level.quantity)}</span>
      <span className="num muted">{level.orderCount}</span>
    </button>
  );
}
