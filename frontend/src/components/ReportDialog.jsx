import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { reportSchema } from '../lib/schemas';
import { Dialog } from './Dialog';
import { FieldError, FormAlert, TextField } from './Fields';

/**
 * The report form for both kinds of report: a receiver reporting a listing ("What's wrong?") and a donor reporting
 * the receiver of a claim ("What happened?"). `send({ reason, details })` makes the request; a second report on the
 * same listing or claim comes back as 409 with the reason's message ("You've already reported this listing.").
 */
export function ReportDialog({ open, onClose, title, subtitle, legend, reasons, send, onSent }) {
  return (
    <Dialog open={open} onClose={onClose} labelledBy="report-title">
      <ReportForm title={title} subtitle={subtitle} legend={legend} reasons={reasons} send={send} onSent={onSent} onClose={onClose} />
    </Dialog>
  );
}

function ReportForm({ title, subtitle, legend, reasons, send, onSent, onClose }) {
  const [problem, setProblem] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(reportSchema), defaultValues: { reason: '', details: '' } });

  async function onSubmit(values) {
    setProblem(null);
    try {
      await send({ reason: values.reason, details: values.details.trim() || null });
      onSent({ already: false });
    } catch (error) {
      if (error.code === 'ALREADY_REPORTED') {
        onSent({ already: true, message: error.message });
        return;
      }
      if (error.detail('reason') || error.detail('details')) {
        ['reason', 'details'].forEach((field) => {
          if (error.detail(field)) {
            setError(field, { type: 'server', message: error.detail(field) });
          }
        });
        return;
      }
      setProblem(error.message);
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <h2 id="report-title" className="mb-1">
        {title}
      </h2>
      {subtitle && <p className="muted sm mb-3">{subtitle}</p>}
      <FormAlert message={problem} />
      <fieldset className="plain-fieldset" aria-describedby={errors.reason ? 'report-reason-error' : undefined}>
        <legend className="flabel">{legend}</legend>
        {reasons.map((reason) => (
          <label key={reason.value} className="chk">
            <input type="radio" value={reason.value} {...register('reason')} /> {reason.label}
          </label>
        ))}
      </fieldset>
      <FieldError id="report-reason-error" message={errors.reason?.message} />
      <TextField
        as="textarea"
        id="report-details"
        label="Tell us more (optional)"
        maxLength={500}
        error={errors.details?.message}
        {...register('details')}
      />
      <div className="acts">
        <button type="button" className="btn btn-line" onClick={onClose} data-autofocus>
          Cancel
        </button>
        <button type="submit" className="btn btn-main" disabled={isSubmitting}>
          {isSubmitting ? 'Sending…' : 'Send report'}
        </button>
      </div>
    </form>
  );
}
