package com.regattadesk.entry;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Integration tests for Entry payment status management (BC08-001).
 * 
 * Tests the complete workflow of updating payment status for entries,
 * including authorization and audit event generation.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntryPaymentStatusIT {

    @Inject
    DataSource dataSource;
    
    @Inject
    EntryService entryService;
    
    private UUID testRegattaId;
    private UUID testEventId;
    private UUID testBlockId;
    private UUID testCrewId;
    private UUID testEntryId;

    @BeforeAll
    @Transactional
    void setupTestData() throws Exception {
        // Create minimal test data in database for testing
        testRegattaId = UUID.randomUUID();
        testEventId = UUID.randomUUID();
        testBlockId = UUID.randomUUID();
        testCrewId = UUID.randomUUID();
        
        // Create test regatta, event, block, crew via direct DB insert
        // This is a workaround until full CRUD APIs are available
        try (Connection conn = dataSource.getConnection()) {
            // Insert test regatta
            String regattaSql = "INSERT INTO regattas (id, name, time_zone, status) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(regattaSql)) {
                stmt.setObject(1, testRegattaId);
                stmt.setString(2, "Test Regatta");
                stmt.setString(3, "Europe/Amsterdam");
                stmt.setString(4, "draft");
                stmt.executeUpdate();
            }
            
            // Insert test category
            UUID categoryId = UUID.randomUUID();
            String categorySql = "INSERT INTO categories (id, name) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(categorySql)) {
                stmt.setObject(1, categoryId);
                stmt.setString(2, "Test Category");
                stmt.executeUpdate();
            }
            
            // Insert test boat type (use unique code)
            UUID boatTypeId = UUID.randomUUID();
            String uniqueCode = "T" + (System.currentTimeMillis() % 100000000); // Max 9 digits + T
            String boatTypeSql = "INSERT INTO boat_types (id, code, name, rowers, coxswain, sculling) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(boatTypeSql)) {
                stmt.setObject(1, boatTypeId);
                stmt.setString(2, uniqueCode);
                stmt.setString(3, "Test Scull");
                stmt.setInt(4, 1);
                stmt.setBoolean(5, false);
                stmt.setBoolean(6, true);
                stmt.executeUpdate();
            }
            
            // Insert test event
            String eventSql = "INSERT INTO events (id, regatta_id, category_id, boat_type_id, name) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(eventSql)) {
                stmt.setObject(1, testEventId);
                stmt.setObject(2, testRegattaId);
                stmt.setObject(3, categoryId);
                stmt.setObject(4, boatTypeId);
                stmt.setString(5, "Test Event");
                stmt.executeUpdate();
            }
            
            // Insert test block
            String blockSql = "INSERT INTO blocks (id, regatta_id, name, start_time) VALUES (?, ?, ?, NOW())";
            try (PreparedStatement stmt = conn.prepareStatement(blockSql)) {
                stmt.setObject(1, testBlockId);
                stmt.setObject(2, testRegattaId);
                stmt.setString(3, "Test Block");
                stmt.executeUpdate();
            }
            
            // Insert test crew
            String crewSql = "INSERT INTO crews (id, display_name, is_composite) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(crewSql)) {
                stmt.setObject(1, testCrewId);
                stmt.setString(2, "Test Crew");
                stmt.setBoolean(3, false);
                stmt.executeUpdate();
            }
        }
        
        // Create entry via service (this will use event sourcing)
        var entry = entryService.createEntry(testRegattaId, testEventId, testBlockId, testCrewId, null);
        testEntryId = entry.id();
    }

    @Test
    void updatePaymentStatus_withFinancialManager_shouldSucceed() {
        given()
            .header("Remote-User", "finance")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("""
                {
                  "payment_status": "paid",
                  "paid_at": "2026-02-20T10:30:00Z",
                  "paid_by": "Finance Staff",
                  "payment_reference": "BANK-2026-001"
                }
                """)
            .when()
            .patch("/api/v1/entries/" + testEntryId + "/payment-status")
            .then()
            .statusCode(200)
            .body("payment_status", equalTo("paid"))
            .body("paid_at", notNullValue())
            .body("paid_by", equalTo("Finance Staff"))
            .body("payment_reference", equalTo("BANK-2026-001"));
    }

    @Test
    void updatePaymentStatus_withoutFinancialRole_shouldReturn403() {
        given()
            .header("Remote-User", "info_desk_user")
            .header("Remote-Groups", "info_desk")
            .contentType("application/json")
            .body("""
                {
                  "payment_status": "paid"
                }
                """)
            .when()
            .patch("/api/v1/entries/" + testEntryId + "/payment-status")
            .then()
            .statusCode(403);
    }

    @Test
    void updatePaymentStatus_withRegattaAdmin_shouldSucceed() {
        // REGATTA_ADMIN should also be able to update payment status
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "regatta_admin")
            .contentType("application/json")
            .body("""
                {
                  "payment_status": "paid",
                  "paid_at": "2026-02-20T11:00:00Z"
                }
                """)
            .when()
            .patch("/api/v1/entries/" + testEntryId + "/payment-status")
            .then()
            .statusCode(200)
            .body("payment_status", equalTo("paid"));
    }

    @Test
    void updatePaymentStatus_withSuperAdmin_shouldSucceed() {
        // SUPER_ADMIN should also be able to update payment status
        given()
            .header("Remote-User", "superadmin")
            .header("Remote-Groups", "super_admin")
            .contentType("application/json")
            .body("""
                {
                  "payment_status": "unpaid"
                }
                """)
            .when()
            .patch("/api/v1/entries/" + testEntryId + "/payment-status")
            .then()
            .statusCode(200)
            .body("payment_status", equalTo("unpaid"));
    }

    @Test
    void updatePaymentStatus_invalidStatus_shouldReturn400() {
        given()
            .header("Remote-User", "finance")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("""
                {
                  "payment_status": "partially_paid"
                }
                """)
            .when()
            .patch("/api/v1/entries/" + testEntryId + "/payment-status")
            .then()
            .statusCode(400);
    }

    @Test
    void updatePaymentStatus_nonExistentEntry_shouldReturn404() {
        UUID nonExistentId = UUID.randomUUID();
        
        given()
            .header("Remote-User", "finance")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("""
                {
                  "payment_status": "paid",
                  "paid_at": "2026-02-20T10:30:00Z"
                }
                """)
            .when()
            .patch("/api/v1/entries/" + nonExistentId + "/payment-status")
            .then()
            .statusCode(404)
            .body("error.code", equalTo("NOT_FOUND"));
    }

    @Test
    void updatePaymentStatus_markUnpaid_shouldClearMetadata() {
        // First mark as paid
        given()
            .header("Remote-User", "finance")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("""
                {
                  "payment_status": "paid",
                  "paid_at": "2026-02-20T10:30:00Z",
                  "paid_by": "Finance Staff",
                  "payment_reference": "REF-001"
                }
                """)
            .when()
            .patch("/api/v1/entries/" + testEntryId + "/payment-status")
            .then()
            .statusCode(200);
        
        // Then mark as unpaid
        given()
            .header("Remote-User", "finance")
            .header("Remote-Groups", "financial_manager")
            .contentType("application/json")
            .body("""
                {
                  "payment_status": "unpaid"
                }
                """)
            .when()
            .patch("/api/v1/entries/" + testEntryId + "/payment-status")
            .then()
            .statusCode(200)
            .body("payment_status", equalTo("unpaid"))
            .body("paid_at", nullValue())
            .body("paid_by", nullValue())
            .body("payment_reference", nullValue());
    }

    @Test
    void getEntry_shouldIncludePaymentStatus() {
        given()
            .header("Remote-User", "admin")
            .header("Remote-Groups", "super_admin")
            .when()
            .get("/api/v1/entries/" + testEntryId)
            .then()
            .statusCode(200)
            .body("id", equalTo(testEntryId.toString()))
            .body("payment_status", equalTo("unpaid"));
    }
}
