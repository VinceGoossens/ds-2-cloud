package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.*;
import be.kuleuven.distributedsystems.cloud.exceptions.FlightNotFoundException;
import be.kuleuven.distributedsystems.cloud.exceptions.SeatNotFoundException;
import com.google.api.core.ApiFuture;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.firestore.*;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.gson.Gson;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static be.kuleuven.distributedsystems.cloud.auth.WebSecurityConfig.getUser;

@RestController
@RequestMapping("/api")
public class APIController {

    @Resource(name = "webClientBuilder")
    WebClient.Builder webClientBuilder;

    @Resource(name = "projectId")
    String projectId;

    @Resource(name = "channelProvider")
    TransportChannelProvider channelProvider;

    @Resource(name = "credentialsProvider")
    CredentialsProvider credentialsProvider;

    @Resource(name = "db")
    Firestore db;

    private String API_KEY = "Iw8zeveVyaPNWonPNaU0213uw3g6Ei";

    @GetMapping("/getFlights")
    public List<Flight> getFlights() {
        List<Flight> flightsArray = new ArrayList<Flight>();
        Collection<Flight> flightsCollectionReliable = this.webClientBuilder
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

        for (Flight flight : flightsCollectionReliable) {
            flightsArray.add(flight);
        }

        Collection<Flight> flightsCollectionUnreliable = this.webClientBuilder
                .baseUrl("https://unreliable-airline.com")
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("flights")
                        .queryParam("key",API_KEY)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<CollectionModel<Flight>>() {})
                .retry(3)
                .block()
                .getContent();

        for (Flight flight : flightsCollectionUnreliable) {
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
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                        response -> Mono.error(new FlightNotFoundException(airline, flightId)))
                .bodyToMono(new ParameterizedTypeReference<Flight>() {})
                .retry(3)
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
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                        response -> Mono.error(new FlightNotFoundException(airline, flightId)))
                .bodyToMono(new ParameterizedTypeReference<CollectionModel<String>>() {})
                .retry(3)
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
                .retry(3)
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
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                        response -> Mono.error(new SeatNotFoundException(airline, flightId, seatId)))
                .bodyToMono(new ParameterizedTypeReference<Seat>() {})
                .retry(3)
                .block();
        return seat;
    }

    @PostMapping ("/confirmQuotes")
    public void confirmQuotes(@RequestBody List<Quote> quotes) throws IOException {
        TopicName topicName = TopicName.of(projectId,"confirmQuotes");
        Publisher publisher =
                Publisher.newBuilder(topicName)
                        .setChannelProvider(channelProvider)
                        .setCredentialsProvider(credentialsProvider)
                        .build();
        Gson gson = new Gson();
        String quotesJSON = gson.toJson(quotes);
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder().putAttributes("quotes",quotesJSON).putAttributes("customer",getUser().getEmail().toString()).build();
        ApiFuture<String> future = publisher.publish(pubsubMessage);
    }

    @GetMapping ("/getBookings")
    public List<Booking> getBookings() throws ExecutionException, InterruptedException {
        return readBookingsFromCurrentUser();
    }

    @GetMapping ("/getAllBookings")
    public List<Booking> getAllBookings() throws Exception {
        if (getUser().isManager()) {
            return readAllBookings();
        }
        else {
            throw new IllegalAccessException("Your are not a manager");
        }
    }

    @GetMapping ("/getBestCustomers")
    public List<String> getBestCustomers() throws Exception {
        List<String> bestCustomers = new ArrayList<>();
        Map<String, Integer> nbOfTickets = new HashMap<>();
        List<Booking> bookings = readAllBookings();
        if (getUser().isManager()) {
            bookings.forEach(booking -> {
                if (nbOfTickets.containsKey(booking.getCustomer())) {
                    int oldValue = nbOfTickets.get(booking.getCustomer());
                    nbOfTickets.put(booking.getCustomer(), oldValue + booking.getTickets().size());
                }
                else {
                    nbOfTickets.put(booking.getCustomer(), booking.getTickets().size());
                }
            });

            if (nbOfTickets.isEmpty()) {
                return bestCustomers;
            }
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

    private List<Booking> readBookingsFromCurrentUser() throws ExecutionException, InterruptedException {
        List<Booking> customerBookings = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        ApiFuture<QuerySnapshot> bookingsQuery = db.collection("bookings").get();
        QuerySnapshot bookingsQuerySnapshot = bookingsQuery.get();
        List<QueryDocumentSnapshot> bookingsSnapShot = bookingsQuerySnapshot.getDocuments();
        for (QueryDocumentSnapshot bookingSnapShot : bookingsSnapShot) {
            Map<String, Object> bookingData = bookingSnapShot.getData();
            String customer = bookingData.get("customer").toString();
            if (customer.equals(getUser().getEmail())) {
                UUID bookingReference = UUID.fromString(bookingData.get("id").toString());
                LocalDateTime time = LocalDateTime.parse(bookingData.get("time").toString(),formatter);
                ApiFuture<QuerySnapshot> ticketsQuery = db.collection("bookings").document(bookingReference.toString()).collection("tickets").get();
                QuerySnapshot ticketsQuerySnapshot = ticketsQuery.get();
                List<QueryDocumentSnapshot> ticketsSnapShot = ticketsQuerySnapshot.getDocuments();
                List<Ticket> tickets = new ArrayList<>();
                for (QueryDocumentSnapshot ticketSnapShot : ticketsSnapShot) {
                    Map<String, Object> ticketData = ticketSnapShot.getData();
                    String airline = ticketData.get("airline").toString();
                    UUID flightId = UUID.fromString(ticketData.get("flightId").toString());
                    UUID seatId = UUID.fromString(ticketData.get("seatId").toString());
                    UUID ticketId = UUID.fromString(ticketData.get("ticketId").toString());
                    tickets.add(new Ticket(airline, flightId, seatId, ticketId, customer, bookingReference.toString()));
                }
                customerBookings.add(new Booking(bookingReference, time, tickets, customer));
            }
        }
        return customerBookings;
    }

    private List<Booking> readAllBookings() throws ExecutionException, InterruptedException {
        List<Booking> bookings = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        ApiFuture<QuerySnapshot> bookingsQuery = db.collection("bookings").get();
        QuerySnapshot bookingsQuerySnapshot = bookingsQuery.get();
        List<QueryDocumentSnapshot> bookingsSnapShot = bookingsQuerySnapshot.getDocuments();
        for (QueryDocumentSnapshot bookingSnapShot : bookingsSnapShot) {
            Map<String, Object> bookingData = bookingSnapShot.getData();
            String customer = bookingData.get("customer").toString();
            UUID bookingReference = UUID.fromString(bookingData.get("id").toString());
            LocalDateTime time = LocalDateTime.parse(bookingData.get("time").toString(), formatter);
            ApiFuture<QuerySnapshot> ticketsQuery = db.collection("bookings").document(bookingReference.toString()).collection("tickets").get();
            QuerySnapshot ticketsQuerySnapshot = ticketsQuery.get();
            List<QueryDocumentSnapshot> ticketsSnapShot = ticketsQuerySnapshot.getDocuments();
            List<Ticket> tickets = new ArrayList<>();
            for (QueryDocumentSnapshot ticketSnapShot : ticketsSnapShot) {
                Map<String, Object> ticketData = ticketSnapShot.getData();
                String airline = ticketData.get("airline").toString();
                UUID flightId = UUID.fromString(ticketData.get("flightId").toString());
                UUID seatId = UUID.fromString(ticketData.get("seatId").toString());
                UUID ticketId = UUID.fromString(ticketData.get("ticketId").toString());
                tickets.add(new Ticket(airline, flightId, seatId, ticketId, customer, bookingReference.toString()));
            }
            bookings.add(new Booking(bookingReference, time, tickets, customer));
        }
        return bookings;
    }
}
