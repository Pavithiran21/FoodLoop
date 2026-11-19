import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Dialog } from '../../components/Dialog';
import { FormAlert, TextField } from '../../components/Fields';
import { reasonSchema } from '../../lib/schemas';

/**
 * An Admin decision that needs a reason the person will see: Reject a donor, Suspend an account. `onConfirm(reason)`
 * makes the request; it resolves when done, or throws the API error (a field message goes under the reason; anything
 * else, like "That account is already suspended", is shown at the top).
 */
export function ReasonDialog({ open, onClose, title, text, confirmLabel, onConfirm }) {
  return (
    <Dialog open={open} onClose={onClose} labelledBy="reason-title" describedBy="reason-text">
      <ReasonForm title={title} text={text} confirmLabel={confirmLabel} onConfirm={onConfirm} onClose={onClose} />
    </Dialog>
  );
}

function ReasonForm({ title, text, confirmLabel, onConfirm, onClose }) {
  const [problem, setProblem] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(reasonSchema), defaultValues: { reason: '' } });

  async function onSubmit(values) {
    setProblem(null);
    try {
      await onConfirm(values.reason.trim());
    } catch (error) {
      if (error.detail?.('reason')) {
        setError('reason', { type: 'server', message: error.detail('reason') });
      } else {
        setProblem(error.message);
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <h2 id="reason-title">{title}</h2>
      <p id="reason-text" className="muted sm mt-1.5 mb-3">
        {text}
      </p>
      <FormAlert message={problem} />
      <TextField
        id="reason-input"
        label="Reason"
        maxLength={200}
        hint="Up to 200 characters. They'll see it in the email."
        error={errors.reason?.message}
        {...register('reason')}
      />
      <div className="acts mt-1!">
        <button type="button" className="btn btn-line" onClick={onClose} data-autofocus>
          Cancel
        </button>
        <button type="submit" className="btn btn-danger" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : confirmLabel}
        </button>
      </div>
    </form>
  );
}
