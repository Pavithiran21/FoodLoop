package com.foodloop.common.web;

/** Great-circle distance between two latitude/longitude points (the haversine formula). */
public final class Haversine {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Haversine() {
    }

    /** Distance in kilometres, rounded to 0.1 km (the precision a ticket shows). */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double km = 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
        return Math.round(km * 10.0) / 10.0;
    }
}
