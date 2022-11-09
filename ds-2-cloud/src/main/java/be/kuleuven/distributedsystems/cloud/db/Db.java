package be.kuleuven.distributedsystems.cloud.db;

import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.User;

import java.util.*;

public class Db {

    private Map<User, List<Booking>> bookings = new HashMap<>();

    public List<Booking> getBookings(User user) {
        return bookings.get(user);
    }

    public void addBooking (User user, Booking booking) {
        if (bookings.get(user) != null) {
            List<Booking> oldBookings = bookings.get(user);
            oldBookings.add(booking);
            bookings.put(user, oldBookings);
        }
        else {
            List<Booking> newBookings = new ArrayList<>();
            newBookings.add(booking);
            bookings.put(user, newBookings);
        }
    }
}
