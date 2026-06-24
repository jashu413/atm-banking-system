import { LogOut, Menu, ShieldCheck, UserRound } from 'lucide-react';
import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Sidebar } from '../components/Sidebar';

export function AppLayout() {
  const [open, setOpen] = useState(false);
  const auth = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await auth.logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="app-shell">
      <Sidebar open={open} onClose={() => setOpen(false)} />
      <div className="workspace">
        <header className="topbar">
          <button className="icon-button mobile-only" onClick={() => setOpen(true)} aria-label="Open navigation">
            <Menu size={20} />
          </button>
          <NavLink className="brand-mark" to={auth.role === 'ADMIN' ? '/admin' : '/dashboard'}>
            <ShieldCheck size={22} />
            <span>ATM Banking</span>
          </NavLink>
          <div className="topbar-user">
            <UserRound size={18} />
            <span>{auth.username}</span>
            <span className="role-pill">{auth.role}</span>
            <button className="icon-button" onClick={handleLogout} aria-label="Log out" title="Log out">
              <LogOut size={18} />
            </button>
          </div>
        </header>
        <main className="content-shell">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
