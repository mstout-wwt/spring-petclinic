# Key Workflows

## 1. Application Startup

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant SA as SpringApplication.run()
    participant AC as Auto-Configuration
    participant DS as DataSource
    participant SQL as schema.sql / data.sql
    participant Cache as Spring Cache (JCache)
    participant Tomcat as Embedded Tomcat

    Dev->>SA: java -jar / mvnw spring-boot:run
    SA->>AC: trigger auto-configuration
    AC->>DS: configure DataSource (H2 / MySQL / PostgreSQL)
    DS->>SQL: execute schema.sql (create tables)
    DS->>SQL: execute data.sql (seed data)
    AC->>Cache: register "vets" cache via CacheConfiguration
    AC->>Tomcat: start embedded Tomcat on port 8080
    Tomcat-->>Dev: application ready at http://localhost:8080
```

## 2. Owner Search Flow

```mermaid
sequenceDiagram
    participant B as Browser
    participant OC as OwnerController
    participant OR as OwnerRepository
    participant V as View (Thymeleaf)

    B->>OC: GET /owners/find
    OC-->>B: render owners/findOwners form

    B->>OC: GET /owners?lastName=Smith
    OC->>OR: findByLastNameStartingWith("Smith", pageable)
    OR-->>OC: Page<Owner> results

    alt No owners found
        OC-->>B: re-render findOwners form with "not found" error
    else Exactly 1 owner found
        OC-->>B: redirect to /owners/{ownerId}
    else Multiple owners found
        OC->>V: render owners/ownersList (paginated)
        V-->>B: display owner list
    end
```

## 3. Add New Pet

```mermaid
sequenceDiagram
    participant B as Browser
    participant PC as PetController
    participant PTR as PetTypeRepository
    participant OR as OwnerRepository
    participant V as View (Thymeleaf)

    B->>PC: GET /owners/{ownerId}/pets/new
    PC->>OR: findById(ownerId)
    PC->>PTR: findPetTypes()
    PC-->>B: render pets/createOrUpdatePetForm

    B->>PC: POST /owners/{ownerId}/pets/new (name, birthDate, type)
    PC->>PC: validate (PetValidator + Bean Validation)
    alt Validation errors
        PC-->>B: re-render form with errors
    else Valid
        PC->>OR: save(owner with new pet)
        PC-->>B: redirect to /owners/{ownerId}
    end
```

## 4. Book a Visit

```mermaid
sequenceDiagram
    participant B as Browser
    participant VC as VisitController
    participant OR as OwnerRepository
    participant V as View (Thymeleaf)

    B->>VC: GET /owners/{ownerId}/pets/{petId}/visits/new
    VC->>OR: findById(ownerId)
    VC->>VC: loadPetWithVisit — load pet + existing visit history
    VC-->>B: render pets/createOrUpdateVisitForm (with visit history)

    B->>VC: POST /owners/{ownerId}/pets/{petId}/visits/new (date, description)
    VC->>VC: validate (date must be in the future, description not blank)
    alt Validation errors
        VC-->>B: re-render form with errors
    else Valid
        VC->>OR: save(owner with new visit on pet)
        VC-->>B: redirect to /owners/{ownerId}
    end
```

## 5. Vet List with Caching

```mermaid
sequenceDiagram
    participant B as Browser
    participant VetC as VetController
    participant VetR as VetRepository
    participant Cache as Spring Cache ("vets")
    participant DB as Database
    participant V as View (Thymeleaf)

    B->>VetC: GET /vets.html
    VetC->>VetR: findAll(pageable)
    VetR->>Cache: cache lookup for "vets"

    alt Cache miss (first request or cache evicted)
        Cache->>DB: SELECT * FROM vets JOIN vet_specialties ...
        DB-->>Cache: Vet records
        Cache-->>VetR: store result in cache
    else Cache hit
        Cache-->>VetR: return cached Vet collection
    end

    VetR-->>VetC: Page<Vet>
    VetC->>V: render vets/vetList
    V-->>B: paginated vet list HTML

    Note over B,VetC: GET /vets returns JSON (Vets wrapper object, also cached)
```

## 6. Error Handling

```mermaid
sequenceDiagram
    participant B as Browser
    participant CC as CrashController
    participant EH as Spring Error Handling
    participant V as View (Thymeleaf)

    B->>CC: GET /oups
    CC->>CC: throws RuntimeException("Expected: controller used to showcase...")
    CC-->>EH: exception propagates
    EH->>V: resolve error view (error.html)
    V-->>B: render error page
```

## 7. Locale Switching

```mermaid
sequenceDiagram
    participant B as Browser
    participant LCI as LocaleChangeInterceptor
    participant LR as SessionLocaleResolver
    participant MS as MessageSource
    participant V as View (Thymeleaf)

    B->>LCI: any request with ?lang=de
    LCI->>LR: setLocale(request, response, Locale.GERMAN)
    LR->>LR: store locale in HTTP session
    LR-->>LCI: locale updated

    Note over LCI,V: subsequent requests in same session use de locale

    V->>MS: resolve message keys
    MS-->>V: messages from messages_de.properties
    V-->>B: render localized view (German)
```

## 8. CI Pipeline

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant GH as GitHub
    participant GHA as GitHub Actions
    participant JDK as Java 17 (adopt)
    participant MVN as Maven Wrapper (./mvnw)

    Dev->>GH: git push to main (or open PR targeting main)
    GH->>GHA: trigger "Java CI with Maven" workflow
    GHA->>GHA: checkout repository (actions/checkout@v4)
    GHA->>JDK: set up JDK 17 with Maven cache (actions/setup-java@v4)
    GHA->>MVN: ./mvnw -B verify
    MVN->>MVN: compile → test → package → verify
    MVN-->>GHA: build result (success / failure)
    GHA-->>GH: report check status on commit / PR
    GH-->>Dev: notify result
```
