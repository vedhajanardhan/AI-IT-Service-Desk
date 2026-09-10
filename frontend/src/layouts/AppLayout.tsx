import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import type { ComponentType } from 'react';
import {
  BookOpen,
  LayoutDashboard,
  ListChecks,
  LogOut,
  PlusCircle,
  ShieldCheck,
  Users,
  Wrench,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import NotificationBell from '@/components/NotificationBell';
import type { Role } from '@/types';

interface NavItem {
  to: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  roles: Role[];
}

const NAV_ITEMS: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, roles: ['EMPLOYEE', 'ENGINEER', 'ADMIN'] },
  { to: '/incidents/new', label: 'Create Incident', icon: PlusCircle, roles: ['EMPLOYEE', 'ENGINEER', 'ADMIN'] },
  { to: '/incidents', label: 'My Incidents', icon: ListChecks, roles: ['EMPLOYEE'] },
  { to: '/incidents', label: 'Incident Queue', icon: ListChecks, roles: ['ENGINEER', 'ADMIN'] },
  { to: '/knowledge-base', label: 'Knowledge Base', icon: BookOpen, roles: ['EMPLOYEE', 'ENGINEER', 'ADMIN'] },
  { to: '/remediation-actions', label: 'Remediation Catalog', icon: Wrench, roles: ['ENGINEER', 'ADMIN'] },
  { to: '/admin/analytics', label: 'Analytics', icon: ShieldCheck, roles: ['ADMIN'] },
  { to: '/admin/users', label: 'User Management', icon: Users, roles: ['ADMIN'] },
];

export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) return null;

  const visibleItems = NAV_ITEMS.filter((item) => item.roles.includes(user.role));

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="flex h-screen bg-slate-50">
      <aside className="flex w-64 flex-shrink-0 flex-col border-r border-slate-200 bg-white">
        <div className="flex h-16 items-center gap-2 border-b border-slate-200 px-5">
          <ShieldCheck className="h-6 w-6 text-brand-600" />
          <span className="text-sm font-semibold text-slate-900">IT Service Desk</span>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          {visibleItems.map((item) => (
            <NavLink
              key={item.label}
              to={item.to}
              end={item.to === '/dashboard'}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100'
                }`
              }
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-slate-200 p-4">
          <div className="mb-3 flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-brand-100 text-sm font-semibold text-brand-700">
              {user.fullName.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-slate-900">{user.fullName}</p>
              <p className="truncate text-xs text-slate-500">{user.role}</p>
            </div>
          </div>
          <button onClick={handleLogout} className="btn-secondary w-full">
            <LogOut className="h-4 w-4" /> Sign out
          </button>
        </div>
      </aside>

      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="flex h-16 flex-shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6">
          <h1 className="text-lg font-semibold text-slate-900">Welcome back, {user.fullName.split(' ')[0]}</h1>
          <NotificationBell />
        </header>
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
