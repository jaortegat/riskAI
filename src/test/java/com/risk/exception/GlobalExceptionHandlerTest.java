package com.risk.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GlobalExceptionHandler.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("should return 400 BAD_REQUEST for IllegalArgumentException")
    void shouldHandle400ForIllegalArgument() {
        var ex = new IllegalArgumentException("Invalid territory");

        ResponseEntity<Map<String, String>> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid territory", response.getBody().get("error"));
    }

    @Test
    @DisplayName("should return 409 CONFLICT for IllegalStateException")
    void shouldHandle409ForIllegalState() {
        var ex = new IllegalStateException("Game already started");

        ResponseEntity<Map<String, String>> response = handler.handleIllegalState(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Game already started", response.getBody().get("error"));
    }

    @Test
    @DisplayName("should return 500 INTERNAL_SERVER_ERROR for generic Exception")
    void shouldHandle500ForGenericException() {
        var ex = new RuntimeException("Something went wrong");

        ResponseEntity<Map<String, String>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody().get("error"));
    }
}
