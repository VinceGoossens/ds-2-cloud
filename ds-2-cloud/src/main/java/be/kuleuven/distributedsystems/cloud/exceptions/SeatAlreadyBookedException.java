package be.kuleuven.distributedsystems.cloud.exceptions;

public class SeatAlreadyBookedException extends Exception {

    public SeatAlreadyBookedException(String airline, String flightId, String seatId) {
        super("Seat " + seatId + "on flight " + flightId + " of " + airline + " is already booked.");
    }
}
