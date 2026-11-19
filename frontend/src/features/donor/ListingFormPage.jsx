import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { IconAlertTriangle, IconArrowLeft, IconCameraPlus } from '@tabler/icons-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router';
import { createListing, getDonorListing, removePhoto, updateListing, uploadPhoto } from '../../api/donor';
import { keys, queryClient } from '../../api/queryClient';
import { useAuth } from '../../auth/AuthContext';
import { FieldError, FormAlert, SelectField, TextField } from '../../components/Fields';
import { ListingPhoto } from '../../components/ListingPhoto';
import { Skeleton } from '../../components/StatePanels';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import { applyFieldErrors } from '../../lib/apiErrors';
import { CATEGORIES, DIETARY } from '../../lib/labels';
import { stubColour } from '../../lib/pictures';
import { listingSchema } from '../../lib/schemas';
import { utcToZonedInput, zonedInputToUtc, zoneShortName } from '../../lib/time';
import { NotFoundPage, ServerErrorPanel } from '../../pages/ErrorPages';
import { useToast } from '../../toast/ToastContext';

const FIELDS = ['title', 'quantity', 'category', 'dietary', 'pickupFrom', 'pickupUntil', 'bestBefore', 'safetyConfirmed'];
const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
const PHOTO_MESSAGE = "That file can't be used. Use a JPG or PNG photo up to 5 MB.";

/** The next half hour, as a start time a donor is likely to want. */
function nextHalfHour(now) {
  const step = 30 * 60_000;
  return Math.ceil(now / step) * step;
}

/**
 * New listing (/donor/listings/new) and Edit (/donor/listings/:id/edit). The edit form loads the listing first;
 * a claimed listing can't be changed, so it shows why instead.
 */
export function ListingFormPage() {
  const { id } = useParams();
  const editing = Boolean(id);
  usePageTitle(editing ? 'Edit listing' : 'New listing');
  const listingQuery = useQuery({ queryKey: keys.donorListing(id), queryFn: () => getDonorListing(id), enabled: editing });

  if (!editing) {
    return <ListingForm listing={null} />;
  }
  if (listingQuery.isPending) {
    return (
      <div className="card pad mx-auto max-w-[760px]">
        <Skeleton label="Loading the listing…" blocks={1} lines={3} columns={1} />
      </div>
    );
  }
  if (listingQuery.isError) {
    return listingQuery.error.status === 404 ? (
      <NotFoundPage />
    ) : (
      <ServerErrorPanel error={listingQuery.error} onRetry={() => listingQuery.refetch()} />
    );
  }
  if (listingQuery.data.status !== 'LISTED') {
    return (
      <div className="card pad mx-auto max-w-[760px]">
        <Link to={`/donor/listings/${id}`} className="back">
          <IconArrowLeft size={16} aria-hidden="true" /> Back to the listing
        </Link>
        <h1 className="mt-2 text-[22px]">Edit listing</h1>
        <div className="alert a-err mt-3" role="alert">
          <IconAlertTriangle size={20} aria-hidden="true" />
          <div>A listing can be changed only while nobody has claimed it.</div>
        </div>
      </div>
    );
  }
  return <ListingForm listing={listingQuery.data} />;
}

function ListingForm({ listing }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const now = useNow();
  const { showToast, showError } = useToast();
  // New listings use the business's pick-up place and its zone; an open listing keeps the place it was listed with.
  const zone = listing?.timeZone ?? user.donor.timeZone;
  const zoneName = zoneShortName(zone);
  const [photo, setPhoto] = useState(null);
  const [photoError, setPhotoError] = useState(null);
  const [removeCurrent, setRemoveCurrent] = useState(false);
  const [problem, setProblem] = useState(null);

  const start = nextHalfHour(now);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(listingSchema),
    defaultValues: listing
      ? {
          title: listing.title,
          quantity: listing.quantity,
          category: listing.category,
          dietary: listing.dietary,
          pickupFrom: utcToZonedInput(listing.pickupFrom, zone),
          pickupUntil: utcToZonedInput(listing.pickupUntil, zone),
          bestBefore: utcToZonedInput(listing.bestBefore, zone),
          safetyConfirmed: false,
        }
      : {
          title: '',
          quantity: '',
          category: '',
          dietary: [],
          pickupFrom: utcToZonedInput(start, zone),
          pickupUntil: utcToZonedInput(start + 2 * 3_600_000, zone),
          bestBefore: '',
          safetyConfirmed: false,
        },
  });

  function pickPhoto(event) {
    const file = event.target.files?.[0] ?? null;
    setPhotoError(null);
    if (file && (!['image/jpeg', 'image/png'].includes(file.type) || file.size > MAX_PHOTO_BYTES)) {
      setPhoto(null);
      setPhotoError(PHOTO_MESSAGE);
      event.target.value = '';
      return;
    }
    setPhoto(file);
    if (file) {
      setRemoveCurrent(false);
    }
  }

  async function onSubmit(values) {
    setProblem(null);
    const body = {
      title: values.title.trim(),
      quantity: values.quantity.trim(),
      category: values.category,
      dietary: values.dietary,
      pickupFrom: zonedInputToUtc(values.pickupFrom, zone),
      pickupUntil: zonedInputToUtc(values.pickupUntil, zone),
      bestBefore: zonedInputToUtc(values.bestBefore, zone),
      safetyConfirmed: values.safetyConfirmed,
    };
    let saved;
    try {
      saved = listing ? await updateListing(listing.id, body) : await createListing(body);
    } catch (error) {
      if (!applyFieldErrors(error, setError, FIELDS)) {
        setProblem(error.message);
      }
      return;
    }
    // The listing is saved; the photo is a second request, so a refused photo doesn't lose the listing.
    try {
      if (photo) {
        await uploadPhoto(saved.id, photo);
      } else if (removeCurrent && listing?.hasPhoto) {
        await removePhoto(saved.id);
      }
    } catch (error) {
      showError(`The listing was saved, but the photo wasn't: ${error.message}`);
    }
    queryClient.invalidateQueries({ queryKey: ['donor-listings'] });
    queryClient.invalidateQueries({ queryKey: ['listing-photo', String(saved.id)] });
    showToast(listing ? 'Listing saved' : 'Listing published');
    navigate(listing ? `/donor/listings/${saved.id}` : '/donor');
  }

  const showCurrent = listing?.hasPhoto && !removeCurrent && !photo;

  return (
    <div className="card pad mx-auto max-w-[760px] sm:p-7!">
      <Link to={listing ? `/donor/listings/${listing.id}` : '/donor'} className="back">
        <IconArrowLeft size={16} aria-hidden="true" /> {listing ? 'Back to the listing' : 'My listings'}
      </Link>
      <h1 className="mt-2 mb-3.5 text-[22px]">{listing ? 'Edit listing' : 'New listing'}</h1>
      <FormAlert message={problem} />
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        {listing && (showCurrent || listing.hasPhoto) ? (
          <div className="mb-3.5">
            <span className="flabel">Photo</span>
            <div className="row-flex gap-3.5!">
              <div className="thumb" style={{ background: stubColour(listing.category) }}>
                {showCurrent ? (
                  <ListingPhoto listingId={listing.id} hasPhoto category={listing.category} alt={`Current photo of ${listing.title}`} pictureWidth={70} />
                ) : (
                  <span className="xs muted px-1 text-center">{photo ? 'New photo' : 'No photo'}</span>
                )}
              </div>
              <div className="fill">
                <b className="sm">{photo ? photo.name : removeCurrent ? 'The photo will be removed' : 'Current photo'}</b>
                <div className="muted xs">
                  {photo ? `${Math.round(photo.size / 1024)} KB · saved with the listing` : 'Shown to receivers'}
                </div>
                <div className="row-flex mt-1.5 gap-1.5!">
                  <label className="btn btn-soft btn-sm cursor-pointer">
                    Replace
                    <input type="file" accept="image/jpeg,image/png" className="sr-only" onChange={pickPhoto} />
                  </label>
                  {!removeCurrent && (
                    <button
                      type="button"
                      className="btn btn-text btn-sm btn-text-err"
                      onClick={() => {
                        setRemoveCurrent(true);
                        setPhoto(null);
                      }}
                    >
                      Remove photo
                    </button>
                  )}
                </div>
              </div>
            </div>
            <FieldError id="listing-photo-error" message={photoError} />
          </div>
        ) : (
          <>
            <label className="upload" htmlFor="listing-photo">
              <IconCameraPlus size={28} color="var(--forest)" aria-hidden="true" className="mx-auto" />
              <span className="sm block">
                <b>{photo ? photo.name : 'Add a photo'}</b> {photo ? '' : '(optional)'}
              </span>
              <span className="muted xs">JPG or PNG, up to 5 MB. Without a photo, receivers see the category picture.</span>
              <input
                id="listing-photo"
                type="file"
                accept="image/jpeg,image/png"
                aria-describedby={photoError ? 'listing-photo-error' : undefined}
                onChange={pickPhoto}
              />
            </label>
            <FieldError id="listing-photo-error" message={photoError} />
          </>
        )}

        <TextField id="listing-title" label="What food is it?" maxLength={100} error={errors.title?.message} {...register('title')} />
        <div className="g2 gap-x-3! gap-y-0!">
          <div>
            <TextField
              id="listing-quantity"
              label="Quantity"
              placeholder="10 pieces"
              maxLength={60}
              error={errors.quantity?.message}
              {...register('quantity')}
            />
          </div>
          <div>
            <SelectField
              id="listing-category"
              label="Category"
              error={errors.category?.message}
              options={[{ value: '', label: 'Pick a category' }, ...CATEGORIES]}
              {...register('category')}
            />
          </div>
        </div>
        <fieldset className="plain-fieldset">
          <legend className="flabel">Dietary</legend>
          <div className="g2 gap-x-3! gap-y-0!">
            {DIETARY.map((tag) => (
              <label key={tag.value} className="chk">
                <input type="checkbox" value={tag.value} {...register('dietary')} /> {tag.label}
              </label>
            ))}
          </div>
        </fieldset>
        <div className="g2 gap-x-3! gap-y-0!">
          <div>
            <TextField
              id="listing-from"
              label="Pick up from"
              type="datetime-local"
              error={errors.pickupFrom?.message}
              {...register('pickupFrom')}
            />
          </div>
          <div>
            <TextField
              id="listing-until"
              label="Pick up until"
              type="datetime-local"
              error={errors.pickupUntil?.message}
              {...register('pickupUntil')}
            />
          </div>
        </div>
        <p className="hint mt-[-4px]! mb-3.5!">
          Times are in {zoneName} ({zone}), the time zone of your pickup address. Pick-up can end at most 7 days from
          now.
        </p>
        <TextField
          id="listing-best"
          label="Best before"
          type="datetime-local"
          hint="It can't be before the pick-up starts."
          error={errors.bestBefore?.message}
          {...register('bestBefore')}
        />
        <label className="chk mb-1">
          <input
            type="checkbox"
            aria-invalid={errors.safetyConfirmed ? 'true' : undefined}
            aria-describedby={errors.safetyConfirmed ? 'listing-safe-error' : undefined}
            {...register('safetyConfirmed')}
          />{' '}
          I confirm this food is safe to eat and I'm responsible for it.
        </label>
        <div className="mt-2">
          <FieldError id="listing-safe-error" message={errors.safetyConfirmed?.message} />
        </div>
        <div className="row-flex mt-2 justify-end">
          <Link className="btn btn-text" to={listing ? `/donor/listings/${listing.id}` : '/donor'}>
            Cancel
          </Link>
          <button type="submit" className="btn btn-main" disabled={isSubmitting}>
            {isSubmitting ? 'Saving…' : listing ? 'Save changes' : 'Publish listing'}
          </button>
        </div>
      </form>
    </div>
  );
}
