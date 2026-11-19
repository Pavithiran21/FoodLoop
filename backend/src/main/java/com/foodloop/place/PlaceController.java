package com.foodloop.place;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodloop.common.web.RateLimiter;
import com.foodloop.common.web.RateLimiter.Limit;
import com.foodloop.place.PlaceService.Place;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Address search for sign-up ("Find address", before there is an account) and for the location picker. Public, so it is
 * limited per IP address; results are cached and Nominatim is called at most once a second (see PlaceService).
 */
@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceService placeService;
    private final RateLimiter rateLimiter;

    public PlaceController(PlaceService placeService, RateLimiter rateLimiter) {
        this.placeService = placeService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/search")
    public List<Place> search(
            @RequestParam("q") @NotBlank(message = "Type an address or area.") @Size(min = 3, max = 120, message = "Type 3 to 120 characters.") String query,
            HttpServletRequest request) {
        rateLimiter.consume(Limit.PLACES_IP, request.getRemoteAddr());
        return placeService.search(query);
    }
}
