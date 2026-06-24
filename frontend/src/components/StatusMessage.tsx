export function StatusMessage({ type, message }: { type: 'error' | 'success' | 'info'; message?: string }) {
  if (!message) {
    return null;
  }

  return <div className={`status-message ${type}`}>{message}</div>;
}
