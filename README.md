# Synapse: Real-Time Chat

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Java](https://img.shields.io/badge/java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4+-green)
![License](https://img.shields.io/badge/license-MIT-blue)

Synapse is a high-performance, horizontally scalable real-time chat backend built to demonstrate advanced system design principles. It moves beyond standard CRUD architecture by implementing micro-batching, write-behind caching, L1/L2 caching strategies, and distributed event broadcasting to handle massive concurrent WebSocket traffic without degrading database performance.

## 🚀 Core Features

- **Distributed Real-Time Engine:** Uses **Redis Pub/Sub** to fan-out messages across a multi-node cluster, ensuring users on Server A can instantly message users on Server B.
- **High-Throughput Micro-Batching:** Protects PostgreSQL from `INSERT` storms during traffic spikes by buffering messages in memory (or Redis Streams) and executing bulk flushes via background daemon threads.
- **Two-Tier Presence System:** Manages online/offline status and typing indicators using volatile Redis TTL keys (Fast Path), gracefully syncing `lastSeen` timestamps to PostgreSQL via a Write-Behind cron job (Slow Path).
- **Stateless Security:** Implements stateless JWT authentication at the HTTP Gateway layer, backed by a sub-millisecond Redis Read-Through cache to prevent DB bottlenecking.
- **WebSocket Handshake Auth:** Secures the persistent STOMP protocol via a custom `ChannelInterceptor` that cryptographically validates JWTs during the initial TCP upgrade frame.
- **"Dirty Disconnect" Recovery:** Automatically detects severed TCP connections (e.g., dropped mobile signals) and sweeps active rooms to broadcast offline events, preventing "ghost" users.
- **Resilient API Gateway:** Utilizes Token Bucket algorithms (`Bucket4j` + `Caffeine`) to aggressively rate-limit abusive IPs before they reach the controller layer.

## ⚙️ Tech Stack

| Category | Technology |
| --- | --- |
| **Core** | Java 17, Spring Boot 3.x |
| **Web & Sockets** | Spring Web, Spring WebSocket (STOMP), SockJS |
| **Security** | Spring Security 6, JWT (`io.jsonwebtoken`) |
| **Database** | Spring Data JPA, PostgreSQL, Hibernate, Flyway |
| **Caching & Messaging** | Spring Data Redis, Redis Pub/Sub, Redisson (Streams) |
| **Performance Tools** | Caffeine (L1 Cache), Bucket4j (Rate Limiting) |
| **Testing** | JUnit 5, Mockito, Spring Boot Test, **Testcontainers** |
| **Observability** | Micrometer, Spring Boot Actuator |

## 📐 Architecture & System Design

### 1. Horizontal Scalability via Redis Pub/Sub
A standalone WebSocket server cannot route messages to clients connected to a different physical server. Synapse solves this distributed state problem:
1. **Client A** sends a STOMP message to **Node 1**.
2. **Node 1** persists the message (via buffer) and publishes the payload to an isolated Redis channel (`chat.room.123`).
3. **Node 2**, subscribed to the Redis topic, receives the payload over the internal network.
4. **Node 2** utilizes `SimpMessagingTemplate` to blast the message down to **Client B's** open socket.

### 2. The Micro-Batching Pipeline
Instead of locking the thread to execute a SQL `INSERT` on every chat message:
* Messages are rapidly ingested into a `LinkedBlockingQueue` or Redisson Stream.
* A `MessageBufferService` daemon thread triggers every 5 seconds (or at 100 messages).
* It utilizes Hibernate Proxies (`getReferenceById`) to construct Foreign Keys without firing `SELECT` statements.
* It executes a massive `saveAll()` bulk insert, maintaining database connection pool integrity under extreme load.

### 3. Smart Data Retrieval (N+1 Prevention)
Aggressive use of `@EntityGraph` forces Hibernate to fetch complex relationships (like a Room's participants and creator) in a single optimized `LEFT OUTER JOIN`, eradicating the N+1 query problem during heavy read operations.

## 💻 Local Development

#### Environmental Variables (`.env`)
```text
# PostgreSQL Settings
POSTGRES_DB=synapse_chat
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password

# Spring App Settings
DB_HOST=localhost
DB_PORT=5432
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT Settings
JWT_SECRET=your-new-super-strong-base64-encoded-secret-key-goes-here
JWT_EXPIRATION=86400000

# Buffer Tuning (Optional)
MESSAGE_BUFFER_ENABLED=true
MESSAGE_BUFFER_MAX_BATCH_SIZE=100
MESSAGE_BUFFER_FLUSH_INTERVAL_MS=5000
```
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
