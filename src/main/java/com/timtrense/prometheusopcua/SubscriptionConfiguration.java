package com.timtrense.prometheusopcua;

import lombok.Data;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import java.util.LinkedList;
import java.util.List;

@Data
@Log4j2
public class SubscriptionConfiguration {

    /**
     * requested interval for sending new values from the opc ua server to here in milliseconds (0 = as fast as possible)
     */
    private double requestedPublishingInterval = 0;

    /**
     * the list of nodes to monitor via this subscription
     */
    private @NonNull List<@NonNull MonitorConfiguration> monitors = new LinkedList<>();

}
