import { zodResolver } from '@hookform/resolvers/zod';
import { IconCircleCheck, IconClockX } from '@tabler/icons-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useSearchParams } from 'react-router';
import { resetPassword } from '../../api/auth';
import { FormAlert, TextField } from '../../components/Fields';
import { usePageTitle } from '../../hooks/usePageTitle';
import { resetSchema } from '../../lib/schemas';
import { AuthCard } from './AuthCard';

/**
 * /reset-password?token=… from the email. A used, expired or broken link shows "This link can't be used any
 * more" with "Send a new one". A reset logs out every session, so the person logs in again afterwards.
 */
export function ResetPasswordPage() {
  usePageTitle('Choose a new password');
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const [done, setDone] = useState(false);
  const [linkDead, setLinkDead] = useState(!token);
  const [problem, setProblem] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(resetSchema), defaultValues: { password: '', confirm: '' } });

  async function onSubmit(values) {
    setProblem(null);
    try {
      await resetPassword(token, values.password);
      setDone(true);
    } catch (error) {
      if (error.code === 'RESET_LINK_EXPIRED' || error.detail('token')) {
        setLinkDead(true);
      } else if (error.detail('password')) {
        setError('password', { type: 'server', message: error.detail('password') });
      } else {
        setProblem(error.message);
      }
    }
  }

  if (done) {
    return (
      <AuthCard>
        <div className="alert a-leaf" role="status">
          <IconCircleCheck size={20} aria-hidden="true" />
          <div>
            <b>Your password was changed.</b> You were logged out everywhere; log in with the new password.
          </div>
        </div>
        <Link className="btn btn-main btn-block mt-4" to="/">
          Log in
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard>
      <h1 className="text-[22px]">Choose a new password</h1>
      <p className="muted sm mt-1 mb-4">At least 8 characters, with a number.</p>
      {linkDead ? (
        <div className="alert a-err" role="alert">
          <IconClockX size={20} aria-hidden="true" />
          <div>
            <b>This link can't be used any more.</b> Links work once and last 30 minutes.{' '}
            <Link to="/forgot-password">Send a new one</Link>
          </div>
        </div>
      ) : (
        <>
          <FormAlert message={problem} />
          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <TextField
              id="reset-password"
              label="New password"
              type="password"
              autoComplete="new-password"
              error={errors.password?.message}
              {...register('password')}
            />
            <TextField
              id="reset-confirm"
              label="Confirm"
              type="password"
              autoComplete="new-password"
              error={errors.confirm?.message}
              {...register('confirm')}
            />
            <button type="submit" className="btn btn-main btn-block" disabled={isSubmitting}>
              {isSubmitting ? 'Saving…' : 'Save new password'}
            </button>
          </form>
        </>
      )}
    </AuthCard>
  );
}
