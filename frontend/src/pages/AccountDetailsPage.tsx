import { useEffect, useState } from 'react';
import { api, getErrorMessage } from '../api/client';
import type { Account } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useSelectedAccount } from '../auth/useSelectedAccount';
import { AccountSelector } from '../components/AccountSelector';
import { AccountSummary } from '../components/AccountSummary';
import { LoadingState } from '../components/LoadingState';
import { PageHeader } from '../components/PageHeader';
import { StatusMessage } from '../components/StatusMessage';

export function AccountDetailsPage() {
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
      <PageHeader title="Account details" eyebrow="Customer account">
        <AccountSelector compact />
      </PageHeader>
      {!accountNumber ? <StatusMessage type="info" message="Select an account number to view details." /> : null}
      {loading ? <LoadingState label="Loading account" /> : null}
      <StatusMessage type="error" message={error} />
      {account ? (
        <>
          <AccountSummary account={account} />
          <section className="details-panel">
            <div><span>Account number</span><strong>{account.accountNumber}</strong></div>
            <div><span>Customer</span><strong>{account.customerName ?? 'Unavailable'}</strong></div>
            <div><span>Account ID</span><strong>{account.id}</strong></div>
          </section>
        </>
      ) : null}
    </>
  );
}
