import type { Account } from '../api/types';
import { formatCurrency } from '../utils/format';

export function AccountSummary({ account }: { account: Account }) {
  return (
    <section className="summary-grid">
      <div className="metric-card">
        <span>Available balance</span>
        <strong>{formatCurrency(account.balance)}</strong>
      </div>
      <div className="metric-card">
        <span>Account type</span>
        <strong>{account.accountType}</strong>
      </div>
      <div className="metric-card">
        <span>Daily withdrawal limit</span>
        <strong>{formatCurrency(account.dailyWithdrawalLimit)}</strong>
      </div>
      <div className="metric-card">
        <span>Status</span>
        <strong className={account.locked ? 'text-danger' : 'text-success'}>
          {account.locked ? 'Locked' : 'Active'}
        </strong>
      </div>
    </section>
  );
}
