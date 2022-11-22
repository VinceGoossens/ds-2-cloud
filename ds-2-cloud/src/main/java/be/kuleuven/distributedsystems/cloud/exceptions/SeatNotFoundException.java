package be.kuleuven.distributedsystems.cloud.exceptions;

public class SeatNotFoundException extends Exception {

    public SeatNotFoundException(String airline, String flightId, String seatId) {
        super("Seat " + seatId + "on flight " + flightId + " of " + airline + " not found.");
    }
}
