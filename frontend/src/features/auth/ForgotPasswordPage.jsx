import { zodResolver } from '@hookform/resolvers/zod';
import { IconMail } from '@tabler/icons-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { forgotPassword } from '../../api/auth';
import { FormAlert, TextField } from '../../components/Fields';
import { usePageTitle } from '../../hooks/usePageTitle';
import { forgotSchema } from '../../lib/schemas';
import { AuthCard } from './AuthCard';

/** Forgot password: the same answer whether or not the email has an account, so nobody can probe for accounts. */
export function ForgotPasswordPage() {
  usePageTitle('Forgot password');
  const [sent, setSent] = useState(false);
  const [problem, setProblem] = useState(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(forgotSchema), defaultValues: { email: '' } });

  async function onSubmit(values) {
    setProblem(null);
    try {
      await forgotPassword(values.email.trim());
      setSent(true);
    } catch (error) {
      setProblem(error.message);
    }
  }

  return (
    <AuthCard>
      <h1 className="text-[22px]">Forgot your password?</h1>
      <p className="muted sm mt-1 mb-4">We'll email you a link to choose a new one. It works once and lasts 30 minutes.</p>
      <FormAlert message={problem} />
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <TextField id="forgot-email" label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <button type="submit" className="btn btn-main btn-block" disabled={isSubmitting}>
          {isSubmitting ? 'Sending…' : 'Send reset link'}
        </button>
      </form>
      {sent && (
        <div className="alert a-leaf mt-3" role="status">
          <IconMail size={20} aria-hidden="true" />
          <div>
            <b>Check your inbox.</b> If an account uses that email, a link is on its way.
          </div>
        </div>
      )}
      <p className="sm mt-4">
        <Link to="/">Back to Log in</Link>
      </p>
    </AuthCard>
  );
}
