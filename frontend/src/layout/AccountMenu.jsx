import {
  IconFlag,
  IconHistory,
  IconLayoutKanban,
  IconLogout,
  IconMenu2,
  IconReceipt,
  IconSearch,
  IconShieldCheck,
  IconUser,
  IconUsers,
} from '@tabler/icons-react';
import { useCallback, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useDismiss } from '../hooks/useDismiss';
import { initials, roleLabel } from '../lib/labels';
import { linksFor } from './navigation';

function LinkIcon({ to }) {
  switch (to) {
    case '/home':
      return <IconSearch size={18} aria-hidden="true" />;
    case '/claims':
      return <IconReceipt size={18} aria-hidden="true" />;
    case '/donor':
      return <IconLayoutKanban size={18} aria-hidden="true" />;
    case '/donor/history':
      return <IconHistory size={18} aria-hidden="true" />;
    case '/admin/donors':
      return <IconShieldCheck size={18} aria-hidden="true" />;
    case '/admin/reports':
      return <IconFlag size={18} aria-hidden="true" />;
    default:
      return <IconUsers size={18} aria-hidden="true" />;
  }
}

/** The menu's items: the page links (when the bar hides them), Profile and Log out. */
function MenuItems({ user, showLinks, onDone }) {
  const { signOut } = useAuth();
  const navigate = useNavigate();
  async function logOut() {
    onDone();
    await signOut();
    navigate('/', { replace: true });
  }
  return (
    <>
      {showLinks &&
        linksFor(user.role).map((link) => (
          <Link key={link.to} to={link.to} onClick={onDone}>
            <LinkIcon to={link.to} /> {link.label}
          </Link>
        ))}
      {user.role === 'RECEIVER' && !showLinks && (
        <Link to="/claims" onClick={onDone}>
          <IconReceipt size={18} aria-hidden="true" /> My claims
        </Link>
      )}
      <Link to="/profile" onClick={onDone}>
        <IconUser size={18} aria-hidden="true" /> {user.role === 'DONOR' ? 'Business profile' : 'Profile'}
      </Link>
      <hr />
      <button type="button" className="menu-item" onClick={logOut}>
        <IconLogout size={18} aria-hidden="true" /> Log out
      </button>
    </>
  );
}

function Who({ user }) {
  const business = user.donor?.businessName;
  return (
    <div className="px-4 pt-1.5 pb-2.5">
      <b>{business ?? user.name}</b>
      <div className="muted xs break-all">
        {roleLabel(user.role)} · {user.email}
      </div>
    </div>
  );
}

/** The avatar button and its dropdown (desktop and tablet). Below 1150 px the page links move in here. */
export function AccountMenu({ user, showLinks, avatarClass }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const close = useCallback(() => setOpen(false), []);
  useDismiss(ref, open, close);
  const letters = initials(user.name);
  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        className="avbtn"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`${letters}, account menu`}
        onClick={() => setOpen((value) => !value)}
      >
        <span className={`av ${avatarClass}`}>{letters}</span>
      </button>
      {open && (
        <nav className="menu right-0! top-[calc(100%+10px)]!" aria-label="Account">
          <Who user={user} />
          <hr />
          <MenuItems user={user} showLinks={showLinks} onDone={close} />
        </nav>
      )}
    </div>
  );
}

/** The phone menu: a bottom sheet over a scrim (Find food, My claims, Profile, Log out). */
export function MenuSheet({ user }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const close = useCallback(() => setOpen(false), []);
  useDismiss(ref, open, close);
  return (
    <>
      <button type="button" className="icon-btn" aria-label="Menu" aria-expanded={open} onClick={() => setOpen(true)}>
        <IconMenu2 size={20} aria-hidden="true" />
      </button>
      {open && (
        <>
          <div className="scrim" aria-hidden="true" />
          <nav ref={ref} className="sheet" aria-label="Menu">
            <div className="grab" />
            <Who user={user} />
            <MenuItems user={user} showLinks onDone={close} />
          </nav>
        </>
      )}
    </>
  );
}

