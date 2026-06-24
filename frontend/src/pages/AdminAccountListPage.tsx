import { useEffect, useState } from 'react';
import { api, getErrorMessage } from '../api/client';
import type { Account } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { LoadingState } from '../components/LoadingState';
import { PageHeader } from '../components/PageHeader';
import { StatusMessage } from '../components/StatusMessage';
import { formatCurrency } from '../utils/format';

export function AdminAccountListPage() {
  const auth = useAuth();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!auth.accessToken) return;
    api.adminAccounts()
      .then(setAccounts)
      .catch((err) => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [auth.accessToken]);

  return (
    <>
      <PageHeader title="Account list" eyebrow="Admin" />
      {loading ? <LoadingState label="Loading accounts" /> : null}
      <StatusMessage type="error" message={error} />
      {!loading && !error && accounts.length === 0 ? <div className="empty-state">No customer accounts found.</div> : null}
      {accounts.length > 0 ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Account</th>
                <th>Customer</th>
                <th>Type</th>
                <th>Balance</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.id}>
                  <td>{account.accountNumber}</td>
                  <td>{account.customerName}</td>
                  <td>{account.accountType}</td>
                  <td>{formatCurrency(account.balance)}</td>
                  <td><span className={account.locked ? 'table-pill danger' : 'table-pill success'}>{account.locked ? 'Locked' : 'Active'}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </>
  );
}
