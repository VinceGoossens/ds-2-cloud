package be.kuleuven.distributedsystems.cloud.controller;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Quote;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import be.kuleuven.distributedsystems.cloud.exceptions.FlightNotFoundException;
import be.kuleuven.distributedsystems.cloud.exceptions.SeatAlreadyBookedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

@RestController
@RequestMapping("/pubsub")
public class PubSubController {

    @Resource(name = "webClientBuilder")
    WebClient.Builder webClientBuilder;

    @Resource(name = "db")
    Firestore db;

    private String API_KEY = "Iw8zeveVyaPNWonPNaU0213uw3g6Ei";

    private static List<Booking> bookings = new ArrayList<>();

    @PostMapping("/subscription")
    public ResponseEntity subscription(@RequestBody String body) throws JsonProcessingException, ExecutionException, InterruptedException {
        UUID bookingReference = UUID.randomUUID();
        List<Ticket> tickets = new ArrayList<>();
        Gson gson = new Gson();
        JsonObject jobj = gson.fromJson(body, JsonObject.class);
        String jquotes = jobj.getAsJsonObject("message").getAsJsonObject("attributes").get("quotes").getAsString();
        ObjectMapper objectMapper = new ObjectMapper();
        List<Quote> quotes = objectMapper.readValue(jquotes, new TypeReference<List<Quote>>(){});
        String customer = jobj.getAsJsonObject("message").getAsJsonObject("attributes").get("customer").getAsString();

        try {
            for (Quote quote : quotes) {
                Ticket ticket = this.webClientBuilder
                        .baseUrl("https://" + quote.getAirline())
                        .build()
                        .put()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("flights", quote.getFlightId().toString(), "seats", quote.getSeatId().toString(), "ticket")
                                .queryParam("customer", customer)
                                .queryParam("bookingReference", bookingReference)
                                .queryParam("key", API_KEY)
                                .build())
                        .retrieve()
                        .onStatus(status -> status.value() == HttpStatus.CONFLICT.value(),
                                response -> Mono.error(new SeatAlreadyBookedException(quote.getAirline(), quote.getFlightId().toString(), quote.getSeatId().toString())))
                        .bodyToMono(Ticket.class)
                        .retry(3)
                        .block();
                tickets.add(ticket);
            }
        } catch (Exception e) {
            deleteTickets(tickets);
            tickets = new ArrayList<>();
        }

        if (!tickets.isEmpty()) {
            Booking booking = new Booking(bookingReference, LocalDateTime.now(), tickets, customer);
            storeBooking(booking);
        }

        return ResponseEntity.status(HttpStatus.OK).body("Pubsub message succesfully received");
    }

    private void storeBooking(Booking booking) throws ExecutionException, InterruptedException {
        DocumentReference bookingDocumentRef = db.collection("bookings").document(booking.getId().toString());
        Map<String, Object> bookingData = new HashMap<>();
        bookingData.put("id", booking.getId().toString());
        bookingData.put("time", booking.getTime().toString());
        bookingData.put("customer", booking.getCustomer());
        ApiFuture<WriteResult> future = bookingDocumentRef.set(bookingData);
        future.get();

        Map<String, Object> ticketData;
        for (Ticket ticket : booking.getTickets()) {
            ticketData = new HashMap<>();
            ticketData.put("airline",ticket.getAirline());
            ticketData.put("flightId",ticket.getFlightId().toString());
            ticketData.put("seatId",ticket.getSeatId().toString());
            ticketData.put("ticketId",ticket.getTicketId().toString());
            ticketData.put("Customer",ticket.getCustomer());
            future = bookingDocumentRef.collection("tickets").document(ticket.getTicketId().toString()).set(ticketData);
            future.get();
        }
    }

    private void deleteTickets(List<Ticket> tickets) {
        for (Ticket ticket : tickets) {
            this.webClientBuilder
                    .baseUrl("https://" + ticket.getAirline())
                    .build()
                    .delete()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment("flights", ticket.getFlightId().toString(), "seats", ticket.getSeatId().toString(), "ticket", ticket.getTicketId().toString())
                            .queryParam("key",API_KEY)
                            .build())
                    .retrieve()
                    .bodyToMono(Ticket.class)
                    .retry(3)
                    .block();
        }
    }
}