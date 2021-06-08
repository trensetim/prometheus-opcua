package com.timtrense.prometheusopcua;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;

import java.util.HashMap;
import java.util.Map;

/**
 * a circular fifo queue buffer for storing opc ua data changes on nodes to their respective prometheus metric names
 *
 * @author Tim Trense
 */
@Data
@ToString( exclude = {"nodeValueBuffers"} )
@EqualsAndHashCode( exclude = {"nodeValueBuffers"} )
public class Buffer {

    /**
     * the size for newly allocated entries in {@link #nodeValueBuffers}.
     * changes do not affect existing entries.
     */
    private int bufferSize;

    /**
     * prometheus metric names (incl. all labels) to temporary circular fifo buffers for new values on that metric
     */
    private final Map<@NonNull String, @NonNull CircularFifoQueue<DataValue>> nodeValueBuffers = new HashMap<>();

    public Buffer( int bufferSize ) {this.bufferSize = bufferSize;}

    /**
     * either gets or creates and registers a circular buffer for the given prometheus metric name and labels
     *
     * @param metric the full string for the prometheus metric with its labels
     * @return a circular buffer queue for temporary storing new value for that metric
     */
    public @NonNull CircularFifoQueue<DataValue> getNodeBuffer( @NonNull String metric ) {
        CircularFifoQueue<DataValue> result;
        synchronized( nodeValueBuffers ) {
            result = nodeValueBuffers.get( metric );
            if ( result == null ) {
                result = new CircularFifoQueue<>( bufferSize );
                nodeValueBuffers.put( metric, result );
            }
        }
        return result;
    }

}
