import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../api/types';

export function ProtectedRoute({ allowedRoles }: { allowedRoles?: Role[] }) {
  const auth = useAuth();
  const location = useLocation();

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (allowedRoles && (!auth.role || !allowedRoles.includes(auth.role))) {
    return <Navigate to="/forbidden" replace />;
  }

  return <Outlet />;
}
