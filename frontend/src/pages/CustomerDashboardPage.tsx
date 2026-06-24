import { ArrowRight, History, Send } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, getErrorMessage } from '../api/client';
import type { Account } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useSelectedAccount } from '../auth/useSelectedAccount';
import { AccountSelector } from '../components/AccountSelector';
import { AccountSummary } from '../components/AccountSummary';
import { LoadingState } from '../components/LoadingState';
import { PageHeader } from '../components/PageHeader';
import { StatusMessage } from '../components/StatusMessage';

export function CustomerDashboardPage() {
  const auth = useAuth();
  const { accountNumber } = useSelectedAccount();
  const [account, setAccount] = useState<Account | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!auth.accessToken || !accountNumber) {
      setAccount(null);
      return;
    }
    setLoading(true);
    setError('');
    api.getAccount(accountNumber)
      .then(setAccount)
      .catch((err) => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [auth.accessToken, accountNumber]);

  return (
    <>
      <PageHeader title="Customer dashboard" eyebrow="Overview">
        <AccountSelector compact />
      </PageHeader>
      {!accountNumber ? <StatusMessage type="info" message="Select your account number to load dashboard details." /> : null}
      {loading ? <LoadingState label="Loading account" /> : null}
      <StatusMessage type="error" message={error} />
      {account ? <AccountSummary account={account} /> : null}
      <section className="action-grid">
        <Link className="action-tile" to="/deposit"><ArrowRight size={20} /> Deposit funds</Link>
        <Link className="action-tile" to="/transfer"><Send size={20} /> Transfer money</Link>
        <Link className="action-tile" to="/transactions"><History size={20} /> View history</Link>
      </section>
    </>
  );
}
