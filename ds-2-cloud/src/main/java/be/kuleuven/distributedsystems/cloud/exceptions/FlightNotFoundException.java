package be.kuleuven.distributedsystems.cloud.exceptions;

public class FlightNotFoundException extends Exception {

    public FlightNotFoundException(String airline, String flightId) {
        super ("Flight " + flightId + " of " + airline + " not found.");
    }
}
