FROM openjdk:11.0.11-jre
MAINTAINER Tim Trense <trensetim@gmail.com>
COPY build/libs/prometheus-opcua-*.jar /bin/prometheus-opcua.jar
EXPOSE 8080
CMD ["java", "-jar", "/bin/prometheus-opcua.jar"]
