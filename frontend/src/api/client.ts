import axios from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';
import type { Account, ApiError, LoginResponse, Transaction, UserProfile } from './types';

// Keep a separate axios instance for the refresh call so it bypasses our interceptors.
const refreshClient = axios.create();

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const AUTH_KEY = 'atm.auth.session';
const ACCOUNT_KEY = 'atm.selectedAccount';

export class BankingApiError extends Error {
  details: ApiError;

  constructor(details: ApiError) {
    super(details.message);
    this.name = 'BankingApiError';
    this.details = details;
  }
}

function readAuth(): { accessToken: string | null; refreshToken: string | null } {
  try {
    const stored = sessionStorage.getItem(AUTH_KEY);
    return stored ? (JSON.parse(stored) as { accessToken: string; refreshToken: string }) : { accessToken: null, refreshToken: null };
  } catch {
    return { accessToken: null, refreshToken: null };
  }
}

function normalizeError(data: unknown, status: number, path: string): ApiError {
  if (data && typeof data === 'object' && 'message' in data) {
    return data as ApiError;
  }
  return {
    status,
    error: status === 403 ? 'Forbidden' : 'Request Failed',
    message: 'The request could not be completed.',
    path,
  };
}

const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { Accept: 'application/json' },
});

// Attach the stored access token to every request.
axiosClient.interceptors.request.use((config) => {
  const { accessToken } = readAuth();
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

// On 401: attempt silent token refresh, retry once, then redirect to login.
// Auth endpoints are excluded — their 401s are credential errors, not expiry.
axiosClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    const axiosError = axios.isAxiosError(error) ? error : null;
    const originalConfig = axiosError?.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const url = originalConfig?.url ?? '';
    const status = axiosError?.response?.status;

    if (status === 401 && !originalConfig?._retry && !url.includes('/auth/')) {
      if (originalConfig) originalConfig._retry = true;
      const { refreshToken } = readAuth();
      if (refreshToken) {
        try {
          // Use a bare client so this call does NOT go through our interceptors.
          const resp = await refreshClient.post<LoginResponse>(
            `${API_BASE_URL}/auth/refresh`,
            { refreshToken },
            { headers: { 'Content-Type': 'application/json', Accept: 'application/json' } },
          );
          const { accessToken: newAccess, refreshToken: newRefresh } = resp.data;
          const stored = sessionStorage.getItem(AUTH_KEY);
          if (stored) {
            sessionStorage.setItem(AUTH_KEY, JSON.stringify({
              ...JSON.parse(stored) as object,
              accessToken: newAccess,
              refreshToken: newRefresh,
            }));
          }
          if (originalConfig) {
            originalConfig.headers.Authorization = `Bearer ${newAccess}`;
            return axiosClient.request(originalConfig);
          }
        } catch {
          // Refresh failed — fall through to clear session and redirect.
        }
      }
      sessionStorage.removeItem(AUTH_KEY);
      sessionStorage.removeItem(ACCOUNT_KEY);
      window.location.href = '/login';
      return Promise.reject(error);
    }

    const data = axiosError?.response?.data ?? null;
    const errorStatus = axiosError?.response?.status ?? 0;
    throw new BankingApiError(normalizeError(data, errorStatus, url));
  },
);

async function request<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const response = await axiosClient.request<T>({
    url: path,
    method: options.method ?? 'GET',
    data: options.body,
  });
  return response.data;
}

export const api = {
  login: (username: string, password: string) =>
    request<LoginResponse>('/auth/login', { method: 'POST', body: { username, password } }),

  logout: (refreshToken?: string) =>
    request<{ message: string }>('/auth/logout', {
      method: 'POST',
      body: refreshToken ? { refreshToken } : undefined,
    }),

  getAccount: (accountNumber: string) =>
    request<Account>(`/accounts/${encodeURIComponent(accountNumber)}`),

  deposit: (accountNumber: string, amount: string) =>
    request<Account>(`/accounts/${encodeURIComponent(accountNumber)}/deposit`, {
      method: 'POST',
      body: { amount },
    }),

  withdraw: (accountNumber: string, pin: string, amount: string) =>
    request<Account>(`/accounts/${encodeURIComponent(accountNumber)}/withdraw`, {
      method: 'POST',
      body: { pin, amount },
    }),

  transfer: (accountNumber: string, targetAccountNumber: string, pin: string, amount: string) =>
    request<{ message: string }>(`/accounts/${encodeURIComponent(accountNumber)}/transfer`, {
      method: 'POST',
      body: { targetAccountNumber, pin, amount },
    }),

  changePin: (accountNumber: string, currentPin: string, newPin: string) =>
    request<Account>(`/accounts/${encodeURIComponent(accountNumber)}/pin`, {
      method: 'POST',
      body: { currentPin, newPin },
    }),

  transactions: (accountNumber: string) =>
    request<Transaction[]>(`/accounts/${encodeURIComponent(accountNumber)}/transactions`),

  miniStatement: (accountNumber: string, count = 5) =>
    request<Transaction[]>(`/accounts/${encodeURIComponent(accountNumber)}/transactions/mini?count=${count}`),

  getProfile: () => request<UserProfile>('/users/me'),

  adminAccounts: () => request<Account[]>('/admin/accounts'),

  lockAccount: (accountNumber: string) =>
    request<Account>(`/admin/accounts/${encodeURIComponent(accountNumber)}/lock`, { method: 'POST' }),

  unlockAccount: (accountNumber: string) =>
    request<Account>(`/admin/accounts/${encodeURIComponent(accountNumber)}/unlock`, { method: 'POST' }),
};

export function getErrorMessage(error: unknown): string {
  if (error instanceof BankingApiError) {
    return error.details.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'Something went wrong.';
}
