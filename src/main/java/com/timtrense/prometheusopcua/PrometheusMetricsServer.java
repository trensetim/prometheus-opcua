package com.timtrense.prometheusopcua;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The http server for the {@link PrometheusMetricsServlet}
 *
 * @author Tim Trense
 */
@Log4j2
public class PrometheusMetricsServer extends Server {

    public PrometheusMetricsServer( @NonNull Configuration configuration, @NonNull Buffer buffer ) {
        super( configuration.getHttpPort() );

        ServletHolder servletHolder = new ServletHolder();
        servletHolder.setServlet( new PrometheusMetricsServlet( buffer ) );
        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addServletWithMapping( servletHolder, "/metrics" );

        setHandler( servletHandler );
    }

    @Override
    protected void doStart() throws Exception {
        log.info(
                "starting http server on {}",
                Arrays.stream( getConnectors() )
                        .map( c -> (ServerConnector)c )
                        .map( c -> String.join( ",", c.getProtocols() )
                                + " "
                                + Objects.requireNonNullElse( c.getHost(), "*" )
                                + ":" + c.getPort()
                        )
                        .collect( Collectors.joining( ", " ) )
        );
        super.doStart();
    }
}
