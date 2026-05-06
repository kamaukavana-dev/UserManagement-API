package com.company.usermanagement.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 documentation configuration.
 *
 * @OpenAPIDefinition — global metadata shown at the top of Swagger UI
 * @SecurityScheme — defines the "bearerAuth" scheme referenced by
 *   @SecurityRequirement on controllers. This is what shows the
 *   "Authorize" button in Swagger UI where you paste your JWT.
 *
 * SpringDoc reads all @RestController classes automatically —
 * we only need to configure global metadata and security here.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "User Management API",
        version = "v1",
        description = """
                Production-ready User Management REST API.
                
                ## Authentication
                1. Register via `POST /auth/register` or login via `POST /auth/login`
                2. Copy the `accessToken` from the response
                3. Click **Authorize** and enter: `Bearer <your_token>`
                4. All protected endpoints will now include your token automatically

                ## Account Management
                - `PUT /users/me/password` changes the authenticated user's password
                - `POST /users` lets an ADMIN create a new user and assign `ROLE_USER` or `ROLE_ADMIN`
                - `PATCH /users/{id}/role` lets an ADMIN promote or demote an existing user

                ## Roles
                - **USER** — can view and update their own profile
                - **ADMIN** — full access to all user management operations
                """,
        contact = @Contact(
            name = "Software Engineer Daniel Maina",
            email = "backend@company.com"
        ),
        license = @License(
            name = "MIT License",
            url = "https://opensource.org/licenses/MIT"
        )
    ),
    servers = {
        @Server(url = "/api/v1", description = "Development Server"),
        @Server(url = "https://api.company.com/api/v1", description = "Production Server")
    }
)
@SecurityScheme(
    name = "bearerAuth",                        // referenced by @SecurityRequirement
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER,
    description = "Paste your JWT access token here. Prefix 'Bearer ' is added automatically."
)
public class OpenApiConfig {
    // All configuration is via annotations — no bean methods needed
}
