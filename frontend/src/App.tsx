import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '@/context/AuthContext';
import { ProtectedRoute } from '@/components/ProtectedRoute';
import AppLayout from '@/layouts/AppLayout';
import LoginPage from '@/pages/LoginPage';
import RegisterPage from '@/pages/RegisterPage';
import DashboardPage from '@/pages/DashboardPage';
import CreateIncidentPage from '@/pages/CreateIncidentPage';
import IncidentListPage from '@/pages/IncidentListPage';
import IncidentDetailsPage from '@/pages/IncidentDetailsPage';
import KnowledgeBasePage from '@/pages/KnowledgeBasePage';
import RemediationActionsPage from '@/pages/RemediationActionsPage';
import AnalyticsPage from '@/pages/admin/AnalyticsPage';
import UserManagementPage from '@/pages/admin/UserManagementPage';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/incidents" element={<IncidentListPage />} />
              <Route path="/incidents/new" element={<CreateIncidentPage />} />
              <Route path="/incidents/:id" element={<IncidentDetailsPage />} />
              <Route path="/knowledge-base" element={<KnowledgeBasePage />} />

              <Route element={<ProtectedRoute allowedRoles={['ENGINEER', 'ADMIN']} />}>
                <Route path="/remediation-actions" element={<RemediationActionsPage />} />
              </Route>

              <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
                <Route path="/admin/analytics" element={<AnalyticsPage />} />
                <Route path="/admin/users" element={<UserManagementPage />} />
              </Route>
            </Route>
          </Route>

          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
