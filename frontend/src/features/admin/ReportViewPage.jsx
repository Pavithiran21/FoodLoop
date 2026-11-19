import { useMutation, useQuery } from '@tanstack/react-query';
import { IconArrowLeft } from '@tabler/icons-react';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { dismissReport, getReport, suspendFromReport } from '../../api/admin';
import { invalidateAdminViews, keys } from '../../api/queryClient';
import { Skeleton } from '../../components/StatePanels';
import { usePageTitle } from '../../hooks/usePageTitle';
import { partyText, reportHandle, reportReasonLabel, reportStatus } from '../../lib/labels';
import { browserZone, formatDay, formatTime } from '../../lib/time';
import { NotFoundPage, ServerErrorPanel } from '../../pages/ErrorPages';
import { useToast } from '../../toast/ToastContext';
import { ReasonDialog } from './ReasonDialog';

/** "1 dismissed, 2 actioned", or "None". */
function pastReportsText(pastReports) {
  if (pastReports.length === 0) {
    return 'None';
  }
  const counts = {};
  pastReports.forEach((report) => {
    counts[report.status] = (counts[report.status] ?? 0) + 1;
  });
  return ['OPEN', 'DISMISSED', 'ACTIONED']
    .filter((status) => counts[status])
    .map((status) => `${counts[status]} ${status.toLowerCase()}`)
    .join(', ');
}

/** One report (/admin/reports/:id): what it is about, who sent it, earlier reports; Suspend or Dismiss while open. */
export function ReportViewPage() {
  const { id } = useParams();
  const { showToast, showError } = useToast();
  const [suspendOpen, setSuspendOpen] = useState(false);
  const reportQuery = useQuery({ queryKey: keys.adminReport(id), queryFn: () => getReport(id) });
  usePageTitle(`Report ${reportHandle(id)}`);
  const zone = browserZone();

  const dismiss = useMutation({
    mutationFn: () => dismissReport(id),
    onSuccess: () => showToast('Report dismissed'),
    // "This report is already settled." when another Admin got there first.
    onError: (error) => showError(error.message),
    onSettled: invalidateAdminViews,
  });

  if (reportQuery.isPending) {
    return (
      <div className="card pad mx-auto max-w-[820px]">
        <Skeleton label="Loading the report…" blocks={0} lines={4} />
      </div>
    );
  }
  if (reportQuery.isError) {
    return reportQuery.error.status === 404 ? (
      <NotFoundPage />
    ) : (
      <ServerErrorPanel error={reportQuery.error} onRetry={() => reportQuery.refetch()} />
    );
  }

  const { report, details, resolvedAt, pastReports, canSuspend } = reportQuery.data;
  const tag = reportStatus(report.status);
  const open = report.status === 'OPEN';

  return (
    <div className="card pad mx-auto max-w-[820px] sm:p-7!">
      <Link to="/admin/reports" className="back">
        <IconArrowLeft size={16} aria-hidden="true" /> Reports
      </Link>
      <div className="row-flex mt-2.5">
        <h1 className="fill text-[22px]">
          Report {reportHandle(report.id)} · {reportReasonLabel(report.reason)}
        </h1>
        <span className={`tag ${tag.className}`}>{tag.label}</span>
      </div>
      <dl className="dl">
        <dt>About</dt>
        <dd>
          {partyText(report.about)}
          {report.listing && ` · listing "${report.listing.title}"`}
        </dd>
        <dt>From</dt>
        <dd>
          {report.from.name}, {formatDay(report.createdAt, zone)}, {formatTime(report.createdAt, zone)}
        </dd>
        {report.claim && (
          <>
            <dt>Claim</dt>
            <dd>{report.claim.number}</dd>
          </>
        )}
        <dt>Details</dt>
        <dd className="font-semibold!">{details ? `"${details}"` : 'None given'}</dd>
        <dt>Past reports</dt>
        <dd>{pastReportsText(pastReports)}</dd>
        {resolvedAt && (
          <>
            <dt>Settled</dt>
            <dd>
              {formatDay(resolvedAt, zone)}, {formatTime(resolvedAt, zone)}
            </dd>
          </>
        )}
      </dl>
      {open && (
        <div className="row-flex">
          {canSuspend && (
            <button type="button" className="btn btn-danger btn-sm" onClick={() => setSuspendOpen(true)}>
              Suspend {report.about.name}
            </button>
          )}
          <button type="button" className="btn btn-line btn-sm" onClick={() => dismiss.mutate()} disabled={dismiss.isPending}>
            {dismiss.isPending ? 'Dismissing…' : 'Dismiss report'}
          </button>
        </div>
      )}
      {open && !canSuspend && (
        <p className="muted xs mt-2.5">This account can't be suspended from here (it is already suspended, deleted or a demo account).</p>
      )}

      <ReasonDialog
        open={suspendOpen}
        onClose={() => setSuspendOpen(false)}
        title={`Suspend ${report.about.name}?`}
        text="Their open listings are withdrawn, their active claims are cancelled, their open reports are marked Actioned, and they can't log in. They get an email with your reason."
        confirmLabel="Suspend"
        onConfirm={async (reason) => {
          try {
            await suspendFromReport(report.id, reason);
            showToast('Account suspended');
            setSuspendOpen(false);
          } finally {
            invalidateAdminViews();
          }
        }}
      />
    </div>
  );
}
