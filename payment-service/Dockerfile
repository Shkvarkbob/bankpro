# --- STAGE 1: Build ---
FROM maven:3.9.9-eclipse-temurin-24 AS build
WORKDIR /app

# Копируем весь проект
COPY . .

# Собираем только нужный модуль
RUN mvn -pl payment-service -am clean package -DskipTests

# --- STAGE 2: Runtime ---
FROM eclipse-temurin:24-jdk
WORKDIR /app
COPY --from=build /app/payment-service/target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]