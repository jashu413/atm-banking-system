import { KeyRound } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { api, getErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useSelectedAccount } from '../auth/useSelectedAccount';
import { AccountSelector } from '../components/AccountSelector';
import { PageHeader } from '../components/PageHeader';
import { StatusMessage } from '../components/StatusMessage';

export function ChangePinPage() {
  const auth = useAuth();
  const { accountNumber } = useSelectedAccount();
  const [currentPin, setCurrentPin] = useState('');
  const [newPin, setNewPin] = useState('');
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
      await api.changePin(accountNumber, currentPin, newPin);
      setSuccess('PIN changed successfully.');
      setCurrentPin('');
      setNewPin('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <PageHeader title="Change PIN" eyebrow="Security"><AccountSelector compact /></PageHeader>
      <section className="form-panel">
        <form className="form-stack" onSubmit={submit}>
          <StatusMessage type="error" message={error} />
          <StatusMessage type="success" message={success} />
          <label>
            Current PIN
            <input type="password" inputMode="numeric" maxLength={4} value={currentPin} onChange={(e) => setCurrentPin(e.target.value)} required />
          </label>
          <label>
            New PIN
            <input type="password" inputMode="numeric" maxLength={4} value={newPin} onChange={(e) => setNewPin(e.target.value)} required />
          </label>
          <button className="button primary icon-text" disabled={loading || !accountNumber} type="submit">
            <KeyRound size={18} />
            <span>{loading ? 'Updating...' : 'Change PIN'}</span>
          </button>
        </form>
      </section>
    </>
  );
}
