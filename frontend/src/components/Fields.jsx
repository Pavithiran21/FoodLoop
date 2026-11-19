import { IconAlertCircle } from '@tabler/icons-react';
import { describedBy } from '../lib/fieldIds';

/** A field's error line, tied to its input with aria-describedby. */
export function FieldError({ id, message }) {
  if (!message) {
    return null;
  }
  return (
    <div className="ferr" id={id}>
      <IconAlertCircle size={16} aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}

/**
 * A labelled text input with its hint and error. `ref` and the rest come from React Hook Form's register(), so
 * <TextField id="email" label="Email" error={errors.email?.message} {...register('email')} /> is the whole field.
 */
export function TextField({ id, label, error, hint, className = '', as = 'input', ...inputProps }) {
  const Control = as;
  return (
    <>
      <label className="flabel" htmlFor={id}>
        {label}
      </label>
      <Control
        id={id}
        className={`inp ${className}`}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={describedBy(id, { error, hint })}
        {...inputProps}
      />
      <FieldError id={`${id}-error`} message={error} />
      {hint && (
        <div className="hint" id={`${id}-hint`}>
          {hint}
        </div>
      )}
    </>
  );
}

/** A labelled native select. */
export function SelectField({ id, label, error, hint, options, ...selectProps }) {
  return (
    <>
      <label className="flabel" htmlFor={id}>
        {label}
      </label>
      <select
        id={id}
        className="inp"
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={describedBy(id, { error, hint })}
        {...selectProps}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <FieldError id={`${id}-error`} message={error} />
      {hint && (
        <div className="hint" id={`${id}-hint`}>
          {hint}
        </div>
      )}
    </>
  );
}

/** A form-wide error (not about one field), for example a 429 or "That email or password doesn't match". */
export function FormAlert({ message, icon: Icon = IconAlertCircle, children }) {
  if (!message && !children) {
    return null;
  }
  return (
    <div className="alert a-err mb-3.5" role="alert">
      <Icon size={20} aria-hidden="true" />
      <div>{message ?? children}</div>
    </div>
  );
}
