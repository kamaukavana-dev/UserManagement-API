package com.company.usermanagement.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response body returned for all API errors.
 *
 * @JsonInclude(NON_NULL) ensures fieldErrors is omitted
 * when null (i.e., for non-validation errors).
 *
 * Consistent structure means API clients can always rely on
 * the same JSON shape for error handling.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status;
    private String error;
    private String message;
    private Instant timestamp;

    /**
     * Only populated for validation errors (HTTP 400).
     * Maps field name → validation message.
     * Example: { "email": "must be a valid email address" }
     */
    private Map<String, String> fieldErrors;
}
