import { useQuery } from '@tanstack/react-query';
import { IconFlag } from '@tabler/icons-react';
import { Link, useNavigate } from 'react-router';
import { getReports } from '../../api/admin';
import { keys } from '../../api/queryClient';
import { Pager } from '../../components/Pager';
import { EmptyState, ErrorPanel, Skeleton } from '../../components/StatePanels';
import { Tabs } from '../../components/Tabs';
import { usePageTitle } from '../../hooks/usePageTitle';
import { pageFrom, useUrlFilters } from '../../hooks/useUrlFilters';
import { REPORT_STATUSES, partyText, reportHandle, reportReasonLabel, reportStatus } from '../../lib/labels';
import { browserZone, formatDay } from '../../lib/time';
import { useNavCounts } from '../../layout/navigation';

/** Reports (/admin/reports): All, Open, Dismissed and Actioned; open reports first, oldest first. */
export function ReportsPage() {
  usePageTitle('Reports');
  const navigate = useNavigate();
  const [searchParams, update] = useUrlFilters();
  const status = REPORT_STATUSES.some((entry) => entry.value === searchParams.get('status')) ? searchParams.get('status') : '';
  const page = pageFrom(searchParams);
  const { openReports } = useNavCounts('ADMIN');
  const reports = useQuery({ queryKey: keys.adminReports(status, page), queryFn: () => getReports(status || undefined, page) });
  const zone = browserZone();

  return (
    <div className="card overflow-hidden">
      <div className="row-flex px-5 py-4">
        <h1 className="fill text-[22px]">Reports</h1>
        <Tabs
          label="Report status"
          value={status}
          onChange={(value) => update({ status: value })}
          tabs={[
            { value: '', label: 'All' },
            { value: 'OPEN', label: openReports > 0 ? `Open · ${openReports}` : 'Open' },
            { value: 'DISMISSED', label: 'Dismissed' },
            { value: 'ACTIONED', label: 'Actioned' },
          ]}
        />
      </div>
      {reports.isPending && (
        <div className="px-5 pb-5">
          <Skeleton label="Loading reports…" blocks={0} lines={3} />
        </div>
      )}
      {reports.isError && <ErrorPanel error={reports.error} title="Couldn't load reports" onRetry={() => reports.refetch()} />}
      {reports.data && reports.data.totalItems === 0 && (
        <EmptyState art={<IconFlag size={36} color="var(--muted)" className="mx-auto" aria-hidden="true" />} title="No reports">
          {status === 'OPEN' ? 'Nothing is waiting for you.' : 'Reports from donors and receivers appear here.'}
        </EmptyState>
      )}
      {reports.data && reports.data.totalItems > 0 && (
        <>
          <div className="tablewrap">
            <table className="tbl cards">
              <thead>
                <tr>
                  <th scope="col">Report</th>
                  <th scope="col">About</th>
                  <th scope="col">From</th>
                  <th scope="col">Reason</th>
                  <th scope="col">Date</th>
                  <th scope="col">Status</th>
                </tr>
              </thead>
              <tbody>
                {reports.data.items.map((report) => {
                  const tag = reportStatus(report.status);
                  return (
                    <tr key={report.id} className="click" onClick={() => navigate(`/admin/reports/${report.id}`)}>
                      <td>
                        <Link className="row-link" to={`/admin/reports/${report.id}`}>
                          <b>{reportHandle(report.id)}</b>
                        </Link>
                      </td>
                      <td data-l="About">{partyText(report.about)}</td>
                      <td data-l="From">{report.from.name}</td>
                      <td data-l="Reason">{reportReasonLabel(report.reason)}</td>
                      <td data-l="Date">{formatDay(report.createdAt, zone)}</td>
                      <td>
                        <span className={`tag ${tag.className}`}>{tag.label}</span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pager data={reports.data} onPage={(next) => update({ page: next }, { replace: false })} note="open reports first, oldest first" />
        </>
      )}
    </div>
  );
}
