import { BanknoteArrowDown } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { api, getErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useSelectedAccount } from '../auth/useSelectedAccount';
import { AccountSelector } from '../components/AccountSelector';
import { PageHeader } from '../components/PageHeader';
import { StatusMessage } from '../components/StatusMessage';
import { formatCurrency } from '../utils/format';

export function DepositPage() {
  const auth = useAuth();
  const { accountNumber } = useSelectedAccount();
  const [amount, setAmount] = useState('');
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
      const account = await api.deposit(accountNumber, amount);
      setSuccess(`Deposit completed. New balance: ${formatCurrency(account.balance)}.`);
      setAmount('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <PageHeader title="Deposit" eyebrow="Cash operation"><AccountSelector compact /></PageHeader>
      <section className="form-panel">
        <form className="form-stack" onSubmit={submit}>
          <StatusMessage type="error" message={error} />
          <StatusMessage type="success" message={success} />
          <label>
            Amount
            <input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required />
          </label>
          <button className="button primary icon-text" disabled={loading || !accountNumber} type="submit">
            <BanknoteArrowDown size={18} />
            <span>{loading ? 'Depositing...' : 'Deposit'}</span>
          </button>
        </form>
      </section>
    </>
  );
}
