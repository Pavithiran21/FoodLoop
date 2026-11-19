import { Link } from 'react-router';

/**
 * The FoodLoop mark: a tangerine apple drawn as a loop arrow (food going round to someone else), a light-green
 * leaf and a butter seed in the centre, on a forest rounded tile.
 */
export function LogoMark({ size = 32, title }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" role={title ? 'img' : undefined} aria-hidden={title ? undefined : 'true'}>
      {title && <title>{title}</title>}
      <rect width="64" height="64" rx="20" fill="#1f4a2c" />
      <path
        d="M32 21c-4-3-13-3-16 5-3 9 2 20 9 23 3 1 5 0 7-1 2 1 4 2 7 1 7-3 12-14 9-23-1-4-4-6-7-6.5"
        fill="none"
        stroke="#f2862e"
        strokeWidth="5"
        strokeLinecap="round"
      />
      <path d="M41 15.5l2 5-5.4 1" fill="none" stroke="#f2862e" strokeWidth="4.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M32 22c0-6 3-10 9-11-1 6-4 9-9 11z" fill="#8fd18f" />
      <circle cx="32" cy="36" r="4" fill="#ffe6a3" />
    </svg>
  );
}

/** The mark and the "Food" + tangerine "Loop" wordmark, as a link home. */
export function Wordmark({ to = '/', size = 32, className = '' }) {
  return (
    <Link to={to} className={`word ${className}`} aria-label="FoodLoop home">
      <LogoMark size={size} />
      <span>
        Food<em>Loop</em>
      </span>
    </Link>
  );
}
