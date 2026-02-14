package com.regattadesk.public_api;

import com.regattadesk.jwt.JwtConfig;
import com.regattadesk.jwt.JwtTokenService;
import com.regattadesk.jwt.JwtTokenService.InvalidTokenException;
import com.regattadesk.jwt.JwtTokenService.ValidatedToken;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

/**
 * REST resource for public anonymous session management.
 * 
 * This endpoint mints or refreshes anonymous JWT cookies for public clients.
 * The cookie is used for SSE connection tracking and public API authentication.
 */
@Path("/public/session")
public class PublicSessionResource {
    private static final Logger LOG = Logger.getLogger(PublicSessionResource.class);
    
    private static final String COOKIE_NAME = "regattadesk_public_session";
    
    @Inject
    JwtTokenService jwtTokenService;

    @Inject
    JwtConfig jwtConfig;
    
    /**
     * Mints or refreshes an anonymous public session cookie.
     * 
     * Returns 204 with Set-Cookie header if:
     * - No cookie exists (new session)
     * - Cookie is invalid (expired or malformed)
     * - Cookie is valid but in refresh window (last 20% of TTL)
     * 
     * Returns 204 without Set-Cookie if cookie is valid and not in refresh window.
     * 
     * @param existingCookie the existing session cookie, if any
     * @return 204 No Content with optional Set-Cookie header
     */
    @POST
    public Response createOrRefreshSession(@CookieParam(COOKIE_NAME) Cookie existingCookie) {
        boolean shouldRefresh = shouldRefreshCookie(existingCookie);
        
        if (shouldRefresh) {
            String token = jwtTokenService.issueToken();
            LOG.debug("Refreshing or issuing public session cookie");
            
            NewCookie cookie = new NewCookie.Builder(COOKIE_NAME)
                .value(token)
                .path("/")
                .maxAge(jwtConfig.ttlSeconds())
                .secure(true)
                .httpOnly(true)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
            
            return Response.noContent()
                .header("Cache-Control", "no-store")
                .cookie(cookie)
                .build();
        } else {
            LOG.debug("Public session cookie is valid and outside refresh window");
            return Response.noContent()
                .header("Cache-Control", "no-store")
                .build();
        }
    }
    
    /**
     * Determines if the cookie should be refreshed.
     * 
     * @param cookie the existing cookie
     * @return true if cookie should be refreshed, false otherwise
     */
    private boolean shouldRefreshCookie(Cookie cookie) {
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return true; // No cookie exists, issue new one
        }
        
        try {
            ValidatedToken token = jwtTokenService.validateToken(cookie.getValue());
            return jwtTokenService.isInRefreshWindow(token);
        } catch (InvalidTokenException e) {
            // Invalid or expired token, issue new one
            return true;
        }
    }
}
