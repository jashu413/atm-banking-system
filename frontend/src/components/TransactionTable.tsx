import type { Transaction } from '../api/types';
import { formatCurrency, formatDateTime } from '../utils/format';

export function TransactionTable({ transactions }: { transactions: Transaction[] }) {
  if (transactions.length === 0) {
    return <div className="empty-state">No transactions found for this account.</div>;
  }

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Date</th>
            <th>Type</th>
            <th>Amount</th>
            <th>Balance after</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((transaction) => (
            <tr key={transaction.id}>
              <td>{formatDateTime(transaction.createdAt)}</td>
              <td><span className="table-pill">{transaction.type.replaceAll('_', ' ')}</span></td>
              <td>{formatCurrency(transaction.amount)}</td>
              <td>{formatCurrency(transaction.balanceAfter)}</td>
              <td>{transaction.description}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
