#!/bin/bash
./gradlew fatjar
docker build . -t trensetim/prometheus-opcua
