import { useEffect, useState } from 'react';
import { api, getErrorMessage } from '../api/client';
import type { Transaction } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useSelectedAccount } from '../auth/useSelectedAccount';
import { AccountSelector } from '../components/AccountSelector';
import { LoadingState } from '../components/LoadingState';
import { PageHeader } from '../components/PageHeader';
import { StatusMessage } from '../components/StatusMessage';
import { TransactionTable } from '../components/TransactionTable';

export function TransactionHistoryPage() {
  const auth = useAuth();
  const { accountNumber } = useSelectedAccount();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!auth.accessToken || !accountNumber) return;
    setLoading(true);
    setError('');
    api.transactions(accountNumber)
      .then(setTransactions)
      .catch((err) => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [auth.accessToken, accountNumber]);

  return (
    <>
      <PageHeader title="Transaction history" eyebrow="Full ledger"><AccountSelector compact /></PageHeader>
      {!accountNumber ? <StatusMessage type="info" message="Select an account number to view history." /> : null}
      {loading ? <LoadingState label="Loading transactions" /> : null}
      <StatusMessage type="error" message={error} />
      {accountNumber && !loading && !error ? <TransactionTable transactions={transactions} /> : null}
    </>
  );
}
