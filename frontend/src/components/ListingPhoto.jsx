import { useQuery } from '@tanstack/react-query';
import { useCallback } from 'react';
import { getListingPhoto } from '../api/listings';
import { keys } from '../api/queryClient';
import { FoodPicture } from './FoodPicture';

/**
 * A listing's photo, or its category picture when it has none.
 *
 * GET /listings/{id}/photo needs the Bearer token, so an <img src="/api/..."> can't load it. The bytes are fetched
 * through the API client (cached by TanStack Query) and shown through an object URL. The URL is made in the img's
 * ref callback and revoked in that callback's cleanup (React 19), so it lives exactly as long as the <img>.
 * `version` changes after the donor replaces the photo, so the new one is fetched.
 */
export function ListingPhoto({ listingId, hasPhoto, category, alt, pictureWidth = 90, version = 0 }) {
  const { data: blob, isError } = useQuery({
    queryKey: keys.listingPhoto(listingId, version),
    queryFn: () => getListingPhoto(listingId),
    enabled: Boolean(hasPhoto),
    staleTime: 5 * 60_000,
    retry: false,
  });

  const showBlob = useCallback(
    (img) => {
      if (!img || !blob) {
        return undefined;
      }
      const url = URL.createObjectURL(blob);
      img.src = url;
      return () => URL.revokeObjectURL(url);
    },
    [blob],
  );

  if (hasPhoto && blob && !isError) {
    return <img ref={showBlob} alt={alt} />;
  }
  return <FoodPicture category={category} width={pictureWidth} />;
}
