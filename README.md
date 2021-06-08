# prometheus-opcua

A prometheus exporter for [OPC/UA](https://github.com/eclipse/milo).

## configuration

~~~yaml
# /etc/prometheusopcua.conf.yaml

url: opc.tcp://myOpcuaServer:4840

# you will most likely only need one subscription
subscriptions:
    # list all opc ua nodes to monitor here
    -   monitors:
            -   identifier: ns=1;s=AGENT.OBJECTS.myMaschine.input.frequency
                # prometheus labels are optional, yet recommended
                label:
                    maschine: myMaschine
                    io: input
                # optional different prometheus metric name. default = "node"
                name: frequency
~~~

You may provide a different configuration file path as the first program command line argument.

## environment

- Java 11+ required
- logging will be outputted to console

## current limitations

currently, only the messageSecurityMode:None is supported.

## thanks

- eclipse milo
- eclipse jetty
- project lombok
- apache commons
- log4j2
- snakeyaml
