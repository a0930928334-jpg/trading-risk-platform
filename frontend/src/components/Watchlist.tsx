import { Instrument } from '../api';
import { deviationPercent, formatPrice } from '../format';

interface Props {
  instruments: Instrument[];
  selected: string;
  onSelect: (symbol: string) => void;
}

export default function Watchlist({ instruments, selected, onSelect }: Props) {
  if (instruments.length === 0) {
    return <p className="muted small">Loading…</p>;
  }
  return (
    <ul className="watchlist">
      {instruments.map((instrument) => {
        const last = instrument.lastTradePrice;
        const change = last == null ? null : deviationPercent(last, instrument.referencePrice);
        return (
          <li key={instrument.symbol}>
            <button
              className={instrument.symbol === selected ? 'selected' : ''}
              onClick={() => onSelect(instrument.symbol)}
              title={instrument.name}
            >
              <span className="symbol">{instrument.symbol}</span>
              <span className="num">{formatPrice(last ?? instrument.referencePrice)}</span>
              <span className={`num small ${change == null ? 'muted' : change >= 0 ? 'up' : 'down'}`}>
                {change == null ? 'ref' : `${change >= 0 ? '+' : ''}${change.toFixed(2)}%`}
              </span>
              {instrument.status !== 'ACTIVE' && <span className="tag warn">{instrument.status}</span>}
            </button>
          </li>
        );
      })}
    </ul>
  );
}
