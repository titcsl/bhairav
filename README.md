# Bhairav — Quantum Wall

A Spring Boot implementation of a **hybrid post-quantum encryption middleware**. Every session key is derived from an **ML-KEM (Kyber-768)** key encapsulation combined with a classical **ECC P-256 ECDH** exchange, seeded with true quantum randomness from the ANU Quantum Random Number Generator, then used to drive **AES-256-GCM** for the actual data encryption. Requests are protected against replay attacks with a nonce + timestamp window, and every protected endpoint is gated behind an API key.

> Algorithm string reported by the API: `ML-KEM-768 + P-256 ECDH + ANU-QRNG` for key exchange, `AES-256-GCM` for payload encryption.

## Tech Stack

- **Java 25**, Spring Boot 4.0.4 (Web, Validation, Data JPA)
- **Bouncy Castle** (`bcprov-jdk18on`, `bcpkix-jdk18on`) — ML-KEM (Kyber) post-quantum KEM + classical crypto primitives
- **MySQL** — persistence for encrypted records
- **Memcached** (via XMemcached) — nonce tracking for replay protection
- **springdoc-openapi** — Swagger/OpenAPI docs
- **Lombok**

## API Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/handshake` | Performs the hybrid ML-KEM + ECDH key exchange and returns a session ID |
| `POST` | `/encrypt` | Encrypts and stores data, either under a handshake session key or a fresh quantum-derived key |
| `GET` | `/decrypt/{id}` | Decrypts and returns a previously stored record |
| `PUT` | `/update/{id}` | Re-encrypts a record with new data |
| `GET` | `/ping` | Health check |

Protected endpoints (`/encrypt`, `/decrypt`, `/handshake`) require `X-API-Key`, `X-Nonce`, and `X-Timestamp` headers.

## Project Structure — File Index

```
bhairav/
├── pom.xml                                Maven build file — declares Spring Boot 4.0.4 parent,
│                                           dependencies (web, validation, JPA, MySQL, Memcached,
│                                           Bouncy Castle PQC, springdoc, Lombok), Java 25 target.
├── mvnw / mvnw.cmd                        Maven Wrapper launch scripts (Unix / Windows) — lets the
│                                           project build without a locally installed Maven.
├── .gitattributes                         Forces LF line endings for `mvnw`, CRLF for `*.cmd`.
├── .gitignore                             Standard Spring/IDE ignores (build output, .idea, .vscode,
│                                           STS/NetBeans metadata, etc.).
│
├── .mvn/wrapper/
│   └── maven-wrapper.properties           Pins the Maven Wrapper version (3.3.4) and the Maven
│                                           distribution it downloads (3.9.14).
│
└── src/
    ├── main/
    │   ├── resources/
    │   │   └── application.yml            Spring config: server port (8000), MySQL datasource,
    │   │                                   JPA/Hibernate settings, Memcached servers, and the
    │   │                                   Quantum Wall API key + nonce window (30s).
    │   │
    │   └── java/space/whatsgoinon/bhairav/
    │       ├── BhairavApplication.java     Spring Boot entry point (`main` method).
    │       │
    │       ├── config/
    │       │   ├── CorsConfig.java         Global CORS policy — allows all origins/methods for now.
    │       │   ├── MemcachedConfig.java    Builds the `MemcachedClient` bean from `memcached.servers`.
    │       │   └── SwaggerConfig.java      OpenAPI metadata ("Quantum Wall API") and documents the
    │       │                               required `X-API-Key` / `X-Nonce` / `X-Timestamp` headers.
    │       │
    │       ├── controller/
    │       │   ├── HandshakeController.java  `POST /handshake` — runs the hybrid KEM/ECDH exchange.
    │       │   ├── EncryptController.java    `POST /encrypt` — encrypts and persists data.
    │       │   ├── DecryptController.java    `GET /decrypt/{id}` — decrypts a stored record.
    │       │   ├── UpdateController.java     `PUT /update/{id}` — re-encrypts a record with new data.
    │       │   └── PingController.java       `GET /ping` — health check endpoint.
    │       │
    │       ├── dto/
    │       │   ├── HandshakeRequest.java     Client's Kyber + ECC public keys (Base64).
    │       │   ├── HandshakeResponse.java    Session ID, KEM ciphertext, server ECC public key, algo.
    │       │   ├── EncryptRequest.java       Record ID, plaintext data, optional session ID.
    │       │   ├── EncryptResponse.java      Status, timing, algorithm, MITM-check result.
    │       │   ├── DecryptResponse.java      Decrypted plaintext, stored ciphertext, timing.
    │       │   ├── UpdateRequest.java        Record ID, new plaintext, optional session ID.
    │       │   ├── UpdateResponse.java       Status, timing, algorithm, MITM-check result.
    │       │   └── ErrorResponse.java        Standard error shape; flags MITM/replay-attack events.
    │       │
    │       ├── entity/
    │       │   └── Encrypt.java              JPA entity mapped to `encrypted_records`
    │       │                                 (id, ciphertext, encryption key, created_at).
    │       │
    │       ├── repository/
    │       │   └── EncryptedRecordRepository.java  Spring Data JPA repository for `Encrypt`.
    │       │
    │       ├── service/
    │       │   ├── HybridKexService.java     Core key exchange: generates the server's Kyber-768 +
    │       │   │                             ECC P-256 keypairs, performs the hybrid handshake, and
    │       │   │                             derives session keys via HKDF-SHA256.
    │       │   ├── QuantumEntropyService.java  Fetches true random bytes from the ANU Quantum Random
    │       │   │                             Number Generator API for fresh encryption keys.
    │       │   ├── AesCryptoService.java     AES-256-GCM encrypt/decrypt (IV + tag + ciphertext,
    │       │   │                             Base64-encoded).
    │       │   ├── EncryptionService.java    Orchestrates encrypt/decrypt/update — picks a session key
    │       │   │                             (from handshake) or a fresh quantum key, then delegates
    │       │   │                             to AesCryptoService and persists via the repository.
    │       │   └── NonceService.java         Replay-attack protection — validates nonce freshness and
    │       │                                 timestamp window using Memcached as a seen-nonce cache.
    │       │
    │       └── filter/
    │           └── ApiKeyFilter.java         Servlet filter (runs first) — enforces the API key on
    │                                         protected routes and hands off to NonceService.
    │
    └── test/
        └── java/space/whatsgoinon/bhairav/
            └── BhairavApplicationTests.java  Default Spring Boot context-load smoke test.
```

## Running Locally

1. Start MySQL and Memcached locally (defaults: MySQL on `3306`, Memcached on `11211`).
2. Set real values for `spring.datasource.password` and `quantum-wall.api-key` (see **Security Note** below — do not commit real secrets).
3. `./mvnw spring-boot:run`
4. API docs available via springdoc at `/swagger-ui.html` once running.

## Security Note

`application.yml` currently commits a live-looking database password and API key directly into source control. Before this repo is public-facing (e.g. linked from a CV or application), consider:

- Rotating both the MySQL password and the `quantum-wall.api-key` value
- Moving them to environment variables or a `.env`/secrets manager, and referencing them in `application.yml` with `${VAR_NAME}` placeholders
- Adding `application.yml` (or a local override file) to `.gitignore` if it will ever hold real credentials again
- Scrubbing the old secrets from git history if they were ever real (`git log -p -- src/main/resources/application.yml`)
