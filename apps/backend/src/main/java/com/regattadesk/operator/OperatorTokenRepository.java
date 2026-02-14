package com.regattadesk.operator;

import com.regattadesk.operator.events.OperatorTokenEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for operator token persistence operations.
 * 
 * Provides data access methods for managing operator tokens including
 * CRUD operations and query methods for validation and authorization.
 */
public interface OperatorTokenRepository {
    
    /**
     * Saves a new operator token.
     * 
     * @param token the token to save
     * @return the saved token with generated ID if applicable
     */
    OperatorToken save(OperatorToken token);
    
    /**
     * Updates an existing operator token.
     * 
     * @param token the token to update
     * @return the updated token
     */
    OperatorToken update(OperatorToken token);
    
    /**
     * Finds a token by its ID.
     * 
     * @param id the token ID
     * @return the token if found, empty otherwise
     */
    Optional<OperatorToken> findById(UUID id);
    
    /**
     * Finds a token by its token string.
     * 
     * @param token the token string
     * @return the token if found, empty otherwise
     */
    Optional<OperatorToken> findByToken(String token);
    
    /**
     * Finds all tokens for a specific regatta.
     * 
     * @param regattaId the regatta ID
     * @return list of tokens for the regatta
     */
    List<OperatorToken> findByRegattaId(UUID regattaId);

    /**
     * Finds tokens for a specific regatta with pagination.
     *
     * @param regattaId the regatta ID
     * @param limit max number of rows to return
     * @param offset row offset
     * @return paginated tokens for the regatta
     */
    List<OperatorToken> findByRegattaId(UUID regattaId, int limit, int offset);
    
    /**
     * Finds all active tokens for a specific regatta and station.
     * 
     * @param regattaId the regatta ID
     * @param station the station identifier
     * @return list of active tokens for the regatta and station
     */
    List<OperatorToken> findActiveByRegattaIdAndStation(UUID regattaId, String station);

    /**
     * Finds active tokens for a regatta with pagination.
     *
     * @param regattaId the regatta ID
     * @param limit max number of rows to return
     * @param offset row offset
     * @return paginated active tokens for the regatta
     */
    List<OperatorToken> findActiveByRegattaId(UUID regattaId, int limit, int offset);
    
    /**
     * Finds all tokens that are currently valid (active and within validity window).
     * 
     * @param regattaId the regatta ID
     * @param checkTime the time to check validity at
     * @return list of currently valid tokens for the regatta
     */
    List<OperatorToken> findValidTokens(UUID regattaId, Instant checkTime);
    
    /**
     * Revokes a token by setting its active flag to false.
     * 
     * @param tokenId the ID of the token to revoke
     * @return true if the token was revoked, false if not found
     */
    boolean revoke(UUID tokenId);
    
    /**
     * Checks if a token exists by its token string.
     * 
     * @param token the token string
     * @return true if a token with this string exists
     */
    boolean existsByToken(String token);

    /**
     * Appends an operator token lifecycle event to the append-only event store.
     *
     * @param event operator token event to append
     */
    void appendEvent(OperatorTokenEvent event);
}
