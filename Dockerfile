FROM openjdk:8-alpine

COPY target/uberjar/mdr2.jar /mdr2/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/mdr2/app.jar"]
