import { createContext, useContext, useEffect, useMemo, useState, ReactNode } from 'react';
import { authApi, LoginPayload, RegisterPayload } from '@/api/auth';
import { tokenStorage } from '@/api/client';
import type { User } from '@/types';

const USER_KEY = 'sd_user';

interface AuthContextValue {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const stored = localStorage.getItem(USER_KEY);
    const hasToken = !!tokenStorage.getAccessToken();
    if (stored && hasToken) {
      setUser(JSON.parse(stored));
    }
    setIsLoading(false);
  }, []);

  const persistSession = (auth: { userId: string; email: string; fullName: string; role: User['role']; accessToken: string; refreshToken: string }) => {
    const nextUser: User = { id: auth.userId, email: auth.email, fullName: auth.fullName, role: auth.role };
    tokenStorage.setTokens(auth.accessToken, auth.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(nextUser));
    setUser(nextUser);
  };

  const login = async (payload: LoginPayload) => {
    const response = await authApi.login(payload);
    persistSession(response);
  };

  const register = async (payload: RegisterPayload) => {
    const response = await authApi.register(payload);
    persistSession(response);
  };

  const logout = () => {
    tokenStorage.clear();
    localStorage.removeItem(USER_KEY);
    setUser(null);
  };

  const value = useMemo(
    () => ({ user, isAuthenticated: !!user, isLoading, login, register, logout }),
    [user, isLoading]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
