import { SearchX } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function NotFoundPage() {
  const { role } = useAuth();
  const homeLink = role === 'ADMIN' ? '/admin' : '/dashboard';

  return (
    <main className="simple-page">
      <SearchX size={42} />
      <h1>Page not found</h1>
      <p>The page you requested does not exist.</p>
      <Link className="button primary" to={homeLink}>Go home</Link>
    </main>
  );
}
