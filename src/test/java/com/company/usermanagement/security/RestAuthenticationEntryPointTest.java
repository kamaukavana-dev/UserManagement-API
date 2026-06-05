package com.company.usermanagement.security;

import com.company.usermanagement.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestAuthenticationEntryPointTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private AuthenticationException authException;

    private RestAuthenticationEntryPoint authenticationEntryPoint;

    @BeforeEach
    void setUp() {
        authenticationEntryPoint = new RestAuthenticationEntryPoint(objectMapper);
    }

    @Test
    void commence_ShouldReturnUnauthorizedResponse() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        authenticationEntryPoint.commence(request, response, authException);

        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(response).setHeader("WWW-Authenticate", "Bearer realm=\"usermanagement\"");

        ArgumentCaptor<ErrorResponse> captor = ArgumentCaptor.forClass(ErrorResponse.class);
        verify(objectMapper).writeValue(eq(printWriter), captor.capture());

        ErrorResponse errorResponse = captor.getValue();
        assertThat(errorResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(errorResponse.getMessage()).isEqualTo("Authentication is required to access this resource");
    }
}
