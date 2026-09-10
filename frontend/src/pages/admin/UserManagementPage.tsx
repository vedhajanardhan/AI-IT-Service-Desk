import { Construction } from 'lucide-react';

/**
 * User management needs an admin-facing user CRUD API (list/disable/change
 * role) that doesn't exist yet - registration exists, but there's no
 * "list all users" or "update a user's role" endpoint on the backend.
 * Flagged honestly rather than building a UI against endpoints that
 * don't exist.
 */
export default function UserManagementPage() {
  return (
    <div className="card flex flex-col items-center justify-center gap-3 p-16 text-center">
      <Construction className="h-8 w-8 text-slate-300" />
      <h2 className="text-sm font-semibold text-slate-700">User management - needs a backend admin API</h2>
      <p className="max-w-md text-sm text-slate-500">
        Listing, disabling, and changing roles for existing users needs new backend endpoints that don't
        exist yet (only self-registration does). This page is ready to build against them.
      </p>
    </div>
  );
}
