# ============================================
# STEG 1: BYGG JAR-FILEN
# ============================================

# Basimage: Maven + Java 21 för att bygga projektet
FROM maven:3.9-eclipse-temurin-21 AS build

# Arbetsmapp där alla kommandon körs
WORKDIR /app

# Kopiera pom.xml först (för Docker caching)
COPY pom.xml .

# Ladda ner alla Maven dependencies (cacheas om pom.xml inte ändras)
RUN mvn dependency:go-offline

# Kopiera hela src-mappen
COPY src ./src

# Bygg JAR-filen (hoppar över tester för snabbare build)
RUN mvn clean package -DskipTests


# ============================================
# STEG 2: KÖR JAR-FILEN
# ============================================

# Basimage: Bara Java runtime (mindre image)
FROM eclipse-temurin:21-jre

# Arbetsmapp i produktions-containern
WORKDIR /app

# Kopiera den färdiga JAR-filen från bygg-steget
COPY --from=build /app/target/*.jar app.jar

# Dokumentera att vi använder port 8080 (API Gateway)
EXPOSE 8080

# Kommando som körs när container startar
ENTRYPOINT ["java", "-jar", "app.jar"]
