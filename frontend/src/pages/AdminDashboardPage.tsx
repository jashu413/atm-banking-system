import { LockKeyhole, UsersRound } from 'lucide-react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';

export function AdminDashboardPage() {
  return (
    <>
      <PageHeader title="Admin dashboard" eyebrow="Back office" />
      <section className="action-grid">
        <Link className="action-tile" to="/admin/accounts"><UsersRound size={20} /> Review customer accounts</Link>
        <Link className="action-tile" to="/admin/lock-unlock"><LockKeyhole size={20} /> Lock or unlock accounts</Link>
      </section>
    </>
  );
}
