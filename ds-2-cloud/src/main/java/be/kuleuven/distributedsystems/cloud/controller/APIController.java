package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.db.Db;
import be.kuleuven.distributedsystems.cloud.entities.*;
import org.eclipse.jetty.util.DateCache;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

import static be.kuleuven.distributedsystems.cloud.auth.WebSecurityConfig.getUser;

@RestController
@RequestMapping("/api")
public class APIController {

    @Resource(name = "webClientBuilder")
    WebClient.Builder webClientBuilder;
    private String API_KEY = "Iw8zeveVyaPNWonPNaU0213uw3g6Ei";
    private List<Booking> bookings = new ArrayList<>();

    @GetMapping("/getFlights")
    public List<Flight> getFlights() {
        Collection<Flight> flightsCollection = this.webClientBuilder
                .baseUrl("https://reliable-airline.com")
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                                .pathSegment("flights")
                                .queryParam("key",API_KEY)
                                .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<CollectionModel<Flight>>() {})
                .block()
                .getContent();

        List<Flight> flightsArray = new ArrayList<Flight>();
        for (Flight flight : flightsCollection) {
            flightsArray.add(flight);
        }

        return flightsArray;
    }

    @GetMapping("/getFlight")
    public Flight getFlight(@RequestParam("airline") String airline, @RequestParam("flightId") String flightId) {
        Flight flight = this.webClientBuilder
                .baseUrl("https://" + airline)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("flights", flightId)
                        .queryParam("key",API_KEY)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Flight>() {})
                .block();
        return flight;
    }

    @GetMapping("/getFlightTimes")
    public List<String> getFlightTimes(@RequestParam("airline") String airline, @RequestParam("flightId") String flightId) {
        Collection<String> flightsTimes = this.webClientBuilder
                .baseUrl("https://" + airline)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("flights", flightId, "times")
                        .queryParam("key",API_KEY)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<CollectionModel<String>>() {})
                .block()
                .getContent();

        List<String> flightTimesArray = new ArrayList<String>();
        for (String flightTime : flightsTimes) {
            flightTimesArray.add(flightTime);
        }
        flightTimesArray.sort(null);

        return flightTimesArray;
    }

    @GetMapping("/getAvailableSeats")
    public Map<String,List<Seat>> getAvailableSeats(@RequestParam("airline") String airline, @RequestParam("flightId") String flightId, @RequestParam("time") String time) {
        Collection<Seat> seats = this.webClientBuilder
                .baseUrl("https://" + airline)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("flights", flightId, "seats")
                        .queryParam("key",API_KEY)
                        .queryParam("time",time)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<CollectionModel<Seat>>() {})
                .block()
                .getContent();

        Map<String,Seat> seatsMapEconomy = new HashMap<>();
        Map<String,Seat> seatsMapBusiness = new HashMap<>();
        Map<String,Seat> seatsMapFirst = new HashMap<>();
        for (Seat seat : seats) {
            if (seat.getType().equals("Economy"))
                seatsMapEconomy.put(seat.getName(), seat);
            else if (seat.getType().equals("Business"))
                seatsMapBusiness.put(seat.getName(), seat);
            else
                seatsMapFirst.put(seat.getName(), seat);
        }

        List<String> keyListEconomy = new ArrayList<>(seatsMapEconomy.keySet());
        List<String> keyListLength2 = new ArrayList<String>();
        List<String> keyListLength3 = new ArrayList<String>();
        for (String key : keyListEconomy) {
            if (key.length() == 2) {
                keyListLength2.add(key);
            }
            else {
                keyListLength3.add(key);
            }
        }
        Collections.sort(keyListLength2);
        Collections.sort(keyListLength3);
        keyListLength2.addAll(keyListLength3);
        List<Seat> seatsArrayEconomy = new ArrayList<Seat>();
        for (String key : keyListLength2) {
            seatsArrayEconomy.add(seatsMapEconomy.get(key));
        }

        List<String> keyListBusiness = new ArrayList<>(seatsMapBusiness.keySet());
        keyListLength2 = new ArrayList<String>();
        keyListLength3 = new ArrayList<String>();
        for (String key : keyListBusiness) {
            if (key.length() == 2) {
                keyListLength2.add(key);
            }
            else {
                keyListLength3.add(key);
            }
        }
        Collections.sort(keyListLength2);
        Collections.sort(keyListLength3);
        keyListLength2.addAll(keyListLength3);
        List<Seat> seatsArrayBusiness = new ArrayList<Seat>();
        for (String key : keyListLength2) {
            seatsArrayBusiness.add(seatsMapBusiness.get(key));
        }

        List<String> keyListFirst = new ArrayList<>(seatsMapFirst.keySet());
        keyListLength2 = new ArrayList<String>();
        keyListLength3 = new ArrayList<String>();
        for (String key : keyListFirst) {
            if (key.length() == 2) {
                keyListLength2.add(key);
            }
            else {
                keyListLength3.add(key);
            }
        }
        Collections.sort(keyListLength2);
        Collections.sort(keyListLength3);
        keyListLength2.addAll(keyListLength3);
        List<Seat> seatsArrayFirst = new ArrayList<Seat>();
        for (String key : keyListLength2) {
            seatsArrayFirst.add(seatsMapFirst.get(key));
        }

        Map<String,List<Seat>> seatsMap = new HashMap<>();
        seatsMap.put("Economy", seatsArrayEconomy);
        seatsMap.put("Business", seatsArrayBusiness);
        seatsMap.put("First", seatsArrayFirst);

        return seatsMap;
    }

    @GetMapping("/getSeat")
    public Seat getSeat(@RequestParam("airline") String airline, @RequestParam("flightId") String flightId, @RequestParam("seatId") String seatId) {
        Seat seat = this.webClientBuilder
                .baseUrl("https://" + airline)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("flights", flightId, "seats", seatId)
                        .queryParam("key",API_KEY)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Seat>() {})
                .block();

        return seat;
    }

    @PostMapping ("/confirmQuotes")
    public void confirmQuotes(@RequestBody List<Quote> quotes) {
        UUID bookingReference = UUID.randomUUID();
        List<Ticket> tickets = new ArrayList<>();
        for (Quote quote : quotes) {
            Ticket ticket = this.webClientBuilder
                    .baseUrl("https://" + quote.getAirline())
                    .build()
                    .put()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment("flights", quote.getFlightId().toString(), "seats", quote.getSeatId().toString(), "ticket")
                            .queryParam("customer",getUser().getEmail())
                            .queryParam("bookingReference", bookingReference)
                            .queryParam("key",API_KEY)
                            .build())
                    .retrieve()
                    .bodyToMono(Ticket.class)
                    .block();
            tickets.add(ticket);
        }
        Booking booking = new Booking(bookingReference, LocalDateTime.now(), tickets, getUser().getEmail());
        bookings.add(booking);
    }

    @GetMapping ("/getBookings")
    public List<Booking> getBookings() {
        List<Booking> customerBookings = new ArrayList<>();
        for (Booking booking : bookings) {
            System.out.println(getUser().getEmail());
            if (booking.getCustomer() == getUser().getEmail()) {
                customerBookings.add(booking);
            }
            System.out.println(customerBookings);
        }
        return customerBookings;
    }

    @GetMapping ("/getAllBookings")
    public List<Booking> getAllBookings() throws Exception {
        if (getUser().isManager())
            return bookings;
        else {
            throw new IllegalAccessException("Your are not a manager");
        }
    }

    @GetMapping ("/getBestCustomer")
    public List<String> getBestCustomer() throws Exception {
        List<String> bestCustomers = new ArrayList<>();
        Map<String, Integer> nbOfTickets = new HashMap<>();
        if (getUser().isManager()) {
            bookings.forEach(booking -> {
                if (nbOfTickets.containsKey(booking.getCustomer())) {
                    int oldValue = nbOfTickets.get(booking.getCustomer());
                    nbOfTickets.put(booking.getCustomer(), oldValue + 1);
                }
                else {
                    nbOfTickets.put(booking.getCustomer(), 1);
                }
            });

            int maxNbOfTickets = Collections.max(nbOfTickets.values());
            for (Map.Entry<String, Integer> entry : nbOfTickets.entrySet()) {
                if (entry.getValue() == maxNbOfTickets) {
                    bestCustomers.add(entry.getKey());
                }
            }
            return bestCustomers;
        }
        else {
            throw new IllegalAccessException("Your are not a manager");
        }
    }


}
