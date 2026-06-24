import {
  BanknoteArrowDown,
  BanknoteArrowUp,
  Gauge,
  History,
  KeyRound,
  Landmark,
  ListChecks,
  LockKeyhole,
  ReceiptText,
  Send,
  Shield,
  X,
} from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const customerLinks = [
  { to: '/dashboard', label: 'Dashboard', icon: Gauge },
  { to: '/account', label: 'Account details', icon: Landmark },
  { to: '/deposit', label: 'Deposit', icon: BanknoteArrowDown },
  { to: '/withdraw', label: 'Withdraw', icon: BanknoteArrowUp },
  { to: '/transfer', label: 'Transfer', icon: Send },
  { to: '/transactions', label: 'History', icon: History },
  { to: '/mini-statement', label: 'Mini statement', icon: ReceiptText },
  { to: '/change-pin', label: 'Change PIN', icon: KeyRound },
];

const adminLinks = [
  { to: '/admin', label: 'Admin dashboard', icon: Shield },
  { to: '/admin/accounts', label: 'Account list', icon: ListChecks },
  { to: '/admin/lock-unlock', label: 'Lock or unlock', icon: LockKeyhole },
];

export function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  const auth = useAuth();
  const links = auth.role === 'ADMIN' ? adminLinks : customerLinks;

  return (
    <>
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="sidebar-header">
          <div className="logo-lockup">
            <div className="logo-icon"><Landmark size={22} /></div>
            <div>
              <strong>ATM Banking</strong>
              <span>Secure console</span>
            </div>
          </div>
          <button className="icon-button mobile-only" onClick={onClose} aria-label="Close navigation">
            <X size={18} />
          </button>
        </div>
        <nav className="sidebar-nav">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} onClick={onClose} className={({ isActive }) => (isActive ? 'active' : undefined)}>
              <Icon size={18} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
      {open ? <button className="backdrop mobile-only" onClick={onClose} aria-label="Close navigation overlay" /> : null}
    </>
  );
}
