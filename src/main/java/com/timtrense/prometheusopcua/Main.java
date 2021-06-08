package com.timtrense.prometheusopcua;

import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.UaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Core logic of the application:
 * (1) opening opc ua server connection
 * (2) registering opc ua nodes for monitoring
 * (3) writing the received values to a circular buffer
 * (4) exposing the buffer via http get /metrics
 *
 * @author Tim Trense
 */
@Log4j2
@Data
public class Main {

    @SneakyThrows
    public static void main( String[] args ) {
        log.info( "starting" );

        // 0. read configuration
        String configFile = ( args.length > 0 ? args[0] : "/etc/prometheusopcua.conf.yaml" );
        Configuration configuration = Configuration.read( configFile );

        // 1. prepare the internal buffer
        log.info( "initializing internal buffer with size={}", configuration::getBufferSize );
        Buffer buffer = new Buffer( configuration.getBufferSize() );

        // 2. connect opc ua
        // 2.1 open connection
        EndpointDescription endpointDescription = discoverTargetedEndpoint( configuration );
        UaClient client = connect( configuration, endpointDescription );
        // 2.2 write each new incoming value to the internal buffer
        UaMonitoredItem.ValueConsumer valueConsumer = createValueConsumer( configuration, buffer );
        // 2.3 register subscriptions and monitors to receive incoming values
        configuration.getSubscriptions().forEach( s -> createSubscription( client, s, valueConsumer ) );

        // 2. expose buffer via prometheus /metrics http endpoint
        PrometheusMetricsServer jettyServer = new PrometheusMetricsServer( configuration, buffer );
        jettyServer.start();

        // 3. run indefinitely
        log.info( "up" );
        jettyServer.join();

        log.info( "interrupted" );
        jettyServer.stop();
        client.disconnect().get();
    }

    @SneakyThrows
    private static EndpointDescription discoverTargetedEndpoint( @NonNull Configuration configuration ) {
        log.info( "discovering endpoints on {}", configuration::getDiscoveryUrl );
        CompletableFuture<List<EndpointDescription>> endpointListFuture = DiscoveryClient.getEndpoints( configuration.getDiscoveryUrl() );
        List<EndpointDescription> endpointList = endpointListFuture.get();
        if ( endpointList == null ) {
            throw new NullPointerException( "could not get endpoint list from discovery server" );
        }
        endpointList.forEach( e -> log.debug( "endpoint found: {}", e::toString ) );
        Optional<EndpointDescription> endpointDescriptionOptional = endpointList.stream()
                .filter( e -> e.getEndpointUrl().equals( configuration.getEndpointUrl() ) )
                .filter( e -> e.getSecurityMode() == configuration.getMessageSecurityMode() )
                .findAny();
        if ( endpointDescriptionOptional.isEmpty() ) {
            throw new NullPointerException( "could not find any endpoint matching the configuration" );
        }
        return endpointDescriptionOptional.get();
    }

    @SneakyThrows
    private static UaClient connect( @NonNull Configuration configuration, @NonNull EndpointDescription endpointDescription ) {
        log.info( "building opc ua client for endpoint: {}", endpointDescription );
        OpcUaClientConfigBuilder opcUaClientConfigBuilder = new OpcUaClientConfigBuilder();
        opcUaClientConfigBuilder.setApplicationName( LocalizedText.english( configuration.getApplicationName() ) );
        opcUaClientConfigBuilder.setApplicationUri( configuration.getApplicationUri() );
        opcUaClientConfigBuilder.setEndpoint( endpointDescription );
        OpcUaClientConfig opcUaClientConfig = opcUaClientConfigBuilder.build();
        OpcUaClient client = OpcUaClient.create( opcUaClientConfig );
        UaClient opcua = client.connect().get();
        if ( opcua == null ) {
            throw new NullPointerException( "could not connect to endpoint" );
        }
        return opcua;
    }

    private static UaMonitoredItem.ValueConsumer createValueConsumer( @NonNull Configuration configuration, @NonNull Buffer buffer ) {
        log.info( "creating the opc ua monitor value consumer for writing to the internal buffer" );

        // mapping node ids to metric names
        Map<NodeId, CircularFifoQueue<DataValue>> targetQueues = new HashMap<>();
        configuration.getSubscriptions().forEach( s ->
                s.getMonitors().forEach( m -> {
                    NodeId nodeId = NodeId.parse( m.getIdentifier() );
                    String metricName = m.getMetricName();
                    CircularFifoQueue<DataValue> targetQueue = buffer.getNodeBuffer( metricName );
                    targetQueues.put( nodeId, targetQueue );
                } ) );

        // on each new incoming value, lookup the metrics circular buffer and push that value to it
        return ( item, value ) -> {
            NodeId nodeId = item.getReadValueId().getNodeId();
            Object valueRaw = value.getValue().getValue();
            StatusCode statusCode = value.getStatusCode();
            log.trace( "{} {} {} ", nodeId, statusCode, valueRaw );
            targetQueues.get( nodeId ).offer( value );
        };
    }

    @SneakyThrows
    private static void createSubscription( UaClient client,
            @NonNull SubscriptionConfiguration configuration,
            @NonNull UaMonitoredItem.ValueConsumer valueConsumer ) {
        log.info( "creating opc ua subscription" );
        CompletableFuture<UaSubscription> subscriptionFuture = client.getSubscriptionManager().createSubscription( configuration.getRequestedPublishingInterval() );
        UaSubscription subscription = subscriptionFuture.get();
        if ( subscription == null ) {
            throw new NullPointerException( "could not create subscription" );
        }

        log.debug( "opc ua subscription {} created, creating monitors", subscription::getSubscriptionId );
        List<MonitoredItemCreateRequest> createMonitoredItemsRequests = configuration.getMonitors().stream()
                .map( m -> getCreateMonitorRequest( m, subscription.nextClientHandle() ) )
                .collect( Collectors.toList() );

        UaMonitoredItem.EventConsumer eventConsumer = ( item, event ) ->
                log.warn( "monitor event occurred on node={} event={}", item::getReadValueId, event::toString );

        UaSubscription.ItemCreationCallback itemCreationCallback = ( item, index ) -> {
            if ( item.getStatusCode().isGood() ) {
                log.debug( "monitor created: clientHandle={} node={}", item::getClientHandle, item.getReadValueId()::getNodeId );
                item.setValueConsumer( valueConsumer ); // what to do with received value changes
                item.setEventConsumer( eventConsumer ); // just log any other event
            }
            else {
                log.error( "monitor failed to be created: node={} because={}", item.getReadValueId().getNodeId(), item.getStatusCode() );
            }
        };

        // now registering those monitors on the server
        CompletableFuture<List<UaMonitoredItem>> monitoredItemListFuture = subscription.createMonitoredItems( TimestampsToReturn.Both, createMonitoredItemsRequests, itemCreationCallback );
        List<UaMonitoredItem> monitoredItemList = monitoredItemListFuture.get();
        if ( monitoredItemList == null ) {
            throw new NullPointerException( "could not create monitored items" );
        }

        log.debug( "monitors created" );
    }

    @SneakyThrows
    private static MonitoredItemCreateRequest getCreateMonitorRequest(
            @NonNull MonitorConfiguration configuration,
            @NonNull UInteger defaultClientHandle ) {
        log.debug( "creating monitor on nodeId={}", configuration.getIdentifier() );
        NodeId nodeId = NodeId.parse( configuration.getIdentifier() );
        UInteger attributeId = configuration.getAttribute().uid();
        ReadValueId readValueId = new ReadValueId( nodeId, attributeId, configuration.getIndexRange(), QualifiedName.NULL_VALUE );

        UInteger clientHandle = UInteger.valueOf( Objects.requireNonNullElseGet( configuration.getClientHandle(), defaultClientHandle::intValue ) );
        UInteger queueSize = UInteger.valueOf( configuration.getQueueSize() );
        MonitoringParameters monitoringParameters = new MonitoringParameters( clientHandle, configuration.getSamplingInterval(), null, queueSize, configuration.isDiscardOldest() );

        return new MonitoredItemCreateRequest( readValueId, configuration.getMonitoringMode(), monitoringParameters );
    }
}
