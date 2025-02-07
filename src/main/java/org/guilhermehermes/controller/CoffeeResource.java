package org.guilhermehermes.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.guilhermehermes.model.Coffee;
import org.guilhermehermes.service.CoffeeRepository;

import java.util.Collections;
import java.util.Random;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/coffee")
public class CoffeeResource {

    private static final Logger LOGGER = Logger.getLogger(CoffeeResource.class.toString());

    @Inject
    CoffeeRepository coffeeRepository;

    private AtomicLong counter = new AtomicLong(0);

    @GET
    @Retry(maxRetries = 4)
    public List<Coffee> coffees() {
        final Long invocationNumber = counter.getAndIncrement();

        maybeFail(String.format("CoffeeResource#coffees() invocation #%d failed", invocationNumber));

        LOGGER.info("CoffeeResource#coffees() invocation #%d returning successfully");
        return coffeeRepository.getAllCoffees();
    }

    private void maybeFail(String failureLogMessage) {
        if (new Random().nextBoolean()) {
            LOGGER.log(Level.SEVERE, failureLogMessage);
            throw new RuntimeException("Resource failure.");
        }
    }

    @GET
    @Path("/{id}/recommendations")
    @Timeout(250)
    @Fallback(fallbackMethod = "fallbackRecommendations")
    public List<Coffee> recommendations(int id) {
        long started = System.currentTimeMillis();
        final long invocationNumber = counter.getAndIncrement();

        try {
            randomDelay();
            LOGGER.info("CoffeeResource#recommendations() invocation #%d returning successfully");
            return coffeeRepository.getRecommendations(id);
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, String.format("CoffeeResource#recommendations() invocation #%d failed", invocationNumber), e);
            return null;
        }
    }

    public List<Coffee> fallbackRecommendations(int id) {
        LOGGER.info("Falling back to RecommendationResource#fallbackRecommendations()");
        // safe bet, return something that everybody likes
        return Collections.singletonList(coffeeRepository.getCoffeeById(1));
    }

    private void randomDelay() throws InterruptedException {
        Thread.sleep(new Random().nextInt(500));
    }


    @Path("/{id}/availability")
    @GET
    public Response availability(int id) {
        final Long invocationNumber = counter.getAndIncrement();

        Coffee coffee = coffeeRepository.getCoffeeById(id);
        // check that coffee with given id exists, return 404 if not
        if (coffee == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        try {
            Integer availability = coffeeRepository.getAvailability(coffee);
            LOGGER.info("CoffeeResource#availability() invocation #%d returning successfully");
            return Response.ok(availability).build();
        } catch (RuntimeException e) {
            String message = e.getClass().getSimpleName() + ": " + e.getMessage();
            LOGGER.log(Level.SEVERE, String.format("CoffeeResource#availability() invocation #%d failed", invocationNumber), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(message)
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .build();
        }
    }

}
