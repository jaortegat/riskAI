package com.risk.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @Test
    @DisplayName("should return 400 with field errors for MethodArgumentNotValidException")
    @SuppressWarnings("unchecked")
    void shouldHandle400ForValidationErrors() throws NoSuchMethodException {
        // Create a binding result with field errors
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "gameName", "must not be blank"));
        bindingResult.addError(new FieldError("request", "maxPlayers", "must be greater than 0"));

        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("shouldHandle400ForValidationErrors"), -1);

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Validation failed", response.getBody().get("error"));

        Map<String, String> details = (Map<String, String>) response.getBody().get("details");
        assertNotNull(details);
        assertEquals("must not be blank", details.get("gameName"));
        assertEquals("must be greater than 0", details.get("maxPlayers"));
    }

    @Test
    @DisplayName("should return 400 with empty details for validation exception with no field errors")
    @SuppressWarnings("unchecked")
    void shouldHandleValidationWithNoFieldErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");

        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("shouldHandleValidationWithNoFieldErrors"), -1);

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, String> details = (Map<String, String>) response.getBody().get("details");
        assertTrue(details.isEmpty());
    }

    @Test
    @DisplayName("should return 404 NOT_FOUND for NoResourceFoundException")
    void shouldHandle404ForNoResourceFound() {
        var ex = new NoResourceFoundException(HttpMethod.GET, "api/games/nonexistent", null);

        ResponseEntity<Map<String, String>> response = handler.handleNoResourceFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("error"));
    }

    @Test
    @DisplayName("should return generic error message for NullPointerException")
    void shouldReturnGenericMessageForNPE() {
        var ex = new NullPointerException("some null value");

        ResponseEntity<Map<String, String>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody().get("error"),
                "Should not leak NPE details to client");
    }

    @Test
    @DisplayName("IllegalArgumentException with empty message")
    void shouldHandleEmptyMessageIllegalArgument() {
        var ex = new IllegalArgumentException("");

        ResponseEntity<Map<String, String>> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("", response.getBody().get("error"));
    }

    @Test
    @DisplayName("IllegalStateException with empty message")
    void shouldHandleEmptyMessageIllegalState() {
        var ex = new IllegalStateException("");

        ResponseEntity<Map<String, String>> response = handler.handleIllegalState(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("", response.getBody().get("error"));
    }
}
