package org.acme.service;

import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.acme.messaging.KafkaProducer;
import org.acme.model.Event;
import org.acme.service.elasticsearch.ElasticsearchQueryFactory;
import org.acme.service.elasticsearch.ElasticsearchService;

import java.util.List;

@ApplicationScoped
public class EventService {
    private final KafkaProducer kafkaProducer;
    private final ElasticsearchService elasticsearchService;
    private final ElasticsearchQueryFactory elasticsearchQueryFactory;
    private final EventFactoryService eventFactoryService;

    @Inject
    public EventService(KafkaProducer kafkaProducer,
                        ElasticsearchService elasticsearchService,
                        ElasticsearchQueryFactory elasticsearchQueryFactory,
                        EventFactoryService eventFactoryService) {
        this.kafkaProducer = kafkaProducer;
        this.elasticsearchService = elasticsearchService;
        this.elasticsearchQueryFactory = elasticsearchQueryFactory;
        this.eventFactoryService = eventFactoryService;
    }

    public Uni<Response> createEvent(Event event) {
        Log.info("Publishing event: " + event.getEventId());

        return kafkaProducer.publishKafkaEvent(event)
                .onItem()
                .transform(unused -> Response.ok().build());
    }

    public Uni<List<Event>> retrieveEvents(String index) {
        Log.info("Retrieving all events");

        SearchRequest searchRequest = elasticsearchQueryFactory.searchEventsRequest(index);
        return Uni.createFrom().completionStage(() -> elasticsearchService.searchOperation(searchRequest))
                .onItem()
                .transform(eventFactoryService::extractEventList);
    }

    public Uni<Event> retrieveEventBy(String index, String eventId) {
        Log.info("Retrieving event: " + eventId);

        SearchRequest searchRequest = elasticsearchQueryFactory.searchEventByEventIdRequest(index, eventId);
        return Uni.createFrom().completionStage(() -> elasticsearchService.searchOperation(searchRequest))
                .onItem()
                .transform(eventFactoryService::extractEvent);
    }

    public Uni<Response> updateEvent(Event event) {
        Log.info("Updating event: " + event.getEventId());

        UpdateRequest<Event, Object> updateRequest = elasticsearchQueryFactory.updateEventRequest(event);
        return Uni.createFrom().completionStage(() -> elasticsearchService.updateEvent(updateRequest))
                .onItem()
                .transform(deleteResponse -> Response.ok().build());
    }

    public Uni<Response> deleteEventBy(String index, String eventId) {
        Log.info("Deleting event: " + eventId);

        DeleteRequest deleteRequest = elasticsearchQueryFactory.deleteEventByEventIdRequest(index, eventId);
        return Uni.createFrom().completionStage(() -> elasticsearchService.deleteOperation(deleteRequest))
                .onItem()
                .transform(deleteResponse -> Response.ok().build());
    }
}
