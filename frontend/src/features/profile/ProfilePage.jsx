import { zodResolver } from '@hookform/resolvers/zod';
import { IconLock } from '@tabler/icons-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router';
import { setAccessToken } from '../../api/client';
import { updateDonorProfile } from '../../api/donor';
import { changePassword, deleteAccount, updateMe } from '../../api/me';
import { invalidateClaimViews } from '../../api/queryClient';
import { useAuth } from '../../auth/AuthContext';
import { Dialog } from '../../components/Dialog';
import { FormAlert, TextField } from '../../components/Fields';
import { PlaceSearch } from '../../components/PlaceSearch';
import { usePageTitle } from '../../hooks/usePageTitle';
import { applyFieldErrors } from '../../lib/apiErrors';
import { businessSchema, changePasswordSchema, deleteSchema, profileSchema } from '../../lib/schemas';
import { useToast } from '../../toast/ToastContext';

const DEMO_NOTE = "Demo accounts can't change their email or password, or be deleted.";

/** Name, email (read-only) and, for a receiver, the home location Find food starts from. */
function PersonalForm({ user, setUser }) {
  const { showToast } = useToast();
  const isReceiver = user.role === 'RECEIVER';
  const [home, setHome] = useState(user.home ? { label: user.home.label, lat: user.home.lat, lng: user.home.lng } : null);
  const [homeText, setHomeText] = useState(user.home?.label ?? '');
  const [homeError, setHomeError] = useState(null);
  const [problem, setProblem] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(profileSchema), defaultValues: { name: user.name } });

  async function onSubmit(values) {
    setProblem(null);
    if (isReceiver && !home && homeText.trim()) {
      setHomeError('Find your area again, or leave it empty.');
      return;
    }
    const body = { name: values.name.trim() };
    if (isReceiver) {
      Object.assign(body, { homeLabel: home?.label ?? null, homeLat: home?.lat ?? null, homeLng: home?.lng ?? null });
    }
    try {
      setUser(await updateMe(body));
      showToast('Profile saved');
    } catch (error) {
      if (!applyFieldErrors(error, setError, ['name'])) {
        setProblem(error.message);
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <FormAlert message={problem} />
      <TextField id="profile-name" label="Your name" autoComplete="name" error={errors.name?.message} {...register('name')} />
      <TextField id="profile-email" label="Email" value={user.email} readOnly />
      {isReceiver && (
        <PlaceSearch
          id="profile-home"
          label="Home location (for Find food, optional)"
          buttonLabel="Change"
          hint="Find food starts here. Leave it empty to choose a place each time."
          error={homeError}
          selected={home}
          initialText={user.home?.label ?? ''}
          onSelect={(found) => {
            setHome(found);
            setHomeError(null);
          }}
          onTextChange={setHomeText}
        />
      )}
      <button type="submit" className="btn btn-main" disabled={isSubmitting}>
        {isSubmitting ? 'Saving…' : 'Save'}
      </button>
    </form>
  );
}

/** The donor's business profile: a new pick-up address is used for new listings only. */
function BusinessForm({ user, setUser }) {
  const { showToast } = useToast();
  const donor = user.donor;
  const [place, setPlace] = useState({ label: donor.pickupAddress, area: donor.pickupArea, lat: donor.pickupLat, lng: donor.pickupLng });
  const [placeError, setPlaceError] = useState(null);
  const [problem, setProblem] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(businessSchema),
    defaultValues: { businessName: donor.businessName, contactName: user.name, fssaiNo: donor.fssaiNo ?? '' },
  });

  async function onSubmit(values) {
    setProblem(null);
    if (!place) {
      setPlaceError('Find the pick-up address.');
      return;
    }
    try {
      const updated = await updateDonorProfile({
        businessName: values.businessName.trim(),
        contactName: values.contactName.trim(),
        fssaiNo: values.fssaiNo.trim() || null,
        pickupAddress: place.label,
        pickupArea: place.area,
        pickupLat: place.lat,
        pickupLng: place.lng,
        timeZone: donor.timeZone,
      });
      setUser(updated);
      showToast('Business profile saved');
    } catch (error) {
      const placed = applyFieldErrors(error, setError, ['businessName', 'contactName', 'fssaiNo']);
      const addressDetail = ['pickupAddress', 'pickupArea', 'pickupLat', 'pickupLng'].map((field) => error.detail(field)).find(Boolean);
      if (addressDetail) {
        setPlaceError(addressDetail);
      } else if (!placed) {
        setProblem(error.message);
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit, () => setPlaceError(place ? null : 'Find the pick-up address.'))} noValidate>
      <FormAlert message={problem} />
      <TextField id="business-name" label="Business name" error={errors.businessName?.message} {...register('businessName')} />
      <TextField id="business-contact" label="Contact name" autoComplete="name" error={errors.contactName?.message} {...register('contactName')} />
      <TextField id="business-email" label="Email" value={user.email} readOnly />
      <PlaceSearch
        id="business-address"
        label="Pickup address"
        hint="A new address is used for new listings; open listings keep the address they were listed with."
        error={placeError}
        selected={place}
        onSelect={(found) => {
          setPlace(found);
          setPlaceError(null);
        }}
      />
      <TextField
        id="business-fssai"
        label="FSSAI licence no. (optional)"
        inputMode="numeric"
        placeholder="14 digits"
        hint="Shown only to admins."
        error={errors.fssaiNo?.message}
        {...register('fssaiNo')}
      />
      <button type="submit" className="btn btn-main" disabled={isSubmitting}>
        {isSubmitting ? 'Saving…' : 'Save'}
      </button>
    </form>
  );
}

/** Change password: the other sessions are logged out and this one gets a new access token. */
function PasswordForm() {
  const { showToast } = useToast();
  const [open, setOpen] = useState(false);
  const [problem, setProblem] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(changePasswordSchema), defaultValues: { currentPassword: '', newPassword: '' } });

  async function onSubmit(values) {
    setProblem(null);
    try {
      const { accessToken } = await changePassword(values.currentPassword, values.newPassword);
      setAccessToken(accessToken);
      reset();
      setOpen(false);
      showToast('Password changed · your other sessions were logged out');
    } catch (error) {
      if (!applyFieldErrors(error, setError, ['currentPassword', 'newPassword'])) {
        setProblem(error.message);
      }
    }
  }

  if (!open) {
    return (
      <button type="button" className="btn btn-text btn-sm px-0!" onClick={() => setOpen(true)} aria-expanded="false">
        Change password
      </button>
    );
  }
  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate aria-labelledby="password-title">
      <h2 id="password-title" className="mb-3 text-[17px]">
        Change password
      </h2>
      <FormAlert message={problem} />
      <TextField
        id="password-current"
        label="Current password"
        type="password"
        autoComplete="current-password"
        error={errors.currentPassword?.message}
        {...register('currentPassword')}
      />
      <TextField
        id="password-new"
        label="New password"
        type="password"
        autoComplete="new-password"
        hint="At least 8 characters, with a number. Your other sessions are logged out."
        error={errors.newPassword?.message}
        {...register('newPassword')}
      />
      <div className="row-flex">
        <button type="submit" className="btn btn-main btn-sm" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : 'Change password'}
        </button>
        <button type="button" className="btn btn-text btn-sm" onClick={() => setOpen(false)}>
          Cancel
        </button>
      </div>
    </form>
  );
}

/** Delete my account: asks for the password; the account is anonymised and the person is logged out. */
function DeleteAccount({ user }) {
  const { signOut } = useAuth();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [open, setOpen] = useState(false);
  const isDonor = user.role === 'DONOR';
  const name = isDonor ? user.donor.businessName : null;

  return (
    <>
      <b>Delete my account</b>
      <p className="muted sm mt-1 mb-2.5">
        {isDonor ? 'Removes your business details, address and photos for good.' : 'Removes your name, email and location for good.'}
      </p>
      <button type="button" className="btn btn-line-err btn-sm" onClick={() => setOpen(true)}>
        Delete my account
      </button>
      <Dialog open={open} onClose={() => setOpen(false)} labelledBy="delete-title" describedBy="delete-text">
        <DeleteForm
          title={isDonor ? `Delete ${name}'s account?` : 'Delete your account?'}
          text={
            isDonor
              ? 'Your open listings are withdrawn and your active claims are cancelled; the receivers are emailed. Past handovers stay in receivers\' claims as "Former donor". This can\'t be undone.'
              : 'Your active claims are cancelled and the food goes back on the list. Past claims stay in donors\' history as "Former user". This can\'t be undone.'
          }
          onCancel={() => setOpen(false)}
          onDeleted={async () => {
            setOpen(false);
            invalidateClaimViews();
            await signOut();
            showToast('Your account was deleted');
            navigate('/', { replace: true });
          }}
        />
      </Dialog>
    </>
  );
}

function DeleteForm({ title, text, onCancel, onDeleted }) {
  const [problem, setProblem] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(deleteSchema), defaultValues: { password: '' } });

  async function onSubmit(values) {
    setProblem(null);
    try {
      await deleteAccount(values.password);
      await onDeleted();
    } catch (error) {
      if (!applyFieldErrors(error, setError, ['password'])) {
        setProblem(error.message);
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <h2 id="delete-title">{title}</h2>
      <p id="delete-text" className="muted sm mt-1.5 mb-3">
        {text}
      </p>
      <FormAlert message={problem} />
      <TextField
        id="delete-password"
        label="Enter your password to confirm"
        type="password"
        autoComplete="current-password"
        className="mb-0!"
        error={errors.password?.message}
        {...register('password')}
      />
      <div className="acts">
        <button type="button" className="btn btn-line" onClick={onCancel} data-autofocus>
          Keep my account
        </button>
        <button type="submit" className="btn btn-danger" disabled={isSubmitting}>
          {isSubmitting ? 'Deleting…' : 'Delete account'}
        </button>
      </div>
    </form>
  );
}

/** Profile (/profile): every role's own details; donors see their Business profile. */
export function ProfilePage() {
  const { user, setUser } = useAuth();
  const isDonor = user.role === 'DONOR';
  usePageTitle(isDonor ? 'Business profile' : 'Profile');

  return (
    <div className="g2 mx-auto max-w-[1100px] items-start">
      <section className="card pad sm:p-7!" aria-labelledby="profile-title">
        <h1 id="profile-title" className="mb-3.5 text-[22px]">
          {isDonor ? 'Business profile' : 'Profile'}
        </h1>
        {isDonor ? <BusinessForm user={user} setUser={setUser} /> : <PersonalForm user={user} setUser={setUser} />}
      </section>
      <section className="card pad sm:p-7!" aria-label="Account security">
        {user.demo ? (
          <p className="muted sm flex items-start gap-2">
            <IconLock size={18} aria-hidden="true" className="mt-0.5 shrink-0" /> {DEMO_NOTE}
          </p>
        ) : (
          <>
            <PasswordForm />
            <hr className="divider" />
            {user.role === 'ADMIN' ? (
              <p className="muted sm">Admin accounts are created by the seed script and can't be deleted here.</p>
            ) : (
              <DeleteAccount user={user} />
            )}
          </>
        )}
      </section>
    </div>
  );
}
