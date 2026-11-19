import { useMutation, useQuery } from '@tanstack/react-query';
import { IconCheck, IconRosetteDiscountCheck } from '@tabler/icons-react';
import { useState } from 'react';
import { getDonorCards, getDonorCounts, rejectDonor, verifyDonor } from '../../api/admin';
import { invalidateAdminViews, keys } from '../../api/queryClient';
import { Pager } from '../../components/Pager';
import { EmptyState, ErrorPanel, Skeleton } from '../../components/StatePanels';
import { Tabs } from '../../components/Tabs';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import { pageFrom, useUrlFilters } from '../../hooks/useUrlFilters';
import { DONOR_STATUSES, initials } from '../../lib/labels';
import { browserZone, timeAgo } from '../../lib/time';
import { useToast } from '../../toast/ToastContext';
import { ReasonDialog } from './ReasonDialog';

const EMPTY = {
  WAITING: ['No donors waiting', 'New donor sign-ups appear here for you to verify.'],
  VERIFIED: ['No verified donors yet', 'Donors you verify appear here.'],
  NOT_VERIFIED: ['No donors turned down', "Donors you don't verify appear here until they ask for review again."],
};

/** When the card's donor signed up or asked again ("Asked for review again 1 hour ago"). */
function cardWhen(card, now) {
  const ago = timeAgo(card.createdAt, browserZone(), now);
  if (card.status === 'WAITING' && card.rejectionReason) {
    return `Asked for review again ${ago}`;
  }
  return `Signed up ${ago}`;
}

function DonorCard({ card, now, onVerify, onReject, busy }) {
  return (
    <article className="vcard">
      <div className="row-flex">
        <span className={`av ${card.status === 'WAITING' ? '' : 'av-ready'}`} aria-hidden="true">
          {initials(card.businessName)}
        </span>
        <div className="fill">
          <h2 className="font-sans! text-[15px]">{card.businessName}</h2>
          <div className="muted xs">{cardWhen(card, now)}</div>
        </div>
      </div>
      <dl className="dl">
        <dt>Contact</dt>
        <dd>{card.contactName}</dd>
        <dt>Email</dt>
        <dd>{card.email}</dd>
        <dt>Address</dt>
        <dd>{card.pickupAddress}</dd>
        <dt>FSSAI no.</dt>
        <dd>{card.fssaiNo ?? 'Not given'}</dd>
        {card.rejectionReason && (
          <>
            <dt>{card.status === 'NOT_VERIFIED' ? 'Reason' : 'Last reason'}</dt>
            <dd>"{card.rejectionReason}"</dd>
          </>
        )}
      </dl>
      {card.status === 'WAITING' && (
        <div className="row-flex">
          <button type="button" className="btn btn-main btn-sm fill" onClick={() => onVerify(card)} disabled={busy}>
            <IconCheck size={16} aria-hidden="true" /> Verify<span className="sr-only"> {card.businessName}</span>
          </button>
          <button type="button" className="btn btn-line btn-sm fill" onClick={() => onReject(card)} disabled={busy}>
            Reject<span className="sr-only"> {card.businessName}</span>
          </button>
        </div>
      )}
    </article>
  );
}

/** Verify donors (/admin/donors): Waiting, Verified and Not verified tabs, oldest first. */
export function VerifyDonorsPage() {
  usePageTitle('Verify donors');
  const now = useNow();
  const { showToast, showError } = useToast();
  const [searchParams, update] = useUrlFilters();
  const status = DONOR_STATUSES.some((entry) => entry.value === searchParams.get('status')) ? searchParams.get('status') : 'WAITING';
  const page = pageFrom(searchParams);
  const [rejecting, setRejecting] = useState(null);
  const counts = useQuery({ queryKey: keys.donorCounts, queryFn: getDonorCounts });
  const cards = useQuery({ queryKey: keys.adminDonors(status, page), queryFn: () => getDonorCards(status, page) });

  const verify = useMutation({
    mutationFn: (card) => verifyDonor(card.userId),
    onSuccess: (_, card) => showToast(`Donor verified · ${card.businessName} was emailed`),
    // "This donor isn't waiting for verification any more." when another Admin decided first.
    onError: (error) => showError(error.message),
    onSettled: invalidateAdminViews,
  });

  return (
    <div className="card overflow-hidden">
      <div className="row-flex px-6 pt-5">
        <h1 className="fill text-[24px]">Verify donors</h1>
        <Tabs
          label="Donor status"
          value={status}
          onChange={(value) => update({ status: value === 'WAITING' ? null : value })}
          tabs={DONOR_STATUSES.map((entry) => ({
            value: entry.value,
            label: counts.data ? `${entry.tab} · ${counts.data[entry.countKey]}` : entry.tab,
          }))}
        />
      </div>
      <div className="p-6 max-[859px]:p-4">
        {cards.isPending && <Skeleton label="Loading donors…" blocks={3} columns={3} lines={0} />}
        {cards.isError && <ErrorPanel error={cards.error} title="Couldn't load donors" onRetry={() => cards.refetch()} />}
        {cards.data && cards.data.totalItems === 0 && (
          <EmptyState art={<IconRosetteDiscountCheck size={36} color="var(--leaf-d)" className="mx-auto" aria-hidden="true" />} title={EMPTY[status][0]}>
            {EMPTY[status][1]}
          </EmptyState>
        )}
        {cards.data && cards.data.totalItems > 0 && (
          <div className="verify">
            {cards.data.items.map((card) => (
              <DonorCard key={card.userId} card={card} now={now} onVerify={verify.mutate} onReject={setRejecting} busy={verify.isPending} />
            ))}
          </div>
        )}
      </div>
      <Pager data={cards.data} onPage={(next) => update({ page: next }, { replace: false })} note="oldest first" />

      <ReasonDialog
        open={Boolean(rejecting)}
        onClose={() => setRejecting(null)}
        title={`Reject ${rejecting?.businessName}?`}
        text="They can't list food yet. They get an email with your reason, and can fix their details and ask for review again."
        confirmLabel="Reject"
        onConfirm={async (reason) => {
          try {
            await rejectDonor(rejecting.userId, reason);
            showToast(`Donor not verified · ${rejecting.businessName} was emailed`);
            setRejecting(null);
          } finally {
            invalidateAdminViews();
          }
        }}
      />
    </div>
  );
}
