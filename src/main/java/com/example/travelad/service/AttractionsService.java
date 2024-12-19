package com.example.travelad.service;

import com.example.travelad.beans.Attraction;
import com.example.travelad.dto.AttractionDto;
import com.example.travelad.repositories.AttractionRepository;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttractionsService {

    private static final Logger logger = LoggerFactory.getLogger(AttractionsService.class);

    private final RestTemplate restTemplate;
    private final AttractionRepository attractionRepository;

    @Value("${geoapify.api.key}")
    private String apiKey;

    private String geocodingUrl;
    private String placesUrl;

    public AttractionsService(RestTemplate restTemplate, AttractionRepository attractionRepository) {
        this.restTemplate = restTemplate;
        this.attractionRepository = attractionRepository;
    }

    @PostConstruct
    public void init() {
        geocodingUrl = "https://api.geoapify.com/v1/geocode/search";
        placesUrl = "https://api.geoapify.com/v2/places";
    }

    public List<Attraction> searchPlacesByCity(String cityName) {
        // Check database for cached attractions
        List<Attraction> cachedAttractions = attractionRepository.findByCityIgnoreCase(cityName);
        if (!cachedAttractions.isEmpty()) {
            logger.info("Returning cached attractions for city: {}", cityName);
            return cachedAttractions;
        }

        logger.info("Fetching from Geoapify API for city: {}", cityName);
        List<AttractionDto> geoapifyPlaces = fetchPlacesFromGeoapify(cityName);

        // Map Geoapify places to Attraction entities, filter existing, and save new attractions
        List<Attraction> newAttractions = geoapifyPlaces.stream()
                .map(this::mapToAttraction)
                .filter(attraction -> !attractionRepository.findByNameAndCityIgnoreCase(attraction.getName(), attraction.getCity()).isPresent())
                .map(attractionRepository::save)
                .collect(Collectors.toList());

        logger.info("Saved {} new attractions for city: {}", newAttractions.size(), cityName);
        return newAttractions;
    }

    private List<AttractionDto> fetchPlacesFromGeoapify(String cityName) {
        String geocodingRequestUrl = String.format("%s?text=%s&apiKey=%s", geocodingUrl, cityName, apiKey);
        String geocodingResponse = restTemplate.getForObject(geocodingRequestUrl, String.class);

        JSONObject geocodingJson = new JSONObject(geocodingResponse);
        JSONArray features = geocodingJson.getJSONArray("features");
        if (features.isEmpty()) {
            throw new RuntimeException("City not found in Geoapify geocoding API");
        }

        JSONObject geometry = features.getJSONObject(0).getJSONObject("geometry");
        String lon = geometry.getJSONArray("coordinates").get(0).toString();
        String lat = geometry.getJSONArray("coordinates").get(1).toString();

        String placesRequestUrl = String.format("%s?categories=tourism.sights&filter=circle:%s,%s,5000&apiKey=%s&lang=en",
                placesUrl, lon, lat, apiKey);
        String placesResponse = restTemplate.getForObject(placesRequestUrl, String.class);

        return parsePlaces(new JSONObject(placesResponse));
    }

    private List<AttractionDto> parsePlaces(JSONObject placesJson) {
        List<AttractionDto> places = new ArrayList<>();
        JSONArray features = placesJson.getJSONArray("features");

        for (int i = 0; i < features.length(); i++) {
            JSONObject properties = features.getJSONObject(i).getJSONObject("properties");

            // Extract the name, prioritizing the international name if available
            String name = properties.optJSONObject("name_international") != null
                    ? properties.getJSONObject("name_international").optString("en", properties.optString("name", null))
                    : properties.optString("name", null);

            AttractionDto place = new AttractionDto(
                    name, // Use the extracted international name
                    properties.optString("city", null),
                    properties.optString("country", null),
                    properties.optString("description", null)
            );

            place.setAddress(properties.optString("formatted", null));
            place.setPhone(properties.optJSONObject("contact") != null
                    ? properties.getJSONObject("contact").optString("phone", null)
                    : null);
            place.setWebsite(properties.optString("website", null));
            place.setOpening_hours(properties.optString("opening_hours", null));

            places.add(place);
        }
        return places;
    }


    private Attraction mapToAttraction(AttractionDto placeDto) {
        Attraction attraction = new Attraction();
        attraction.setName(placeDto.getName());
        attraction.setCity(placeDto.getCity());
        attraction.setCountry(placeDto.getCountry());
        attraction.setDescription(placeDto.getDescription());
        attraction.setAddress(placeDto.getAddress());
        attraction.setPhone(placeDto.getPhone());
        attraction.setWebsite(placeDto.getWebsite());
        attraction.setOpeningHours(placeDto.getOpening_hours());
        return attraction;
    }
}