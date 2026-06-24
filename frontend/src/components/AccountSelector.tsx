import { Search } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { useSelectedAccount } from '../auth/useSelectedAccount';

export function AccountSelector({ compact = false }: { compact?: boolean }) {
  const { accountNumber, setAccountNumber } = useSelectedAccount();
  const [draft, setDraft] = useState(accountNumber);

  function submit(event: FormEvent) {
    event.preventDefault();
    setAccountNumber(draft);
  }

  return (
    <form className={compact ? 'account-selector compact' : 'account-selector'} onSubmit={submit}>
      <label htmlFor="accountNumber">Account number</label>
      <div className="inline-control">
        <input
          id="accountNumber"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="1001001001"
          autoComplete="off"
        />
        <button className="button secondary icon-text" type="submit">
          <Search size={16} />
          <span>Select</span>
        </button>
      </div>
    </form>
  );
}
