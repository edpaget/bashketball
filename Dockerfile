FROM eclipse-temurin:24

RUN mkdir /opt/app
COPY target/app-standalone.jar /opt/app/app-standalone.jar
ENTRYPOINT ["java", "-jar", "/opt/app/app-standalone.jar"]
