package com.timtrense.prometheusopcua;

import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * root configuration
 *
 * @author Tim Trense
 */
@Data
@Log4j2
public class Configuration {

    /**
     * HTTP Port for Exposing the Prometheus /metrics endpoint
     */
    private int httpPort = 8080;
    /**
     * How many samples to keep in a circular buffer queue at most before discarding the oldest
     */
    private int bufferSize = 100;

    /**
     * Name of this app for the opc ua server
     */
    private @NonNull String applicationName = "com.timtrense.prometheusopcua";
    /**
     * URI of this app for the opc ua server
     */
    private @NonNull String applicationUri = "urn:com.timtrense.prometheusopcua";

    /**
     * The URL for the opc ua endpoint discovery query
     */
    private String discoveryUrl;
    /**
     * The URL for the opc ua endpoint to connect to
     */
    private String endpointUrl;
    /**
     * Default URL for {@link #endpointUrl} and {@link #discoveryUrl}
     */
    private String url;

    /**
     * opc ua encryption mode: either None, Sign or SignAndEncrypt
     */
    private @NonNull MessageSecurityMode messageSecurityMode = MessageSecurityMode.None;

    /**
     * the subscriptions to open on that opc ua server
     */
    private @NonNull List<@NonNull SubscriptionConfiguration> subscriptions = new LinkedList<>();

    public void setDiscoveryUrl( @NonNull String discoveryUrl ) {
        this.discoveryUrl = discoveryUrl;
    }

    public void setEndpointUrl( @NonNull String endpointUrl ) {
        this.endpointUrl = endpointUrl;
    }

    public void setUrl( @NonNull String url ) {
        this.url = url;
    }

    public String getDiscoveryUrl() {
        return Objects.requireNonNullElse( discoveryUrl, url );
    }

    public String getEndpointUrl() {
        return Objects.requireNonNullElse( endpointUrl, url );
    }

    /**
     * loads the configuration from the given YAML file
     *
     * @param configFile a path of a simple text file in yaml notation
     * @return the loaded configuration
     */
    @SneakyThrows
    public static @NonNull Configuration read( @NonNull String configFile ) {
        log.info( "loading Configuration from {}", configFile );
        Yaml yaml = new Yaml( new Constructor( Configuration.class ) );

        Path configurationFile = Paths.get( configFile );

        try ( FileChannel inChannel = FileChannel.open( configurationFile, StandardOpenOption.READ );
                InputStream fileInputStream = Channels.newInputStream( inChannel ) ) {
            Configuration configuration = yaml.load( fileInputStream );
            if ( configuration == null ) {
                throw new NullPointerException( "could not load the configuration. probably it is malformed." );
            }
            return configuration;
        }
    }
}
