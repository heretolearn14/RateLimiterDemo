# Solution Architecture & Diagrams

This document details the architectural design of the **Rate Limiter Demo**. It includes visual models to help understand the code structure, runtime behavior, and deployment strategy.

---

## 1. Class Diagram (Static View)

This diagram shows the code structure: how classes are related, what dependencies they inject, and their main responsibilities.

```mermaid
classDiagram
    direction TB
    
    %% Configuration Classes
    class RateLimitConfig {
        -String redisHost
        -int redisPort
        -int rateLimitRequests
        -int rateLimitWindowMinutes
        +proxyManager() LettuceBasedProxyManager
        +getRateLimitRequests() int
        +getRateLimitWindowMinutes() int
    }

    class WebConfig {
        +addInterceptors(registry) void
    }

    %% Logic Classes
    class RateLimitInterceptor {
        -ProxyManager proxyManager
        -RateLimitConfig rateLimitConfig
        +preHandle(request, response, handler) boolean
        -getClientIpAddress(request) String
    }

    class SampleController {
        +hello() String
    }

    %% Interfaces/External Imports
    class WebMvcConfigurer {
        <<Interface>>
    }
    class HandlerInterceptor {
        <<Interface>>
    }

    %% Relationships
    WebConfig ..|> WebMvcConfigurer : Implements
    RateLimitInterceptor ..|> HandlerInterceptor : Implements
    
    WebConfig --> RateLimitInterceptor : Injects & Registers
    RateLimitInterceptor --> RateLimitConfig : Uses Config
    
    %% Annotation Note
    note for SampleController "Protected by\nRateLimitInterceptor"
```

### Explanation
- **WebConfig** allows us to hook into the Spring MVC pipeline.
- It registers the **RateLimitInterceptor**.
- The **Interceptor** relies on **RateLimitConfig** to know the rules (limit/window) and to access the **ProxyManager** (Bucket4j) for checking Redis.

---

## 2. Request Flow (Sequence Diagram)

This diagram illustrates the step-by-step lifecycle of a single HTTP request.

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant WebContainer as Spring Boot (Tomcat)
    participant Interceptor as RateLimitInterceptor
    participant Redis as Redis Service
    participant Controller as SampleController

    Note over Client, WebContainer: Request initiated

    Client->>WebContainer: GET /api/hello
    WebContainer->>Interceptor: preHandle()
    
    Interceptor->>Interceptor: getClientIpAddress()
    
    activate Interceptor
    Interceptor->>Redis: Get Bucket State (Key: IP)
    activate Redis
    Redis-->>Interceptor: Return Tokens (e.g., 5)
    deactivate Redis

    alt Tokens > 0
        Interceptor->>Redis: Decrement Token (-1)
        Redis-->>Interceptor: Success
        Interceptor-->>WebContainer: Return true
        
        WebContainer->>Controller: hello()
        activate Controller
        Controller-->>WebContainer: "Hello!..."
        deactivate Controller
        
        WebContainer-->>Client: 200 OK
        Note right of WebContainer: Headers: X-RateLimit-Remaining
    else Tokens == 0
        Interceptor-->>WebContainer: Return false
        deactivate Interceptor
        WebContainer-->>Client: 429 Too Many Requests
        Note right of WebContainer: Headers: Retry-After
    end
```

---

## 3. Overall Architecture (Component View)

This view shows how the application is constructed from a logical component perspective and how it handles data.

```mermaid
graph TD
    subgraph "Client Layer"
        Browser[Browser / Mobile App]
        Curl[API Client / cURL]
    end

    subgraph "API Gateway / Interface"
        API[Stats API Endpoint]
    end

    subgraph "Application Core (Spring Context)"
        direction TB
        Config[Configuration Component] -->|Defines Rules| Limiter
        Limiter[Rate Limiter Logic]
        Logic[Business Controller]
        
        Limiter -->|Intercepts| Logic
    end

    subgraph "Data Layer"
        Redis[(Redis Cache)]
    end

    Browser --> API
    Curl --> API
    API --> Limiter
    Limiter <-->|Read/Write State| Redis
```

---

## 4. Solution & Deployment Architecture

This diagram shows how the solution would look in a real-world production environment with horizontal scaling.

```mermaid
graph LR
    User1((User 1))
    User2((User 2))
    User3((User 3))

    subgraph "Infrastructure"
        LB[Load Balancer / Nginx]
        
        subgraph "Application Cluster"
            Instance1[App Instance 1]
            Instance2[App Instance 2]
            Instance3[App Instance 3]
        end
        
        Redis[(Redis Server)]
    end

    User1 --> LB
    User2 --> LB
    User3 --> LB

    LB --> Instance1
    LB --> Instance2
    LB --> Instance3

    Instance1 <-->|Sync State| Redis
    Instance2 <-->|Sync State| Redis
    Instance3 <-->|Sync State| Redis

    style Redis fill:#f9f,stroke:#333,stroke-width:2px
    style LB fill:#bbf,stroke:#333,stroke-width:2px
```

### Why this structure?
- **Stateless Application:** The Spring Boot API has no internal state about the user.
- **Centralized State:** Redis acts as the "Single Source of Truth".
- **Result:** If User 1 makes 5 requests to Instance 1, and then 5 requests to Instance 2, Redis knows they have made 10 total. They will be blocked correctly, regardless of which server they hit.

---

## 5. Directory Package Structure

A visual representation of the codebase organization.

```mermaid
graph TD
    Root[com.ankitj.RateLimiterDemo]
    
    subgraph Packages
        Config[config]
        Controller[controller]
        Interceptor[interceptor]
        RootFile[RateLimiterDemoApplication.java]
    end
    
    Root --> RootFile
    Root --> Config
    Root --> Controller
    Root --> Interceptor
    
    Config --> RateLimitConfig.java
    Config --> WebConfig.java
    Controller --> SampleController.java
    Interceptor --> RateLimitInterceptor.java
```
