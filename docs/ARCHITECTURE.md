# Hibernate/JPA Architecture

## 🏗️ Tổng quan kiến trúc Hibernate

```mermaid
graph TB
    subgraph "Application Layer"
        APP[Java Application]
    end
    
    subgraph "JPA/Hibernate API Layer"
        EMF[EntityManagerFactory<br/>SessionFactory]
        EM[EntityManager<br/>Session]
        TX[Transaction]
        QUERY[Query/Criteria API]
    end
    
    subgraph "Core ORM Layer"
        PC[Persistence Context<br/>First-Level Cache]
        META[Metadata<br/>Entity Mappings]
        SQL[SQL Generator]
        EVENT[Event System]
    end
    
    subgraph "JDBC Layer"
        CP[Connection Pool]
        JDBC[JDBC Driver]
    end
    
    subgraph "Database"
        DB[(Database)]
    end
    
    APP --> EMF
    APP --> EM
    EM --> TX
    EM --> QUERY
    
    EMF --> META
    EM --> PC
    EM --> SQL
    EM --> EVENT
    
    SQL --> CP
    CP --> JDBC
    JDBC --> DB
```

---

## 🔄 Lifecycle của Session/EntityManager

```mermaid
sequenceDiagram
    participant App as Application
    participant SF as SessionFactory
    participant S as Session
    participant PC as PersistenceContext
    participant DB as Database
    
    App->>SF: buildSessionFactory()
    Note over SF: Load configurations<br/>Parse mappings<br/>Create connection pool
    
    App->>SF: openSession()
    SF->>S: Create new Session
    S->>PC: Initialize PersistenceContext
    
    App->>S: persist(entity)
    S->>PC: Track entity (MANAGED state)
    
    App->>S: beginTransaction()
    
    App->>S: commit()
    S->>PC: flush() - detect changes
    PC->>DB: Execute SQL
    DB-->>PC: Confirm
    S->>PC: Clear tracking
    
    App->>S: close()
    S->>PC: Detach all entities
```

---

## 📊 Entity States (Trạng thái Entity)

```mermaid
stateDiagram-v2
    [*] --> Transient: new Entity()
    
    Transient --> Managed: persist()
    Managed --> Detached: detach() / close()
    Managed --> Removed: remove()
    Removed --> Managed: persist()
    Detached --> Managed: merge()
    
    Managed --> [*]: Database sync on flush
    Removed --> [*]: DELETE on flush
    
    note right of Transient
        Chưa được track
        Không có ID
    end note
    
    note right of Managed
        Được PersistenceContext track
        Changes auto-detected
    end note
    
    note right of Detached
        Có ID nhưng không tracked
        Cần merge() để re-attach
    end note
    
    note right of Removed
        Marked for deletion
        DELETE khi flush
    end note
```

---

## 🧩 Core Components

### 1. Configuration / Metadata

```mermaid
graph LR
    subgraph "Configuration Sources"
        XML[hibernate.cfg.xml]
        PROP[hibernate.properties]
        ANNO[@Entity Annotations]
    end
    
    subgraph "Metadata Model"
        PC[PersistentClass]
        PM[PropertyMapping]
        ID[IdentifierGenerator]
        TYPE[TypeDefinitions]
    end
    
    XML --> META[MetadataBuilder]
    PROP --> META
    ANNO --> META
    
    META --> PC
    META --> PM
    META --> ID
    META --> TYPE
```

### 2. SessionFactory Internal

```mermaid
graph TB
    subgraph "SessionFactory"
        direction TB
        
        subgraph "Immutable Configuration"
            META[Entity Metadata]
            DIALECT[SQL Dialect]
            TYPE[Type Registry]
        end
        
        subgraph "Shared Resources"
            CACHE2[Second-Level Cache]
            POOL[Connection Pool]
            STATS[Statistics]
        end
        
        subgraph "Factory Methods"
            OS[openSession]
            GCS[getCurrentSession]
            WS[withOptions]
        end
    end
    
    META --> OS
    DIALECT --> OS
    POOL --> OS
```

### 3. Session Internal

```mermaid
graph TB
    subgraph "Session"
        direction TB
        
        subgraph "PersistenceContext"
            EC[EntityContext<br/>Map&lt;EntityKey, Entity&gt;]
            SC[SnapshotContext<br/>Original values for dirty check]
            CC[CollectionContext]
        end
        
        subgraph "Action Queue"
            INSERT[InsertActions]
            UPDATE[UpdateActions]
            DELETE[DeleteActions]
        end
        
        subgraph "Services"
            LOADER[EntityLoader]
            PERSISTER[EntityPersister]
            DC[DirtyChecker]
        end
    end
    
    EC --> DC
    SC --> DC
    DC --> UPDATE
    
    PERSISTER --> INSERT
    PERSISTER --> DELETE
```

---

## 🔧 Mini-Hibernate Architecture (Simplified)

```mermaid
graph TB
    subgraph "Your Mini-Hibernate"
        direction TB
        
        subgraph "API Layer"
            MSF[MiniSessionFactory]
            MS[MiniSession]
            MTX[MiniTransaction]
        end
        
        subgraph "Core Layer"
            MPC[MiniPersistenceContext]
            MMP[MetadataParser]
            MSG[SQLGenerator]
        end
        
        subgraph "Mapping Layer"
            ANN["@Entity, @Id, @Column"]
            EM[EntityMetadata]
            FM[FieldMapping]
        end
        
        subgraph "Execution Layer"
            EXEC[JDBCExecutor]
            RM[ResultSetMapper]
        end
    end
    
    MSF --> MS
    MS --> MPC
    MS --> MTX
    
    MMP --> ANN
    MMP --> EM
    EM --> FM
    
    MS --> MSG
    MSG --> EXEC
    EXEC --> RM
```

---

## 📋 Component Responsibilities

| Component | Hibernate | Mini-Hibernate | Responsibility |
|-----------|-----------|----------------|----------------|
| Configuration | `Configuration`, `StandardServiceRegistry` | `MiniConfiguration` | Load settings, mappings |
| SessionFactory | `SessionFactoryImpl` | `MiniSessionFactory` | Heavy-weight, thread-safe, creates Sessions |
| Session | `SessionImpl` | `MiniSession` | Unit of work, manages entities |
| PersistenceContext | `StatefulPersistenceContext` | `MiniPersistenceContext` | First-level cache, entity tracking |
| EntityPersister | `AbstractEntityPersister` | `EntityPersister` | CRUD operations for entity type |
| SQL Generator | `Dialect`, `SQLQueryParser` | `SQLGenerator` | Generate SQL statements |
| Type System | `Type`, `BasicTypeRegistry` | `TypeConverter` | Java ↔ SQL type conversion |

---

## 🔀 Request Flow: persist(entity)

```mermaid
sequenceDiagram
    participant App
    participant Session
    participant PC as PersistenceContext
    participant Persister as EntityPersister
    participant SQLGen as SQLGenerator
    participant JDBC
    
    App->>Session: persist(user)
    Session->>Session: Check entity state
    
    alt Already Managed
        Session-->>App: Return (no-op)
    else Transient
        Session->>PC: addEntity(key, user)
        Session->>PC: takeSnapshot(user)
        Note over PC: Entity now MANAGED
        
        Session->>Session: scheduleInsert(user)
        Note over Session: Queued for flush
    end
    
    App->>Session: flush() or commit()
    
    Session->>Persister: insert(user)
    Persister->>SQLGen: generateInsert(metadata)
    SQLGen-->>Persister: INSERT INTO users...
    
    Persister->>JDBC: executeUpdate(sql, params)
    JDBC-->>Persister: Generated ID
    
    Persister->>PC: updateEntityId(user, id)
```

---

## 🧠 Key Design Patterns Used

| Pattern | Usage in Hibernate | Mini-Hibernate Implementation |
|---------|-------------------|-------------------------------|
| **Factory** | SessionFactory creates Session | `MiniSessionFactory.openSession()` |
| **Unit of Work** | Session tracks changes, flush at end | `MiniSession` + `MiniPersistenceContext` |
| **Identity Map** | One instance per entity per session | `Map<EntityKey, Entity>` in PersistenceContext |
| **Proxy** | Lazy loading entities | Dynamic Proxy hoặc ByteBuddy |
| **Strategy** | Dialect for different databases | `SQLDialect` interface |
| **Observer** | Event listeners (pre/post insert) | `EventListener` interfaces |
| **Template Method** | AbstractEntityPersister | Base persister class |
