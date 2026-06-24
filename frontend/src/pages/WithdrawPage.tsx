import { BanknoteArrowUp } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { api, getErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useSelectedAccount } from '../auth/useSelectedAccount';
import { AccountSelector } from '../components/AccountSelector';
import { PageHeader } from '../components/PageHeader';
import { StatusMessage } from '../components/StatusMessage';
import { formatCurrency } from '../utils/format';

export function WithdrawPage() {
  const auth = useAuth();
  const { accountNumber } = useSelectedAccount();
  const [amount, setAmount] = useState('');
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!auth.accessToken || !accountNumber) return;
    setError('');
    setSuccess('');
    setLoading(true);
    try {
      const account = await api.withdraw(accountNumber, pin, amount);
      setSuccess(`Withdrawal completed. New balance: ${formatCurrency(account.balance)}.`);
      setAmount('');
      setPin('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <PageHeader title="Withdraw" eyebrow="PIN protected"><AccountSelector compact /></PageHeader>
      <section className="form-panel">
        <form className="form-stack" onSubmit={submit}>
          <StatusMessage type="error" message={error} />
          <StatusMessage type="success" message={success} />
          <label>
            Amount
            <input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required />
          </label>
          <label>
            PIN
            <input type="password" inputMode="numeric" maxLength={4} value={pin} onChange={(e) => setPin(e.target.value)} required />
          </label>
          <button className="button primary icon-text" disabled={loading || !accountNumber} type="submit">
            <BanknoteArrowUp size={18} />
            <span>{loading ? 'Withdrawing...' : 'Withdraw'}</span>
          </button>
        </form>
      </section>
    </>
  );
}
