import { Lock, Unlock } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { api, getErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { StatusMessage } from '../components/StatusMessage';

export function AdminLockUnlockPage() {
  const auth = useAuth();
  const [accountNumber, setAccountNumber] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState<'lock' | 'unlock' | null>(null);

  async function submit(event: FormEvent, action: 'lock' | 'unlock') {
    event.preventDefault();
    if (!auth.accessToken) return;
    setError('');
    setSuccess('');
    setLoading(action);
    try {
      const account = action === 'lock'
        ? await api.lockAccount(accountNumber)
        : await api.unlockAccount(accountNumber);
      setSuccess(`Account ${account.accountNumber} is now ${account.locked ? 'locked' : 'active'}.`);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(null);
    }
  }

  return (
    <>
      <PageHeader title="Lock or unlock account" eyebrow="Admin action" />
      <section className="form-panel">
        <form className="form-stack">
          <StatusMessage type="error" message={error} />
          <StatusMessage type="success" message={success} />
          <label>
            Account number
            <input value={accountNumber} onChange={(e) => setAccountNumber(e.target.value)} required />
          </label>
          <div className="split-actions">
            <button className="button danger icon-text" disabled={Boolean(loading) || !accountNumber} onClick={(e) => void submit(e, 'lock')} type="button">
              <Lock size={18} />
              <span>{loading === 'lock' ? 'Locking...' : 'Lock account'}</span>
            </button>
            <button className="button secondary icon-text" disabled={Boolean(loading) || !accountNumber} onClick={(e) => void submit(e, 'unlock')} type="button">
              <Unlock size={18} />
              <span>{loading === 'unlock' ? 'Unlocking...' : 'Unlock account'}</span>
            </button>
          </div>
        </form>
      </section>
    </>
  );
}
