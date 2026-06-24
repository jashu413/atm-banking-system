import { ShieldAlert } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function ForbiddenPage() {
  const { role } = useAuth();
  const homeLink = role === 'ADMIN' ? '/admin' : '/dashboard';

  return (
    <main className="simple-page">
      <ShieldAlert size={42} />
      <h1>Access denied</h1>
      <p>Your current role does not have permission to view this page.</p>
      <Link className="button primary" to={homeLink}>Return to dashboard</Link>
    </main>
  );
}
