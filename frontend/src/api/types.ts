export type Role = 'CUSTOMER' | 'ADMIN';

export type UserProfile = {
  username: string;
  role: Role;
  customerName: string | null;
  accountNumber: string | null;
};

export type LoginResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresInSeconds: number;
  username: string;
  role: Role;
};

export type ApiError = {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  path?: string;
};

export type Account = {
  id: number;
  accountNumber: string;
  accountType: string;
  customerName: string | null;
  balance: number;
  dailyWithdrawalLimit: number;
  locked: boolean;
};

export type Transaction = {
  id: number;
  type: string;
  amount: number;
  balanceAfter: number;
  description: string;
  relatedAccount: string | null;
  createdAt: string;
};
