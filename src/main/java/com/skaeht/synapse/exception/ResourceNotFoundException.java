package com.skaeht.synapse.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

/**
 * ARCHITECTURE NOTE: Standard 404 Exception
 * Extending RuntimeException ensures that methods throwing this do not pollute
 * the method signatures across the Service and Controller layers (avoiding checked exceptions).
 * * The @ResponseStatus annotation serves as a fallback mechanism, ensuring that even if
 * the GlobalExceptionHandler fails or is bypassed, Spring MVC will still map this exception
 * to a 404 Not Found HTTP response.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}