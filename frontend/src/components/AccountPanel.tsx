import { FormEvent, useState } from 'react';
import { Account, api } from '../api';
import { formatMoney } from '../format';

interface Props {
  account: Account | null;
  onDeposited: () => void;
}

export default function AccountPanel({ account, onDeposited }: Props) {
  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState('50000');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function deposit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.deposit(Number(amount));
      setOpen(false);
      onDeposited();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="account">
      <div className="stat">
        <span className="label">Balance</span>
        <span className="value num">{formatMoney(account?.balance)}</span>
      </div>
      <div className="stat">
        <span className="label">Reserved</span>
        <span className="value num muted">{formatMoney(account?.reserved)}</span>
      </div>
      <div className="stat">
        <span className="label">Available</span>
        <span className="value num strong">{formatMoney(account?.available)}</span>
      </div>
      <span className="muted small">{account ? `${account.currency} · ${account.accountNumber}` : ''}</span>

      <div className="deposit">
        <button className="secondary" onClick={() => setOpen((value) => !value)}>
          Deposit
        </button>
        {open && (
          <form className="popover" onSubmit={deposit}>
            <label>
              Amount (USD)
              <input
                type="number"
                min="0.01"
                max="1000000"
                step="0.01"
                required
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </label>
            {error && <div className="banner error">{error}</div>}
            <button className="primary" type="submit" disabled={busy}>
              {busy ? 'Depositing…' : 'Deposit simulated cash'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
