import { useEffect, useState } from 'react';

const ACCOUNT_KEY = 'atm.selectedAccount';

export function useSelectedAccount() {
  const [accountNumber, setAccountNumberState] = useState(() => sessionStorage.getItem(ACCOUNT_KEY) ?? '');

  useEffect(() => {
    const handler = () => setAccountNumberState(sessionStorage.getItem(ACCOUNT_KEY) ?? '');
    window.addEventListener('selected-account-change', handler);
    return () => window.removeEventListener('selected-account-change', handler);
  }, []);

  function setAccountNumber(value: string) {
    const clean = value.trim();
    if (clean) {
      sessionStorage.setItem(ACCOUNT_KEY, clean);
    } else {
      sessionStorage.removeItem(ACCOUNT_KEY);
    }
    setAccountNumberState(clean);
    window.dispatchEvent(new Event('selected-account-change'));
  }

  return { accountNumber, setAccountNumber };
}
