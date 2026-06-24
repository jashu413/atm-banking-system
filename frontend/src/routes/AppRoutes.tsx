import { Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from '../layouts/AppLayout';
import { AuthLayout } from '../layouts/AuthLayout';
import { AdminAccountListPage } from '../pages/AdminAccountListPage';
import { AdminDashboardPage } from '../pages/AdminDashboardPage';
import { AdminLockUnlockPage } from '../pages/AdminLockUnlockPage';
import { ChangePinPage } from '../pages/ChangePinPage';
import { CustomerDashboardPage } from '../pages/CustomerDashboardPage';
import { DepositPage } from '../pages/DepositPage';
import { ForbiddenPage } from '../pages/ForbiddenPage';
import { LoginPage } from '../pages/LoginPage';
import { MiniStatementPage } from '../pages/MiniStatementPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { AccountDetailsPage } from '../pages/AccountDetailsPage';
import { TransactionHistoryPage } from '../pages/TransactionHistoryPage';
import { TransferPage } from '../pages/TransferPage';
import { WithdrawPage } from '../pages/WithdrawPage';
import { ProtectedRoute } from './ProtectedRoute';

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['CUSTOMER']} />}>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<CustomerDashboardPage />} />
          <Route path="/account" element={<AccountDetailsPage />} />
          <Route path="/deposit" element={<DepositPage />} />
          <Route path="/withdraw" element={<WithdrawPage />} />
          <Route path="/transfer" element={<TransferPage />} />
          <Route path="/transactions" element={<TransactionHistoryPage />} />
          <Route path="/mini-statement" element={<MiniStatementPage />} />
          <Route path="/change-pin" element={<ChangePinPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
        <Route element={<AppLayout />}>
          <Route path="/admin" element={<AdminDashboardPage />} />
          <Route path="/admin/accounts" element={<AdminAccountListPage />} />
          <Route path="/admin/lock-unlock" element={<AdminLockUnlockPage />} />
        </Route>
      </Route>

      <Route path="/forbidden" element={<ForbiddenPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
