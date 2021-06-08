package com.timtrense.prometheusopcua;

import lombok.Data;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
@Log4j2
public class MonitorConfiguration {

    /**
     * the mode for the opc ua server to acquire samples from the underlying data source. either "Reporting", "Sampling" or "Disabled"
     */
    private @NonNull MonitoringMode monitoringMode = MonitoringMode.Reporting;

    /**
     * whether the opc ua server shall discard the oldest samples if its publishing queue to here is full
     */
    private boolean discardOldest = true;

    /**
     * how many samples the opc ua server shall keep while not being able to deliver them here before running out of buffer space
     */
    private long queueSize = 10;

    /**
     * milliseconds for the opc ua server to sample the original data source
     */
    private double samplingInterval = 100;

    /**
     * the client handle to assign to this monitor, default = auto assigned
     */
    private Integer clientHandle = null;

    /**
     * the opc ua nodes attribute to monitor for changes
     */
    private AttributeId attribute = AttributeId.Value;

    /**
     * the opc ua index range to monitor
     */
    private String indexRange = null;

    /**
     * the opc ua nodes id (mandatory)
     */
    private String identifier;

    /**
     * the core name of the prometheus metric. it will be surrounded like "opcua_" + name + ("_" + unit)?
     */
    private @NonNull String name = "node";

    /**
     * the labels to assign to the target prometheus metric. if omitted the label "id" will automatically be assigned to the value of identifier (see above)
     */
    private @NonNull Map<@NonNull String, @NonNull String> label = new HashMap<>();

    public void setIdentifier( @NonNull String identifier ) {
        this.identifier = identifier;
    }

    /**
     * @return the full prometheus metric name for this monitor including all labels
     */
    public String getMetricName() {
        StringBuilder builder = new StringBuilder();
        builder.append( "opcua_" );
        builder.append( name );

        if ( !label.isEmpty() ) {
            builder.append(
                    label.entrySet().stream()
                            .map( e -> e.getKey() + "=\"" + e.getValue() + "\"" )
                            .collect( Collectors.joining( ",", "{", "}" ) )
            );
        }
        else {
            builder.append( "{id=\"" ).append( identifier ).append( "\"" )
                    .append( ",attribute=\"" ).append( attribute.name() ).append( "\"}" );
        }

        return builder.toString();
    }
}
