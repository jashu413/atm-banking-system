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

export function MiniStatementPage() {
  const auth = useAuth();
  const { accountNumber } = useSelectedAccount();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [count, setCount] = useState(5);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!auth.accessToken || !accountNumber) return;
    setLoading(true);
    setError('');
    api.miniStatement(accountNumber, count)
      .then(setTransactions)
      .catch((err) => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [auth.accessToken, accountNumber, count]);

  return (
    <>
      <PageHeader title="Mini statement" eyebrow="Recent activity">
        <AccountSelector compact />
      </PageHeader>
      <div className="toolbar-line">
        <label>
          Number of transactions
          <input type="number" min={1} max={20} value={count} onChange={(e) => setCount(Number(e.target.value))} />
        </label>
      </div>
      {!accountNumber ? <StatusMessage type="info" message="Select an account number to view recent transactions." /> : null}
      {loading ? <LoadingState label="Loading mini statement" /> : null}
      <StatusMessage type="error" message={error} />
      {accountNumber && !loading && !error ? <TransactionTable transactions={transactions} /> : null}
    </>
  );
}
