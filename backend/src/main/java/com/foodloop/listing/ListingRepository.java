package com.foodloop.listing;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    /** SELECT ... FOR UPDATE: editing, withdrawing and every claim move lock the listing row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id = :id")
    Optional<Listing> lockById(Long id);

    /**
     * THE CLAIM RACE: one atomic UPDATE that turns LISTED into CLAIMED only while the listing is still LISTED and its
     * pick-up window is still open. MySQL takes the row lock, so of many receivers claiming at the same moment exactly
     * one gets 1 back; the others re-check the row after the winner commits, find it CLAIMED and get 0.
     */
    @Modifying
    @Transactional
    @Query("""
            update Listing l set l.status = com.foodloop.listing.ListingStatus.CLAIMED, l.updatedAt = :touched
            where l.id = :id and l.status = com.foodloop.listing.ListingStatus.LISTED and l.pickupUntil > :now
            """)
    int claimIfAvailable(Long id, Instant now, Instant touched);

    /** Expiry job, unclaimed listing: closes it only if it is still LISTED and its window has ended. */
    @Modifying
    @Transactional
    @Query("""
            update Listing l set l.status = com.foodloop.listing.ListingStatus.EXPIRED, l.closedAt = :now, l.updatedAt = :touched
            where l.id = :id and l.status = com.foodloop.listing.ListingStatus.LISTED and l.pickupUntil <= :now
            """)
    int expireIfListed(Long id, Instant now, Instant touched);

    /** Expiry job, listing whose claim just expired: it is CLAIMED or READY until then. */
    @Modifying
    @Transactional
    @Query("""
            update Listing l set l.status = com.foodloop.listing.ListingStatus.EXPIRED, l.closedAt = :now, l.updatedAt = :touched
            where l.id = :id and l.status in (com.foodloop.listing.ListingStatus.CLAIMED, com.foodloop.listing.ListingStatus.READY)
            """)
    int expireIfClaimed(Long id, Instant now, Instant touched);

    /** Auto-complete job: the claim completed, so the listing does too. */
    @Modifying
    @Transactional
    @Query("""
            update Listing l set l.status = com.foodloop.listing.ListingStatus.COMPLETED, l.closedAt = :now, l.updatedAt = :touched
            where l.id = :id and l.status = com.foodloop.listing.ListingStatus.PICKED_UP
            """)
    int completeIfPickedUp(Long id, Instant now, Instant touched);

    @Query("""
            select l from Listing l
            where l.status = com.foodloop.listing.ListingStatus.LISTED and l.pickupUntil <= :now and l.id > :afterId
            order by l.id
            """)
    List<Listing> findDueForExpiry(Instant now, long afterId, Limit limit);

    /** Account deletion: a deleted donor's listings keep their history but lose the address and the photo flag. */
    @Modifying
    @Transactional
    @Query("update Listing l set l.pickupAddress = 'Removed', l.hasPhoto = false where l.donorId = :donorId")
    int anonymiseDonorListings(Long donorId);

    Page<Listing> findByDonorIdAndStatusIn(Long donorId, Collection<ListingStatus> statuses, Pageable pageable);

    Page<Listing> findByDonorIdAndStatusInAndClosedAtGreaterThanEqual(Long donorId, Collection<ListingStatus> statuses,
            Instant since, Pageable pageable);

    List<Listing> findByDonorIdAndStatus(Long donorId, ListingStatus status);

    long countByDonorIdAndStatus(Long donorId, ListingStatus status);
}
