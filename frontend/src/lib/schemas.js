import { z } from 'zod';

// Form rules checked in the browser before a request is sent, with UI-UX.md's messages. The backend checks
// every rule again (and some only it can check, like the 7-day limit against its own clock); its answer is shown
// next to the field when it differs.

const EMAIL = 'Enter a valid email, like name@example.com.';
const PASSWORD = 'Use at least 8 characters, with a number.';

export const email = z.string().trim().min(1, EMAIL).max(254, EMAIL).regex(/^[^@\s]+@[^@\s]+\.[^@\s]+$/, EMAIL);
export const newPassword = z.string().min(8, PASSWORD).max(200, PASSWORD).regex(/\d/, PASSWORD);

export const loginSchema = z.object({
  email,
  password: z.string().min(1, 'Enter your password.'),
});

export const forgotSchema = z.object({ email });

export const resetSchema = z
  .object({ password: newPassword, confirm: z.string() })
  .refine((values) => values.password === values.confirm, {
    path: ['confirm'],
    message: "The two passwords don't match.",
  });

const fssai = z
  .string()
  .trim()
  .regex(/^(\d{14})?$/, 'Enter the 14-digit FSSAI number, or leave it empty.');

export const signUpSchema = z
  .object({
    role: z.enum(['DONOR', 'RECEIVER'], { error: 'Pick Donor or Receiver.' }),
    name: z.string().trim().min(1, 'Enter your name.').max(80, 'Use at most 80 characters.'),
    email,
    password: newPassword,
    businessName: z.string().trim().max(100, 'Use at most 100 characters.'),
    fssaiNo: fssai,
  })
  .superRefine((values, context) => {
    if (values.role === 'DONOR' && !values.businessName) {
      context.addIssue({ code: 'custom', path: ['businessName'], message: 'Enter the business name.' });
    }
  });

export const profileSchema = z.object({
  name: z.string().trim().min(1, 'Enter your name.').max(80, 'Use at most 80 characters.'),
});

export const businessSchema = z.object({
  businessName: z.string().trim().min(1, 'Enter the business name.').max(100, 'Use at most 100 characters.'),
  contactName: z.string().trim().min(1, 'Enter your name.').max(80, 'Use at most 80 characters.'),
  fssaiNo: fssai,
});

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, 'Enter your current password.'),
  newPassword,
});

export const deleteSchema = z.object({ password: z.string().min(1, 'Enter your password.') });

export const reasonSchema = z.object({
  reason: z.string().trim().min(1, "Add a short reason; they'll see it.").max(200, 'Use at most 200 characters.'),
});

export const reportSchema = z.object({
  reason: z.string({ error: 'Pick a reason.' }).min(1, 'Pick a reason.'),
  details: z.string().max(500, 'Use at most 500 characters.'),
});

/**
 * The listing form. Times are datetime-local strings in the pick-up place's zone; `zone` turns them into
 * instants (the 7-day and "not in the past" rules are left to the server, which owns the clock).
 */
export const listingSchema = z
  .object({
    title: z.string().trim().min(1, 'Say what the food is.').max(100, 'Use at most 100 characters.'),
    quantity: z.string().trim().min(1, 'Enter how much there is, like "10 pieces".').max(60, 'Use at most 60 characters.'),
    category: z.string().min(1, 'Pick a category.'),
    dietary: z.array(z.string()),
    pickupFrom: z.string().min(1, 'Choose when pick-up starts.'),
    pickupUntil: z.string().min(1, 'Choose when pick-up ends.'),
    bestBefore: z.string().min(1, 'Choose the best-before time.'),
    safetyConfirmed: z.literal(true, { error: 'Confirm the food is safe to eat before publishing.' }),
  })
  .superRefine((values, context) => {
    // datetime-local strings in one zone compare correctly as text ("2026-11-13T18:00").
    if (values.pickupFrom && values.pickupUntil && values.pickupUntil <= values.pickupFrom) {
      context.addIssue({ code: 'custom', path: ['pickupUntil'], message: 'Pick-up must end after it starts.' });
    }
    if (values.pickupFrom && values.bestBefore && values.bestBefore < values.pickupFrom) {
      context.addIssue({
        code: 'custom',
        path: ['bestBefore'],
        message: "The best-before time can't be before the pick-up starts.",
      });
    }
  });
