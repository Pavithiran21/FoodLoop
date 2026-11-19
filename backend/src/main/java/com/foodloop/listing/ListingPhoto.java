package com.foodloop.listing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The bytes of a listing's photo (LONGBLOB). Kept out of {@link Listing} so no list query ever reads them. */
@Entity
@Table(name = "listing_photos")
public class ListingPhoto {

    @Id
    @Column(name = "listing_id")
    private Long listingId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(nullable = false)
    private byte[] data;

    protected ListingPhoto() {
    }

    public ListingPhoto(Long listingId, String contentType, byte[] data) {
        this.listingId = listingId;
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data.length;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }
}
