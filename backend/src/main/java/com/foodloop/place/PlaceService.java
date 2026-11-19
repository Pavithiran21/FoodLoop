package com.foodloop.place;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.foodloop.common.error.ApiException;
import com.foodloop.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Address search through OpenStreetMap Nominatim. Its usage policy asks for: a real User-Agent, at most 1 request a
 * second, and caching. This service does all three: the User-Agent comes from NOMINATIM_USER_AGENT, a single lock spaces
 * upstream calls at least 1.1 s apart, and answers are cached for a day (a repeated search never reaches Nominatim).
 * The address text a person types is never logged. In tests and demos NOMINATIM_BASE_URL points at WireMock.
 */
@Service
public class PlaceService {

    private static final Logger log = LoggerFactory.getLogger(PlaceService.class);
    private static final long MIN_GAP_MILLIS = 1100;

    /** One search result. {@code area} is the short place name receivers may see before they claim. */
    public record Place(String label, String area, double lat, double lng) {
    }

    /** The parts of Nominatim's jsonv2 answer that we use (the rest is ignored). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record NominatimHit(String display_name, String lat, String lon, Address address) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Address(String suburb, String neighbourhood, String city_district, String quarter, String city, String town,
            String village, String county, String state_district, String state) {

        /** The most local name that exists, from "Indiranagar" up to the state. */
        String area() {
            for (String candidate : new String[] { suburb, neighbourhood, quarter, city_district, village, town, city,
                    county, state_district, state }) {
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
            return null;
        }
    }

    private final RestClient client;
    private final String countryCodes;
    private final Cache<String, List<Place>> cache = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(5_000)
            .build();
    private final Object upstreamLock = new Object();
    private long lastCallMillis;

    public PlaceService(AppProperties props) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(props.nominatim().baseUrl())
                .defaultHeader("User-Agent", props.nominatim().userAgent())
                .defaultHeader("Accept-Language", "en")
                .build();
        this.countryCodes = props.nominatim().countryCodes();
    }

    /** Up to 5 places for the text, or an empty list when nothing matches. 502 when Nominatim can't be reached. */
    public List<Place> search(String query) {
        String key = query.trim().toLowerCase(Locale.ROOT);
        List<Place> cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        List<Place> found = askNominatim(query.trim());
        cache.put(key, found);
        return found;
    }

    private List<Place> askNominatim(String query) {
        NominatimHit[] hits;
        synchronized (upstreamLock) {
            spaceOutCalls();
            try {
                hits = client.get()
                        .uri(uri -> uri.path("/search")
                                .queryParam("q", query)
                                .queryParam("format", "jsonv2")
                                .queryParam("addressdetails", 1)
                                .queryParam("limit", 5)
                                .queryParamIfPresent("countrycodes", java.util.Optional.ofNullable(countryCodes).filter(c -> !c.isBlank()))
                                .build())
                        .retrieve()
                        .body(NominatimHit[].class);
            } catch (RestClientException ex) {
                // Class name only: the exception text can contain the URL, which contains what the person typed.
                log.warn("Address search failed: error={}", ex.getClass().getSimpleName());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "ADDRESS_SERVICE_UNAVAILABLE",
                        "Address search isn't available right now. Try again in a moment.");
            }
        }
        List<Place> places = new ArrayList<>();
        for (NominatimHit hit : hits == null ? new NominatimHit[0] : hits) {
            try {
                String area = hit.address() == null ? null : hit.address().area();
                places.add(new Place(hit.display_name(), area == null ? firstPart(hit.display_name()) : area,
                        Double.parseDouble(hit.lat()), Double.parseDouble(hit.lon())));
            } catch (RuntimeException ex) {
                // A hit without usable coordinates is skipped, not an error for the person.
            }
        }
        return List.copyOf(places);
    }

    /** At least 1.1 s between two upstream calls, for Nominatim's 1-request-a-second policy. Called under the lock. */
    private void spaceOutCalls() {
        long wait = lastCallMillis + MIN_GAP_MILLIS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallMillis = System.currentTimeMillis();
    }

    private static String firstPart(String displayName) {
        int comma = displayName.indexOf(',');
        return (comma > 0 ? displayName.substring(0, comma) : displayName).trim();
    }
}
