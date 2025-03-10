package com.example.travelad.service;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.exceptions.ClientException;
import com.amadeus.exceptions.ResponseException;
import com.amadeus.resources.HotelOfferSearch;
import com.amadeus.resources.Hotel;
import com.example.travelad.beans.GooglePlaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;

@Service
public class HotelsService {

    private static final Logger logger = LoggerFactory.getLogger(HotelsService.class);
    private Amadeus amadeus;
    private final GooglePlacesService googlePlacesService;

    @Value("${amadeus.api.key}")
    private String apiKey;

    @Value("${amadeus.api.secret}")
    private String apiSecret;

    public HotelsService(GooglePlacesService googlePlacesService) {
        this.googlePlacesService = googlePlacesService;
    }

    @PostConstruct
    public void init() {
        this.amadeus = Amadeus.builder(apiKey, apiSecret).build();
    }

    public Hotel[] searchHotelsByCityCode(String cityCode) throws ResponseException {
        try {
            logger.info("Fetching hotels for cityCode: {}", cityCode);
            return amadeus.referenceData.locations.hotels.byCity.get(
                    Params.with("cityCode", cityCode)
            );
        } catch (ResponseException e) {
            logger.error("Error fetching hotels by city code: {}", e.getMessage());
            throw e;
        }
    }

    public Hotel[] searchHotelsByCityName(String cityName) {
        try {
            GooglePlaces place = googlePlacesService.searchPlaceByCity(cityName);
            if (place == null) {
                logger.warn("No place found for city name: {}", cityName);
                return new Hotel[0];
            }
            logger.info("Fetching hotels for city name: {} using geocode: {}, {}", cityName, place.getLatitude(), place.getLongitude());
            Hotel[] hotels = amadeus.referenceData.locations.hotels.byGeocode.get(
                    Params.with("latitude", String.valueOf(place.getLatitude()))
                            .and("longitude", String.valueOf(place.getLongitude()))
            );
            return hotels != null ? hotels : new Hotel[0];
        } catch (ResponseException e) {
            logger.error("Amadeus API error fetching hotels by city name: {} - Status: {}, Message: {}",
                    cityName, e.getResponse().getStatusCode(), e.getMessage());
            throw new RuntimeException("Error fetching hotels from Amadeus API", e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching hotels by city name: {} - {}", cityName, e.getMessage(), e);
            throw new RuntimeException("Unexpected error processing hotel request", e);
        }
    }

    public HotelOfferSearch[] searchHotelOffers(String hotelIds, String checkInDate, String checkOutDate, Integer adults) throws ResponseException {
        Params params = Params.with("hotelIds", hotelIds);
        if (checkInDate != null && !checkInDate.isEmpty()) {
            params.and("checkInDate", checkInDate);
        }
        if (checkOutDate != null && !checkOutDate.isEmpty()) {
            params.and("checkOutDate", checkOutDate);
        }
        if (adults != null) {
            params.and("adults", adults);
        }
        logger.info("Fetching hotel offers for hotelIds: {}, checkInDate: {}, checkOutDate: {}, adults: {}",
                hotelIds, checkInDate, checkOutDate, adults);
        try {
            return amadeus.shopping.hotelOffersSearch.get(params);
        } catch (ClientException e) {
            logger.error("Client error fetching hotel offers: {}", e.getMessage(), e);
            return new HotelOfferSearch[0];
        } catch (ResponseException e) {
            logger.error("Error fetching hotel offers: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error fetching hotel offers: {}", e.getMessage(), e);
            throw new RuntimeException("An unexpected error occurred while fetching hotel offers", e);
        }
    }
}
