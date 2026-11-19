import { Wordmark } from '../../components/Logo';

/**
 * The plain one-card pages (Sign up, /demo, Forgot and Reset password): the wordmark on the sage page and one
 * white card. Log in has its own layout with the forest story panel.
 */
export function AuthCard({ children, wide = false }) {
  return (
    <div className="auth-page">
      <div className={wide ? 'mx-auto max-w-[780px]' : 'single'}>
        <div className="mb-5">
          <Wordmark size={38} />
        </div>
        <main className="card pad sm:p-8!">{children}</main>
      </div>
    </div>
  );
}
