package br.com.nexapay.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(UUID id, UUID aggregateId, String eventType, String payload, OffsetDateTime createdAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.published = false;
        this.createdAt = createdAt;
    }

    public static OutboxEvent pending(UUID eventId, UUID aggregateId, String eventType, String payload) {
        return new OutboxEvent(eventId, aggregateId, eventType, payload, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isPublished() {
        return published;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
