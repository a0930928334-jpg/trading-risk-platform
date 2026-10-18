import { useState } from 'react';
import { api } from '../api';
import { formatMoney } from '../format';

const STARTING_CASH = 50_000;

/** Shown while the account has no cash and no shares: a new user's first step is a deposit. */
export default function GettingStarted({ onDeposited }: { onDeposited: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function deposit() {
    setBusy(true);
    setError(null);
    try {
      await api.deposit(STARTING_CASH);
      onDeposited();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  }

  return (
    <div className="banner info getting-started" role="status">
      <span>
        <strong>Your account is empty.</strong> Deposit simulated cash to start trading; the market maker is quoting
        every instrument.
        {error && <span className="down"> {error}</span>}
      </span>
      <button className="primary" onClick={deposit} disabled={busy}>
        {busy ? 'Depositing…' : `Deposit ${formatMoney(STARTING_CASH)} USD`}
      </button>
    </div>
  );
}
