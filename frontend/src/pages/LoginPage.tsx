import { Landmark, LockKeyhole, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { getErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { StatusMessage } from '../components/StatusMessage';

export function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (auth.isAuthenticated) {
    return <Navigate to={auth.role === 'ADMIN' ? '/admin' : '/dashboard'} replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      const role = await auth.login(username, password);
      const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;
      navigate(from && from !== '/login' ? from : role === 'ADMIN' ? '/admin' : '/dashboard', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="login-page">
      <div className="login-visual" aria-hidden="true">
        <div className="vault-plate">
          <Landmark size={44} />
          <strong>ATM Banking</strong>
          <span>Secure account operations</span>
        </div>
      </div>
      <div className="login-panel">
        <div className="login-heading">
          <ShieldCheck size={32} />
          <div>
            <span className="eyebrow">Secure sign in</span>
            <h1>Access your banking console</h1>
          </div>
        </div>
        <form className="form-stack" onSubmit={submit}>
          <StatusMessage type="error" message={error} />
          <label>
            Username
            <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              required
            />
          </label>
          <button className="button primary icon-text" disabled={loading} type="submit">
            <LockKeyhole size={18} />
            <span>{loading ? 'Signing in...' : 'Sign in'}</span>
          </button>
        </form>
      </div>
    </section>
  );
}
