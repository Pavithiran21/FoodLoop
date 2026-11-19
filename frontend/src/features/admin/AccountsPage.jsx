import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { getAccounts, reinstateAccount, suspendAccount } from '../../api/admin';
import { invalidateAdminViews, keys } from '../../api/queryClient';
import { Pager } from '../../components/Pager';
import { EmptyState, ErrorPanel, Skeleton } from '../../components/StatePanels';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { usePageTitle } from '../../hooks/usePageTitle';
import { pageFrom, useUrlFilters } from '../../hooks/useUrlFilters';
import { ACCOUNT_STATUSES, ROLES, accountStatus, roleLabel } from '../../lib/labels';
import { useToast } from '../../toast/ToastContext';
import { ReasonDialog } from './ReasonDialog';

/** What an Admin can do with one account: Suspend, Reinstate, Review (donors), or why nothing. */
function RowActions({ account, onSuspend, onReinstate, busy }) {
  if (account.role === 'ADMIN') {
    return <span className="muted xs">Seed script only</span>;
  }
  if (account.demo) {
    return <span className="muted xs">Demo account</span>;
  }
  const review = ['WAITING', 'NOT_VERIFIED'].includes(account.status);
  return (
    <div className="row-flex justify-end gap-1.5! max-[859px]:justify-start">
      {review && (
        <Link className="btn btn-soft btn-sm" to={`/admin/donors?status=${account.status}`}>
          Review<span className="sr-only"> {account.businessName ?? account.name}</span>
        </Link>
      )}
      {account.status === 'SUSPENDED' ? (
        <button type="button" className="btn btn-soft btn-sm" onClick={() => onReinstate(account)} disabled={busy}>
          Reinstate<span className="sr-only"> {account.businessName ?? account.name}</span>
        </button>
      ) : (
        account.canSuspend && (
          <button
            type="button"
            className={`btn btn-sm ${account.openReports > 0 ? 'btn-danger' : 'btn-line'}`}
            onClick={() => onSuspend(account)}
          >
            Suspend<span className="sr-only"> {account.businessName ?? account.name}</span>
          </button>
        )
      )}
    </div>
  );
}

/** Accounts (/admin/accounts): search by name or email, filter by role and status; suspend and reinstate. */
export function AccountsPage() {
  usePageTitle('Accounts');
  const { showToast, showError } = useToast();
  const [searchParams, update] = useUrlFilters();
  const role = searchParams.get('role') ?? '';
  const status = searchParams.get('status') ?? '';
  const page = pageFrom(searchParams);
  const [query, setQuery] = useState(searchParams.get('q') ?? '');
  const debouncedQuery = useDebouncedValue(query.trim(), 350);
  const [suspending, setSuspending] = useState(null);

  // The search box types freely; the URL (and the search) follow once typing pauses.
  useEffect(() => {
    if ((new URLSearchParams(window.location.search).get('q') ?? '') !== debouncedQuery) {
      update({ q: debouncedQuery });
    }
  }, [debouncedQuery, update]);

  const params = { q: searchParams.get('q') ?? undefined, role: role || undefined, status: status || undefined, page };
  const accounts = useQuery({ queryKey: keys.accounts(params), queryFn: () => getAccounts(params) });

  const reinstate = useMutation({
    mutationFn: (account) => reinstateAccount(account.id),
    onSuccess: () => showToast('Account reinstated'),
    // "That account isn't suspended." when another Admin reinstated it first.
    onError: (error) => showError(error.message),
    onSettled: invalidateAdminViews,
  });

  return (
    <div className="card overflow-hidden">
      <div className="row-flex px-5 py-4">
        <h1 className="fill text-[22px]">Accounts</h1>
        <input
          className="inp inp-sm w-[220px]! max-[859px]:w-full!"
          type="search"
          aria-label="Search accounts"
          placeholder="Name or email"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <select className="inp inp-sm" aria-label="Role" value={role} onChange={(event) => update({ role: event.target.value })}>
          <option value="">All roles</option>
          {ROLES.map((entry) => (
            <option key={entry.value} value={entry.value}>
              {entry.label}
            </option>
          ))}
        </select>
        <select className="inp inp-sm" aria-label="Status" value={status} onChange={(event) => update({ status: event.target.value })}>
          <option value="">All statuses</option>
          {ACCOUNT_STATUSES.map((entry) => (
            <option key={entry.value} value={entry.value}>
              {entry.label}
            </option>
          ))}
        </select>
      </div>
      {accounts.isPending && (
        <div className="px-5 pb-5">
          <Skeleton label="Loading accounts…" blocks={0} lines={4} />
        </div>
      )}
      {accounts.isError && <ErrorPanel error={accounts.error} title="Couldn't load accounts" onRetry={() => accounts.refetch()} />}
      {accounts.data && accounts.data.totalItems === 0 && <EmptyState title="No accounts match">Try another name, role or status.</EmptyState>}
      {accounts.data && accounts.data.totalItems > 0 && (
        <>
          <div className="tablewrap">
            <table className="tbl cards">
              <thead>
                <tr>
                  <th scope="col">Account</th>
                  <th scope="col">Role</th>
                  <th scope="col">Status</th>
                  <th scope="col" className="num">
                    Open reports
                  </th>
                  <th scope="col">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {accounts.data.items.map((account) => {
                  const tag = accountStatus(account.status);
                  return (
                    <tr key={account.id}>
                      <td>
                        <b>{account.businessName ?? account.name}</b>
                        <div className="muted xs break-all">
                          {account.businessName ? `${account.name} · ` : ''}
                          {account.email}
                        </div>
                      </td>
                      <td data-l="Role">{roleLabel(account.role)}</td>
                      <td>
                        <span className={`tag ${tag.className}`}>{tag.label}</span>
                        {account.status === 'SUSPENDED' && account.suspendedReason && (
                          <div className="muted xs mt-1">{account.suspendedReason}</div>
                        )}
                      </td>
                      <td className="num" data-l="Open reports">
                        {account.role === 'ADMIN' ? '—' : account.openReports}
                      </td>
                      <td className="num">
                        <RowActions account={account} onSuspend={setSuspending} onReinstate={reinstate.mutate} busy={reinstate.isPending} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pager data={accounts.data} onPage={(next) => update({ page: next }, { replace: false })} />
        </>
      )}

      <ReasonDialog
        open={Boolean(suspending)}
        onClose={() => setSuspending(null)}
        title={`Suspend ${suspending?.businessName ?? suspending?.name}?`}
        text="Their open listings are withdrawn, their active claims are cancelled, their open reports are marked Actioned, and they can't log in. They get an email with your reason."
        confirmLabel="Suspend"
        onConfirm={async (reason) => {
          try {
            await suspendAccount(suspending.id, reason);
            showToast('Account suspended');
            setSuspending(null);
          } finally {
            invalidateAdminViews();
          }
        }}
      />
    </div>
  );
}
