import { Send } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { api, getErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useSelectedAccount } from '../auth/useSelectedAccount';
import { AccountSelector } from '../components/AccountSelector';
import { PageHeader } from '../components/PageHeader';
import { StatusMessage } from '../components/StatusMessage';

export function TransferPage() {
  const auth = useAuth();
  const { accountNumber } = useSelectedAccount();
  const [targetAccountNumber, setTargetAccountNumber] = useState('');
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
      const response = await api.transfer(accountNumber, targetAccountNumber, pin, amount);
      setSuccess(response.message);
      setTargetAccountNumber('');
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
      <PageHeader title="Transfer" eyebrow="Move funds"><AccountSelector compact /></PageHeader>
      <section className="form-panel">
        <form className="form-stack" onSubmit={submit}>
          <StatusMessage type="error" message={error} />
          <StatusMessage type="success" message={success} />
          <label>
            Recipient account
            <input value={targetAccountNumber} onChange={(e) => setTargetAccountNumber(e.target.value)} required />
          </label>
          <label>
            Amount
            <input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required />
          </label>
          <label>
            PIN
            <input type="password" inputMode="numeric" maxLength={4} value={pin} onChange={(e) => setPin(e.target.value)} required />
          </label>
          <button className="button primary icon-text" disabled={loading || !accountNumber} type="submit">
            <Send size={18} />
            <span>{loading ? 'Transferring...' : 'Transfer'}</span>
          </button>
        </form>
      </section>
    </>
  );
}
