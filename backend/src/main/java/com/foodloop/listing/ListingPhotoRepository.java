package com.foodloop.listing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ListingPhotoRepository extends JpaRepository<ListingPhoto, Long> {

    /** Removes the photos of every listing a donor made (account deletion). */
    @Modifying
    @Transactional
    @Query("delete from ListingPhoto p where p.listingId in (select l.id from Listing l where l.donorId = :donorId)")
    int deleteAllForDonor(Long donorId);
}
