import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { Link } from 'react-router';
import { register as registerAccount } from '../../api/auth';
import { useAuth } from '../../auth/AuthContext';
import { FormAlert, TextField } from '../../components/Fields';
import { FoodPicture } from '../../components/FoodPicture';
import { PlaceSearch } from '../../components/PlaceSearch';
import { usePageTitle } from '../../hooks/usePageTitle';
import { applyFieldErrors } from '../../lib/apiErrors';
import { signUpSchema } from '../../lib/schemas';
import { browserZone } from '../../lib/time';
import { AuthCard } from './AuthCard';

const FORM_FIELDS = ['role', 'name', 'email', 'password', 'businessName', 'fssaiNo'];

/**
 * Sign up as a Donor or a Receiver (Admin is never offered). A donor's pick-up address must be found on
 * OpenStreetMap, so it has a position for distances; a receiver's home area is optional. A new donor lands on My
 * listings with "Waiting for verification"; a receiver can claim straight away.
 */
export function SignUpPage() {
  usePageTitle('Create your account');
  const { signIn } = useAuth();
  const [place, setPlace] = useState(null);
  const [placeText, setPlaceText] = useState('');
  const [placeError, setPlaceError] = useState(null);
  const [problem, setProblem] = useState(null);
  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(signUpSchema),
    defaultValues: { role: 'DONOR', name: '', email: '', password: '', businessName: '', fssaiNo: '' },
  });
  const role = useWatch({ control, name: 'role' });
  const isDonor = role === 'DONOR';
  // A donor's pick-up address and a receiver's home are different things: changing the role starts again.
  const roleField = register('role', {
    onChange: () => {
      setPlace(null);
      setPlaceText('');
      setPlaceError(null);
    },
  });

  function checkPlace() {
    if (isDonor && !place) {
      return 'Find the pick-up address.';
    }
    if (!isDonor && !place && placeText.trim()) {
      return 'Find your area again, or leave it empty.';
    }
    return null;
  }

  async function onSubmit(values) {
    setProblem(null);
    const placeProblem = checkPlace();
    setPlaceError(placeProblem);
    if (placeProblem) {
      return;
    }
    const body = { role: values.role, name: values.name.trim(), email: values.email.trim(), password: values.password };
    if (isDonor) {
      Object.assign(body, {
        businessName: values.businessName.trim(),
        fssaiNo: values.fssaiNo.trim() || null,
        pickupAddress: place.label,
        pickupArea: place.area,
        pickupLat: place.lat,
        pickupLng: place.lng,
        timeZone: browserZone(),
      });
    } else if (place) {
      Object.assign(body, { homeLabel: place.label, homeLat: place.lat, homeLng: place.lng });
    }
    try {
      signIn(await registerAccount(body));
    } catch (error) {
      const placed = applyFieldErrors(error, setError, FORM_FIELDS);
      const addressDetail = error.detail('pickupAddress') ?? error.detail('homeLabel');
      if (addressDetail) {
        setPlaceError(addressDetail);
      }
      if (!placed && !addressDetail) {
        setProblem(error.message);
      }
    }
  }

  // Validate the address together with the rest, so every problem shows at once.
  const submit = handleSubmit(onSubmit, () => setPlaceError(checkPlace()));

  return (
    <AuthCard wide>
      <h1 className="text-[26px]">Create your account</h1>
      <form onSubmit={submit} noValidate className="mt-1">
        <fieldset className="roles">
          <legend className="sr-only">How will you use FoodLoop?</legend>
          <label className="rolecard">
            <input type="radio" value="DONOR" {...roleField} />
            <FoodPicture category="BAKERY" width={54} />
            <h3>I have food to give</h3>
            <span className="muted sm">
              Donor · restaurant, bakery or shop. An admin verifies you before your first listing.
            </span>
          </label>
          <label className="rolecard">
            <input type="radio" value="RECEIVER" {...roleField} />
            <FoodPicture category="COOKED_MEAL" width={54} />
            <h3>I'm looking for food</h3>
            <span className="muted sm">Receiver · person or charity. Claim and collect food near you.</span>
          </label>
        </fieldset>
        <FormAlert message={problem} />
        <div className="g2 gap-x-3.5! gap-y-0!">
          {isDonor && (
            <div>
              <TextField id="su-business" label="Business name" error={errors.businessName?.message} {...register('businessName')} />
            </div>
          )}
          <div>
            <TextField
              id="su-name"
              label={isDonor ? 'Your name' : 'Your name or charity'}
              autoComplete="name"
              error={errors.name?.message}
              {...register('name')}
            />
          </div>
          <div>
            <TextField id="su-email" label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
          </div>
          <div>
            <TextField
              id="su-password"
              label="Password"
              type="password"
              autoComplete="new-password"
              hint="At least 8 characters, with a number."
              error={errors.password?.message}
              {...register('password')}
            />
          </div>
        </div>
        {isDonor && (
          <TextField
            id="su-fssai"
            label="FSSAI licence no. (optional)"
            inputMode="numeric"
            placeholder="14 digits"
            hint="Helps the admin verify you faster. Shown only to admins."
            error={errors.fssaiNo?.message}
            {...register('fssaiNo')}
          />
        )}
        <PlaceSearch
          key={role}
          id="su-place"
          label={isDonor ? 'Pickup address' : "Home area (where you'll look for food, optional)"}
          buttonLabel={isDonor ? 'Find address' : 'Find area'}
          hint={
            isDonor
              ? 'We look it up on OpenStreetMap to work out distances. Only the area is shown to receivers until they claim.'
              : 'Used only to sort food by distance. You can search another address any time.'
          }
          error={placeError}
          selected={place}
          onSelect={(found) => {
            setPlace(found);
            setPlaceError(null);
          }}
          onTextChange={setPlaceText}
        />
        <button type="submit" className="btn btn-main btn-block mt-1" disabled={isSubmitting}>
          {isSubmitting ? 'Creating your account…' : isDonor ? 'Create donor account' : 'Create receiver account'}
        </button>
        <p className="muted xs mt-3 text-center">
          {isDonor ? 'Donors can log in before they are verified.' : 'Receivers can claim straight away; there is no verification step.'}{' '}
          Already on FoodLoop? <Link to="/">Log in</Link> · Admin accounts can't be created here.
        </p>
      </form>
    </AuthCard>
  );
}
