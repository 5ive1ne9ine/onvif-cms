# 多阶段构建: 前端 build + 后端打包 + 运行
FROM node:20-alpine AS frontend-build
WORKDIR /app
COPY frontend/package.json frontend/
RUN cd frontend && npm install
COPY frontend/ frontend/
COPY backend/src/main/resources/static backend/src/main/resources/static
RUN cd frontend && npm run build

FROM maven:3.9-eclipse-temurin-8 AS backend-build
WORKDIR /app
COPY pom.xml .
COPY backend/pom.xml backend/
RUN mvn -f backend/pom.xml dependency:go-offline -B || true
COPY backend/ backend/
COPY --from=frontend-build /app/backend/src/main/resources/static backend/src/main/resources/static
RUN mvn -f backend/pom.xml clean package -DskipTests

FROM eclipse-temurin:8-jre
WORKDIR /app

# 安装 ffmpeg (事件录制依赖)
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY --from=backend-build /app/backend/target/onvif-cms.jar app.jar

ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx1g", "-jar", "/app/app.jar"]
