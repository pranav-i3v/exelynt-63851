package com.exelynt.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the Resource Booking System.
 *
 * <p>The application is a modular monolith: every bounded context (auth, user,
 * resource, reservation, audit) lives in its own package but ships as one
 * deployable unit.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }
}
