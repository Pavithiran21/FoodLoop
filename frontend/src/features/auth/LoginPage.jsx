import { zodResolver } from '@hookform/resolvers/zod';
import { IconClockX, IconHandStop, IconUserOff } from '@tabler/icons-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { login } from '../../api/auth';
import { useAuth } from '../../auth/AuthContext';
import { FormAlert, TextField } from '../../components/Fields';
import { FoodPicture } from '../../components/FoodPicture';
import { Wordmark } from '../../components/Logo';
import { usePageTitle } from '../../hooks/usePageTitle';
import { loginSchema } from '../../lib/schemas';
import { useDemoAvailable } from './useDemoAvailable';

/** The forest story panel: how FoodLoop works in three steps (no live totals, which the MVP doesn't have). */
function StoryPanel() {
  return (
    <div className="story on-dark">
      <Wordmark size={38} />
      <h2>Surplus food, picked up before it's wasted.</h2>
      <p className="max-w-[420px]">
        Bakeries, restaurants and shops list what's left at closing time. People and charities nearby claim it and
        collect it.
      </p>
      <ol className="how">
        <li>
          <span className="n">1</span>
          <span>
            <b>A donor lists food</b>What it is, how much, and when to pick it up.
          </span>
        </li>
        <li>
          <span className="n">2</span>
          <span>
            <b>One receiver claims it</b>First claim wins; nobody else can take it.
          </span>
        </li>
        <li>
          <span className="n">3</span>
          <span>
            <b>They collect it in time</b>Unclaimed food closes by itself when the window ends.
          </span>
        </li>
      </ol>
      <span className="story-art">
        <FoodPicture category="FRUIT_VEG" width={200} />
      </span>
    </div>
  );
}

/** "Your account is suspended: <reason>" from the API, shown as its own card with the reason. */
function SuspendedNotice({ message }) {
  const reason = message.replace(/^Your account is suspended:\s*/, '').replace(/\.$/, '');
  return (
    <div className="alert a-err mb-3.5" role="alert">
      <IconUserOff size={20} aria-hidden="true" />
      <div>
        <b>Your account is suspended.</b> Reason: {reason}. If you think this is a mistake, reply to the suspension
        email.
      </div>
    </div>
  );
}

/** Log in, the start page at "/" (UI-UX screen 1). Signed-in people are sent to their home by GuestOnly. */
export function LoginPage() {
  usePageTitle('Log in');
  const { signIn, sessionEnded } = useAuth();
  const demoAvailable = useDemoAvailable();
  const [problem, setProblem] = useState(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } });

  async function onSubmit(values) {
    setProblem(null);
    try {
      signIn(await login(values.email.trim(), values.password));
    } catch (error) {
      setProblem(error);
    }
  }

  return (
    <div className="auth-page">
      <div className="card join">
        <main className="form-side">
          <div className="box">
            <h1 className="text-[30px]">Log in</h1>
            <p className="muted mt-1 mb-5">Welcome back. Find food near you or list what's left today.</p>
            {sessionEnded && !problem && (
              <div className="alert a-tang mb-3.5" role="status">
                <IconClockX size={20} aria-hidden="true" />
                <div>
                  <b>Your session has ended.</b> Log in again.
                </div>
              </div>
            )}
            {problem?.code === 'ACCOUNT_SUSPENDED' ? (
              <SuspendedNotice message={problem.message} />
            ) : (
              <FormAlert message={problem?.message} icon={problem?.status === 429 ? IconHandStop : undefined} />
            )}
            <form onSubmit={handleSubmit(onSubmit)} noValidate>
              <TextField
                id="login-email"
                label="Email"
                type="email"
                autoComplete="email"
                error={errors.email?.message}
                {...register('email')}
              />
              <TextField
                id="login-password"
                label="Password"
                type="password"
                autoComplete="current-password"
                error={errors.password?.message}
                {...register('password')}
              />
              <button type="submit" className="btn btn-main btn-block" disabled={isSubmitting}>
                {isSubmitting ? 'Logging in…' : 'Log in'}
              </button>
            </form>
            <div className="row-flex mt-3.5 justify-between">
              <Link to="/forgot-password" className="sm">
                Forgot password?
              </Link>
              {demoAvailable && (
                <Link to="/demo" className="sm">
                  Try the demo
                </Link>
              )}
            </div>
            <hr className="divider" />
            <p className="sm">
              New to FoodLoop? <Link to="/signup">Create an account</Link>
            </p>
          </div>
        </main>
        <StoryPanel />
      </div>
    </div>
  );
}
