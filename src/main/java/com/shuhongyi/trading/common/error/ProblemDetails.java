package com.shuhongyi.trading.common.error;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;

/**
 * Builds problem details with a consistent shape: a {@code urn:problem-type:<code>} type
 * and a machine-readable {@code code} property alongside the standard RFC 9457 fields.
 */
public final class ProblemDetails {

    private static final String TYPE_PREFIX = "urn:problem-type:";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setProperty("code", code);
        return problem;
    }
}
