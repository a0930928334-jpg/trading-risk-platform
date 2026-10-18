import { FormEvent, useEffect, useRef, useState } from 'react';
import { Account, api, Instrument, Order, Position, Side } from '../api';
import { deviationPercent, formatMoney, formatPrice, formatQuantity, notional } from '../format';

interface Props {
  symbol: string;
  instrument: Instrument | undefined;
  account: Account | null;
  position: Position | undefined;
  pickedPrice: { price: number; at: number } | null;
  onPlaced: () => void;
}

const STATUS_TEXT: Record<Order['status'], string> = {
  NEW: 'Resting on the book',
  PARTIALLY_FILLED: 'Partly filled; the rest is resting on the book',
  FILLED: 'Filled',
  CANCELLED: 'Cancelled',
  REJECTED: 'Rejected',
};

export default function OrderForm({ symbol, instrument, account, position, pickedPrice, onPlaced }: Props) {
  const [side, setSide] = useState<Side>('BUY');
  const [price, setPrice] = useState('');
  const [quantity, setQuantity] = useState('10');
  const [result, setResult] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const mark = instrument?.lastTradePrice ?? instrument?.referencePrice;

  // Start from the mark price once per instrument (it may load after the form); a click on the book overrides it.
  const prefilledFor = useRef<string | null>(null);
  useEffect(() => {
    if (mark !== undefined && prefilledFor.current !== symbol) {
      prefilledFor.current = symbol;
      setPrice(mark.toFixed(2));
      setResult(null);
      setError(null);
    }
  }, [symbol, mark]);

  useEffect(() => {
    if (pickedPrice) setPrice(pickedPrice.price.toFixed(2));
  }, [pickedPrice]);

  const priceValue = Number(price);
  const quantityValue = Number(quantity);
  const value = notional(priceValue, quantityValue);
  const deviation = mark ? deviationPercent(priceValue, mark) : 0;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      setResult(await api.placeOrder(symbol, side, priceValue, quantityValue));
      onPlaced();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="order-form" onSubmit={submit}>
      <div className="segmented sides">
        <button type="button" className={side === 'BUY' ? 'active buy' : ''} onClick={() => setSide('BUY')}>
          Buy
        </button>
        <button type="button" className={side === 'SELL' ? 'active sell' : ''} onClick={() => setSide('SELL')}>
          Sell
        </button>
      </div>

      <label>
        Limit price (USD)
        <input type="number" min="0.01" step="0.01" required value={price} onChange={(e) => setPrice(e.target.value)} />
      </label>
      {mark !== undefined && price !== '' && (
        <div className={`small ${Math.abs(deviation) > 10 ? 'down' : 'muted'}`}>
          {deviation >= 0 ? '+' : ''}
          {deviation.toFixed(2)}% from mark {formatPrice(mark)} (limit ±10%)
        </div>
      )}

      <label>
        Quantity (shares)
        <input type="number" min="1" step="1" required value={quantity} onChange={(e) => setQuantity(e.target.value)} />
      </label>

      <dl className="summary">
        <dt>Order value</dt>
        <dd className="num">{formatMoney(value)}</dd>
        {side === 'BUY' ? (
          <>
            <dt>Available cash</dt>
            <dd className={`num ${account && value > account.available ? 'down' : ''}`}>{formatMoney(account?.available)}</dd>
          </>
        ) : (
          <>
            <dt>Available shares</dt>
            <dd className={`num ${quantityValue > (position?.availableQuantity ?? 0) ? 'down' : ''}`}>
              {formatQuantity(position?.availableQuantity ?? 0)}
            </dd>
          </>
        )}
        <dt>Max order value</dt>
        <dd className="num muted">{formatMoney(100000)}</dd>
      </dl>

      <button className={`primary ${side === 'BUY' ? 'buy' : 'sell'}`} type="submit" disabled={busy}>
        {busy ? 'Sending…' : `${side === 'BUY' ? 'Buy' : 'Sell'} ${symbol}`}
      </button>

      {error && <div className="banner error">{error}</div>}
      {result && (
        <div className={`banner ${result.status === 'REJECTED' ? 'error' : result.status === 'CANCELLED' ? 'warn' : 'ok'}`}>
          <strong>
            #{result.id} {STATUS_TEXT[result.status]}
          </strong>
          <div className="small">
            Filled {formatQuantity(result.filledQuantity)} of {formatQuantity(result.quantity)}
            {result.rejectReason && ` · ${result.rejectCode}: ${result.rejectReason}`}
          </div>
        </div>
      )}
    </form>
  );
}
