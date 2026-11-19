package com.foodloop.listing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.foodloop.listing.ListingDtos.DonorBrief;
import com.foodloop.listing.ListingDtos.ListingCard;

/**
 * "Find food": listings near a point, with the haversine distance worked out inside MySQL so filtering, sorting and
 * paging all happen in the database. Plain SQL on purpose: JPQL has no trigonometry, and one explicit query is easier
 * to explain than a chain of Specifications.
 *
 * Distance = 6371 km * acos(cos(lat1) cos(lat2) cos(lng2 - lng1) + sin(lat1) sin(lat2)) (the haversine / spherical law
 * of cosines, accurate to metres at these distances); LEAST(1, ...) stops rounding errors from making acos fail on
 * two identical points. A bounding box on latitude and longitude runs first, so MySQL can use idx_listings_geo and does
 * the trigonometry only for nearby rows.
 */
@Repository
public class ListingSearchRepository {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double KM_PER_DEGREE_LAT = 111.2;

    public enum SortBy {
        CLOSEST("distance_km ASC, l.id ASC"),
        ENDS_SOONEST("l.pickup_until ASC, l.id ASC"),
        NEWEST("l.created_at DESC, l.id DESC");

        private final String orderBy;

        SortBy(String orderBy) {
            this.orderBy = orderBy;
        }
    }

    /** What the receiver asked for. {@code windowFrom}/{@code windowTo} are null for "any time". */
    public record Search(double lat, double lng, double radiusKm, Set<Category> categories, Set<Dietary> dietary,
            Instant windowFrom, Instant windowTo, Instant now, SortBy sort) {
    }

    public record Result(List<ListingCard> items, long total) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public ListingSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Result search(Search search, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lat", search.lat())
                .addValue("lng", search.lng())
                .addValue("radius", search.radiusKm())
                .addValue("now", utc(search.now()));
        String inner = innerSql(search, params);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + inner + ") found", params, Long.class);
        if (total == null || total == 0) {
            return new Result(List.of(), 0);
        }
        params.addValue("limit", size).addValue("offset", (long) (page - 1) * size);
        List<ListingCard> cards = jdbc.query(inner + " ORDER BY " + search.sort().orderBy + " LIMIT :limit OFFSET :offset",
                params, (rs, row) -> toCard(rs));
        return new Result(withDietary(cards), total);
    }

    private static String innerSql(Search search, MapSqlParameterSource params) {
        double dLat = search.radiusKm() / KM_PER_DEGREE_LAT;
        // A degree of longitude gets shorter towards the poles: widen the box by 1 / cos(latitude).
        double dLng = search.radiusKm() / (KM_PER_DEGREE_LAT * Math.max(0.01, Math.cos(Math.toRadians(search.lat()))));
        params.addValue("minLat", search.lat() - dLat).addValue("maxLat", search.lat() + dLat)
                .addValue("minLng", search.lng() - dLng).addValue("maxLng", search.lng() + dLng);

        StringBuilder sql = new StringBuilder("""
                SELECT l.id, l.title, l.quantity, l.category, l.pickup_from, l.pickup_until, l.best_before, l.time_zone,
                       l.has_photo, l.created_at, l.pickup_area, l.pickup_lat, l.pickup_lng, d.business_name,
                       (%s * ACOS(LEAST(1.0,
                            COS(RADIANS(:lat)) * COS(RADIANS(l.pickup_lat)) * COS(RADIANS(l.pickup_lng) - RADIANS(:lng))
                          + SIN(RADIANS(:lat)) * SIN(RADIANS(l.pickup_lat))))) AS distance_km
                FROM listings l
                JOIN donor_profiles d ON d.user_id = l.donor_id
                JOIN users u ON u.id = l.donor_id
                WHERE l.status = 'LISTED' AND l.pickup_until > :now AND u.status = 'ACTIVE'
                  AND l.pickup_lat BETWEEN :minLat AND :maxLat AND l.pickup_lng BETWEEN :minLng AND :maxLng
                """.formatted(EARTH_RADIUS_KM));
        if (!search.categories().isEmpty()) {
            sql.append(" AND l.category IN (:categories)");
            params.addValue("categories", search.categories().stream().map(Enum::name).toList());
        }
        // Every chosen dietary tag must be on the listing (vegetarian AND halal narrows, it doesn't widen).
        int n = 0;
        for (Dietary tag : search.dietary()) {
            String name = "tag" + n++;
            sql.append(" AND EXISTS (SELECT 1 FROM listing_dietary x WHERE x.listing_id = l.id AND x.tag = :").append(name).append(")");
            params.addValue(name, tag.name());
        }
        if (search.windowFrom() != null) {
            // The pick-up window overlaps the asked-for period (next 2 hours, today, tomorrow).
            sql.append(" AND l.pickup_from < :windowTo AND l.pickup_until > :windowFrom");
            params.addValue("windowFrom", utc(search.windowFrom())).addValue("windowTo", utc(search.windowTo()));
        }
        sql.append(" HAVING distance_km <= :radius");
        return sql.toString();
    }

    private static ListingCard toCard(ResultSet rs) throws SQLException {
        double distance = Math.round(rs.getDouble("distance_km") * 10.0) / 10.0;
        return new ListingCard(rs.getLong("id"), rs.getString("title"), rs.getString("quantity"),
                Category.valueOf(rs.getString("category")), EnumSet.noneOf(Dietary.class),
                new DonorBrief(rs.getString("business_name"), true), rs.getString("pickup_area"), distance,
                instant(rs, "pickup_from"), instant(rs, "pickup_until"), instant(rs, "best_before"), rs.getString("time_zone"),
                rs.getBoolean("has_photo"), instant(rs, "created_at"),
                ListingCard.approx(rs.getDouble("pickup_lat")), ListingCard.approx(rs.getDouble("pickup_lng")));
    }

    /** One more query for the whole page: the dietary tags of the cards found. */
    private List<ListingCard> withDietary(List<ListingCard> cards) {
        List<Long> ids = cards.stream().map(ListingCard::id).toList();
        Map<Long, Set<Dietary>> tags = new HashMap<>();
        jdbc.query("SELECT listing_id, tag FROM listing_dietary WHERE listing_id IN (:ids)",
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    tags.computeIfAbsent(rs.getLong("listing_id"), key -> EnumSet.noneOf(Dietary.class))
                            .add(Dietary.valueOf(rs.getString("tag")));
                });
        List<ListingCard> result = new ArrayList<>(cards.size());
        for (ListingCard c : cards) {
            result.add(new ListingCard(c.id(), c.title(), c.quantity(), c.category(), tags.getOrDefault(c.id(), Set.of()), c.donor(),
                    c.area(), c.distanceKm(), c.pickupFrom(), c.pickupUntil(), c.bestBefore(), c.timeZone(), c.hasPhoto(), c.createdAt(),
                    c.approxLat(), c.approxLng()));
        }
        return result;
    }

    /** DATETIME columns hold UTC (the connection is forced to UTC), so instants are passed and read as UTC. */
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }
}
