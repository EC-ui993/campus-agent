# 阶段一：Maven 构建
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先只拷贝 pom.xml，利用层缓存预拉依赖
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 再拷贝源码并打包（跳过测试）
COPY src ./src
RUN mvn clean package -DskipTests

# 阶段二：精简运行镜像
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
