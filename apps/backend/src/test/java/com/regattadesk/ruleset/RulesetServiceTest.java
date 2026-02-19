package com.regattadesk.ruleset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regattadesk.eventstore.DomainEvent;
import com.regattadesk.eventstore.EventEnvelope;
import com.regattadesk.eventstore.EventMetadata;
import com.regattadesk.eventstore.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RulesetServiceTest {

    @Mock
    EventStore eventStore;

    private RulesetService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new RulesetService(eventStore, objectMapper);
    }

    @Test
    void createRuleset_shouldAppendWithProvidedCorrelationAndCausation() {
        UUID rulesetId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();

        when(eventStore.readStream(rulesetId)).thenReturn(List.of());

        RulesetAggregate aggregate = service.createRuleset(
            rulesetId,
            "Ruleset",
            "v1.0",
            "desc",
            "actual_at_start",
            correlationId,
            causationId
        );

        assertNotNull(aggregate);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DomainEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<EventMetadata> metadataCaptor = ArgumentCaptor.forClass(EventMetadata.class);
        ArgumentCaptor<Long> expectedVersionCaptor = ArgumentCaptor.forClass(Long.class);

        verify(eventStore).append(
            eq(rulesetId),
            eq("Ruleset"),
            expectedVersionCaptor.capture(),
            eventsCaptor.capture(),
            metadataCaptor.capture()
        );

        assertEquals(-1L, expectedVersionCaptor.getValue());
        assertNotNull(eventsCaptor.getValue());
        assertEquals(correlationId, metadataCaptor.getValue().getCorrelationId());
        assertEquals(causationId, metadataCaptor.getValue().getCausationId());
        assertEquals("system", metadataCaptor.getValue().getAdditionalData().get("actor"));
    }

    @Test
    void createRuleset_shouldReturnExistingAggregateWithoutAppending() throws Exception {
        UUID rulesetId = UUID.randomUUID();
        RulesetCreatedEvent created = new RulesetCreatedEvent(
            rulesetId,
            "Existing",
            "v1.0",
            "desc",
            "actual_at_start",
            false
        );

        EventEnvelope envelope = EventEnvelope.builder()
            .eventId(UUID.randomUUID())
            .aggregateId(rulesetId)
            .aggregateType("Ruleset")
            .eventType("RulesetCreated")
            .sequenceNumber(1)
            .payload(created)
            .rawPayload("""
                {
                  "rulesetId":"%s",
                  "name":"Existing",
                  "version":"v1.0",
                  "description":"desc",
                  "ageCalculationType":"actual_at_start",
                  "isGlobal":false
                }
                """.formatted(rulesetId))
            .metadata(EventMetadata.builder().build())
            .createdAt(java.time.Instant.now())
            .build();

        when(eventStore.readStream(rulesetId)).thenReturn(List.of(envelope));

        RulesetAggregate result = service.createRuleset(
            rulesetId,
            "Different",
            "v9.9",
            "different",
            "age_as_of_jan_1"
        );

        assertEquals("Existing", result.getName());
        assertEquals("v1.0", result.getRulesetVersion());
        verify(eventStore, never()).append(any(UUID.class), anyString(), anyLong(), any(), any());
    }

    @Test
    void getRuleset_shouldThrowWhenKnownRulesetEventIsMissingRawPayload() {
        UUID rulesetId = UUID.randomUUID();
        RulesetCreatedEvent created = new RulesetCreatedEvent(
            rulesetId, "Ruleset", "v1", "desc", "actual_at_start", false
        );
        EventEnvelope envelope = EventEnvelope.builder()
            .eventId(UUID.randomUUID())
            .aggregateId(rulesetId)
            .aggregateType("Ruleset")
            .eventType("RulesetCreated")
            .sequenceNumber(1)
            .payload(created)
            .rawPayload(null)
            .metadata(EventMetadata.builder().build())
            .createdAt(java.time.Instant.now())
            .build();

        when(eventStore.readStream(rulesetId)).thenReturn(List.of(envelope));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.getRuleset(rulesetId));
        assertTrue(ex.getMessage().contains("Missing raw payload"));
    }

    @Test
    void updateRuleset_shouldAppendUsingCurrentStreamVersionAsExpectedVersion() throws Exception {
        UUID rulesetId = UUID.randomUUID();
        RulesetCreatedEvent created = new RulesetCreatedEvent(
            rulesetId,
            "Ruleset",
            "v1.0",
            "desc",
            "actual_at_start",
            false
        );
        EventEnvelope envelope = EventEnvelope.builder()
            .eventId(UUID.randomUUID())
            .aggregateId(rulesetId)
            .aggregateType("Ruleset")
            .eventType("RulesetCreated")
            .sequenceNumber(1)
            .payload(created)
            .rawPayload("""
                {
                  "rulesetId":"%s",
                  "name":"Ruleset",
                  "version":"v1.0",
                  "description":"desc",
                  "ageCalculationType":"actual_at_start",
                  "isGlobal":false
                }
                """.formatted(rulesetId))
            .metadata(EventMetadata.builder().build())
            .createdAt(java.time.Instant.now())
            .build();

        when(eventStore.readStream(rulesetId)).thenReturn(List.of(envelope));

        service.updateRuleset(
            rulesetId,
            "Updated",
            "v1.1",
            "updated desc",
            "age_as_of_jan_1"
        );

        ArgumentCaptor<Long> expectedVersionCaptor = ArgumentCaptor.forClass(Long.class);
        verify(eventStore).append(
            eq(rulesetId),
            eq("Ruleset"),
            expectedVersionCaptor.capture(),
            any(),
            any(EventMetadata.class)
        );
        assertEquals(1L, expectedVersionCaptor.getValue());
    }

    @Test
    void getRuleset_shouldDeserializeCreatedEventWithLegacyGlobalField() {
        UUID rulesetId = UUID.randomUUID();
        EventEnvelope envelope = EventEnvelope.builder()
            .eventId(UUID.randomUUID())
            .aggregateId(rulesetId)
            .aggregateType("Ruleset")
            .eventType("RulesetCreated")
            .sequenceNumber(1)
            .payload(new RulesetCreatedEvent(
                rulesetId, "Ruleset", "v1.0", "desc", "actual_at_start", false
            ))
            .rawPayload("""
                {
                  "rulesetId":"%s",
                  "name":"Ruleset",
                  "version":"v1.0",
                  "description":"desc",
                  "ageCalculationType":"actual_at_start",
                  "isGlobal":false,
                  "global":false
                }
                """.formatted(rulesetId))
            .metadata(EventMetadata.builder().build())
            .createdAt(java.time.Instant.now())
            .build();

        when(eventStore.readStream(rulesetId)).thenReturn(List.of(envelope));

        RulesetAggregate aggregate = service.getRuleset(rulesetId).orElseThrow();
        assertEquals("Ruleset", aggregate.getName());
        assertEquals("v1.0", aggregate.getRulesetVersion());
        assertEquals("actual_at_start", aggregate.getAgeCalculationType());
        assertEquals(false, aggregate.isGlobal());
    }

    @Test
    void duplicateRuleset_whenSourceMissing_shouldThrow() {
        UUID sourceId = UUID.randomUUID();
        when(eventStore.readStream(sourceId)).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.duplicateRuleset(sourceId, "copy", "v2.0")
        );
        assertTrue(ex.getMessage().contains("Source ruleset not found"));
        verify(eventStore, never()).append(any(UUID.class), anyString(), anyLong(), any(), any());
    }
}
