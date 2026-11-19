import { IconChevronDown, IconFileX, IconHourglassHigh, IconMapPin, IconRosetteDiscountCheck } from '@tabler/icons-react';
import { Link, useLocation } from 'react-router';
import { homePathFor } from '../auth/AuthContext';
import { Wordmark } from '../components/Logo';
import { shortPlace } from '../lib/listingText';
import { useIsNarrow, useIsPhone } from '../hooks/useMediaQuery';
import { useSearchPlace } from '../place/SearchPlaceContext';
import { AccountMenu, MenuSheet } from './AccountMenu';
import { linksFor, useNavCounts } from './navigation';

/** The receiver's location chip: where Find food looks; it opens the location picker. */
function LocationChip() {
  const { place, openPicker } = useSearchPlace();
  const text = place ? `Near ${shortPlace(place.label)}` : 'Choose a location';
  return (
    <button
      type="button"
      className="loc"
      aria-haspopup="dialog"
      aria-label={place ? `${text}, change location` : text}
      onClick={openPicker}
    >
      <IconMapPin size={17} aria-hidden="true" />
      <span className="loc-text">{text}</span>
      <IconChevronDown size={16} aria-hidden="true" />
    </button>
  );
}

/** The donor's verification state, always visible in the bar. */
function DonorStatusTag({ status }) {
  if (status === 'VERIFIED') {
    return (
      <span className="tag bar-tag">
        <IconRosetteDiscountCheck size={15} aria-hidden="true" /> Verified donor
      </span>
    );
  }
  if (status === 'NOT_VERIFIED') {
    return (
      <span className="tag bar-tag">
        <IconFileX size={15} aria-hidden="true" /> Not verified
      </span>
    );
  }
  return (
    <span className="tag bar-tag">
      <IconHourglassHigh size={15} aria-hidden="true" /> Waiting for verification
    </span>
  );
}

/**
 * The forest top bar with a rounded bottom edge: the wordmark, plain text links with a butter underline for the
 * open page and butter count badges, then the role's own item and the account menu. Admins get the darker bar
 * with an Admin tag. Below 1150 px the links move into the account menu; below 860 px the menu is a bottom sheet.
 */
export function TopBar({ user }) {
  const { pathname } = useLocation();
  const narrow = useIsNarrow();
  const phone = useIsPhone();
  const counts = useNavCounts(user.role);
  const isAdmin = user.role === 'ADMIN';
  const avatarClass = user.role === 'DONOR' ? '' : 'av-tang';

  return (
    <header className={`bar on-dark ${isAdmin ? 'admin' : ''}`}>
      <div className="bar-in">
        <Wordmark to={homePathFor(user)} size={32} />
        {isAdmin && <span className="tag bg-butter text-ink">Admin</span>}
        {!narrow && (
          <nav className="main-nav" aria-label={isAdmin ? 'Admin' : 'Main'}>
            {linksFor(user.role).map((link) => {
              const count = link.countKey ? counts[link.countKey] : 0;
              return (
                <Link key={link.to} to={link.to} aria-current={link.match(pathname) ? 'page' : undefined}>
                  {link.label}
                  {count > 0 && <span className="count">{count}</span>}
                </Link>
              );
            })}
          </nav>
        )}
        <div className="ml-auto flex min-w-0 items-center gap-3">
          {user.role === 'RECEIVER' && <LocationChip />}
          {user.role === 'DONOR' && !phone && <DonorStatusTag status={user.donor?.verificationStatus} />}
          {phone ? <MenuSheet user={user} /> : <AccountMenu user={user} showLinks={narrow} avatarClass={avatarClass} />}
        </div>
      </div>
    </header>
  );
}
