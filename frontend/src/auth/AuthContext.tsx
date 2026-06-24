import { createContext, useContext, useMemo, useState } from 'react';
import { api } from '../api/client';
import type { LoginResponse, Role } from '../api/types';

const ACCOUNT_KEY = 'atm.selectedAccount';

type AuthState = {
  accessToken: string | null;
  refreshToken: string | null;
  username: string | null;
  role: Role | null;
};

type AuthContextValue = AuthState & {
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<Role>;
  logout: () => Promise<void>;
};

const AUTH_KEY = 'atm.auth.session';

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function readStoredAuth(): AuthState {
  const stored = sessionStorage.getItem(AUTH_KEY);
  if (!stored) {
    return emptyAuth;
  }

  try {
    return JSON.parse(stored) as AuthState;
  } catch {
    sessionStorage.removeItem(AUTH_KEY);
    return emptyAuth;
  }
}

const emptyAuth: AuthState = {
  accessToken: null,
  refreshToken: null,
  username: null,
  role: null,
};

function toAuthState(response: LoginResponse): AuthState {
  return {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    username: response.username,
    role: response.role,
  };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [auth, setAuth] = useState<AuthState>(() => readStoredAuth());

  const value = useMemo<AuthContextValue>(() => ({
    ...auth,
    isAuthenticated: Boolean(auth.accessToken),
    async login(username: string, password: string) {
      const response = await api.login(username, password);
      const next = toAuthState(response);
      sessionStorage.setItem(AUTH_KEY, JSON.stringify(next));
      setAuth(next);
      if (response.role === 'CUSTOMER') {
        try {
          const profile = await api.getProfile();
          if (profile.accountNumber) {
            sessionStorage.setItem(ACCOUNT_KEY, profile.accountNumber);
            window.dispatchEvent(new Event('selected-account-change'));
          }
        } catch {
          // Non-critical; user can select the account manually.
        }
      }
      return response.role;
    },
    async logout() {
      // Read the refresh token BEFORE clearing — needed for server-side revocation.
      const { refreshToken: storedRefreshToken } = readStoredAuth();
      setAuth(emptyAuth);
      sessionStorage.removeItem(AUTH_KEY);
      sessionStorage.removeItem('atm.selectedAccount');
      try {
        // /auth/logout is public; sends the refresh token so the backend can revoke it.
        await api.logout(storedRefreshToken ?? undefined);
      } catch {
        // Logout is always local. Server-side revocation is best-effort.
      }
    },
  }), [auth]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
