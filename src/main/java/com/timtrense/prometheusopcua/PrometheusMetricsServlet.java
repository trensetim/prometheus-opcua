package com.timtrense.prometheusopcua;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * the HTTP GET /metrics endpoint for exposing prometheus metrics
 *
 * @author Tim Trense
 */
@Log4j2
@Data
@ToString( exclude = {"buffer"} )
@EqualsAndHashCode( callSuper = false, exclude = {"buffer"} )
public class PrometheusMetricsServlet extends HttpServlet {

    private final @NonNull Buffer buffer;

    @Override
    protected void doGet( HttpServletRequest req, HttpServletResponse resp ) throws IOException {

        // rendering prometheus metrics format according to https://github.com/prometheus/docs/blob/master/content/docs/instrumenting/exposition_formats.md [2021-06-08]

        PrintWriter writer = resp.getWriter();
        for ( Map.Entry<String, CircularFifoQueue<DataValue>> e : buffer.getNodeValueBuffers().entrySet() ) {
            String metricName = e.getKey();
            e.getValue().stream()
                    .filter( dv -> dv.getStatusCode() != null
                            && dv.getSourceTime() != null
                            && dv.getStatusCode().isGood() )
                    .forEachOrdered( dv -> {
                        writer.write( metricName );
                        writer.write( " " );
                        writer.write( formatValue( dv.getValue().getValue() ) );
                        writer.write( " " );
                        writer.write( String.valueOf( dv.getSourceTime().getJavaTime() ) );
                        writer.write( "\n" );
                    } );
        }
    }

    private static String formatValue( Object value ) {
        if ( value instanceof Number ) {
            double v = ( (Number)value ).doubleValue();
            if ( Double.isNaN( v ) ) {
                return "NaN";
            }
            if ( Double.isInfinite( v ) ) {
                return ( v > 0 ? "+" : "-" ) + "Inf";
            }
            return String.valueOf( v );
        }
        if ( value instanceof DateTime ) {
            return String.valueOf( ( (DateTime)value ).getJavaTime() );
        }
        if ( value instanceof Boolean ) {
            return ( (Boolean)value ) ? "1" : "0";
        }
        if ( value == null ) {
            return "NaN";
        }
        return "0";
    }
}
