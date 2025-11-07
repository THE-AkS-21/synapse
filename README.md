# Synapse: A Scalable Real-Time Chat Application

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Java](https://img.shields.io/badge/java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)
![License](https://img.shields.io/badge/license-MIT-blue)

Synapse is a high-performance, horizontally scalable real-time chat backend built to demonstrate modern system design principles. It uses a distributed messaging pattern (Redis Pub/Sub) to ensure that messages are broadcast to all connected clients, even when the application is running on multiple server instances.

This project was built from the ground up with a focus on security, scalability, and testability.

## 🚀 Core Features

-   **JWT Authentication:** Secure REST endpoints for user registration and login.
-   **Secure WebSockets:** WebSocket connections are authenticated using a JWT, passed during the initial STOMP `CONNECT` frame and validated by a custom interceptor.
-   **Horizontal Scalability:** Uses a **Redis Pub/Sub** message broker. When a user sends a message to one server instance, that instance publishes it to a Redis topic. All other server instances subscribe to this topic, receive the message, and forward it to their locally connected clients.
-   **Persistent Message History:** All chat messages are saved to a PostgreSQL database for retrieval.
-   **Modern Java 17:** Uses modern language features like **Java Records** for all DTOs (Data Transfer Objects).
-   **Comprehensive Testing:** Includes unit, web-layer, and full integration tests.

## ⚙️ Tech Stack

| Category | Technology |
| --- | --- |
| **Core** | Java 17, Spring Boot 3.x |
| **Backend** | Spring Web, Spring WebSocket (STOMP), Java 17 Records |
| **Security** | Spring Security 6, JWT (io.jsonwebtoken) |
| **Database** | Spring Data JPA, PostgreSQL, Hibernate |
| **Messaging & Caching** | Spring Data Redis (Reactive), Redis Pub/Sub |
| **Testing** | JUnit 5, Mockito, Spring Boot Test, **Testcontainers** (PostgreSQL), H2 (In-memory) |
| **DevOps** | Docker, Docker Compose, Render (`render.yaml`) |
| **Build** | Maven |

## 📐 Architecture & System Design

The architecture is designed to be horizontally scalable. Running multiple instances of the `synapse` application behind a load balancer will work seamlessly.

### Scalability via Redis Pub/Sub

A simple WebSocket server can only send messages to clients it is *directly* connected to. This fails in a multi-server environment. Synapse solves this using Redis Pub/Sub:

1.  **Client A** (connected to **Server 1**) sends a message.
2.  **Server 1**'s `ChatController` receives the message.
3.  `ChatController` saves the message to PostgreSQL and publishes it to the `synapse-chat` Redis topic using `RedisPublisher`.
4.  **Server 1** *and* **Server 2** (and all other instances) are subscribed to this topic via `RedisSubscriber`.
5.  Both servers receive the message from Redis.
6.  Each server's `RedisSubscriber` uses `SimpMessagingTemplate` to broadcast the message to its *own* set of connected WebSocket clients.
7.  **Client B** (connected to **Server 2**) receives the message.

## Local Development

#### Backend Setup
1. **Install the Dependencies**
```bash
   mvn install
```
2. **Run the Tests**
```bash
   ./mvnw test
```

3. **Run the application**
```bash
   ./mvnw spring-boot:run
```


## 🐳 Docker Commands

### Build and run all services
```bash
         docker-compose up --build
```

### Run in background
```bash
         docker-compose up -d
```

### View logs
```bash
         docker-compose logs -f [service-name]
```

### Stop all services
```bash
         docker-compose down
```

### Stop and remove volumes
```bash
         docker-compose down -v
```

### Rebuild specific service
```bash
         docker-compose up --build [service-name]
```
