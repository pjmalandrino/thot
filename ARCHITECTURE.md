# Architecture THOT

## 1. Vue globale

```
+---------------------------------------------------+
|                 Frontend (Vue 3)                   |
|                   :3000                            |
|  ChatPage | ThotspacePage | AdminPage              |
+---------------------------------------------------+
        |  REST /api/*            |  OIDC
        |  (Bearer JWT)           |  (login/token)
        v                        v
+----------------+       +-------------------+
|    Backend     |       |     Keycloak      |
| Spring Boot    |<------| :8080             |
| :8081          | JWT   | realm: chat-app   |
|                | valid | client: chat-     |
|                |       |   frontend        |
+----------------+       +-------------------+
   |       |       |
   |       |       +---> +-------------------+
   |       |             | Document Parser   |
   |       |             | FastAPI + Docling  |
   |       |             | :8000             |
   |       |             | POST /parse       |
   |       |             +-------------------+
   |       |
   |       +-----------> +-------------------+
   |  REST sync          | Context Engine    |
   |  POST /api/         | Spring Boot       |
   |  context/analyze    | :8082             |
   |  (fail-open)        |                   |
   |                     | Pipeline:         |
   |                     |  1.Vagueness      |
   |                     |  2.QueryRewriter  |
   |                     |  3.WebSearch      |
   |                     +-------------------+
   |                        |           |
   |                        v           v
   |                   +--------+  +--------+
   |                   | LLM    |  | Tavily |
   |                   | Mistral|  | Search |
   |                   | /Ollama|  | API    |
   |                   +--------+  +--------+
   v
+---------------------------------------------------+
|              PostgreSQL 16 (:5432)                 |
|                                                   |
|  schema: public (chatinterface)                   |
|    conversations, llm_interactions, documents,    |
|    thotspaces, llm_providers, llm_models          |
|                                                   |
|  schema: context_engine                           |
|    context_features                               |
+---------------------------------------------------+
```

---

## 2. Backend

```
com.example.chatinterface
|
|-- security/
|     SecurityConfig               OAuth2 + JWT (Keycloak)
|                                  CORS localhost:3000
|
|-- conversation/
|     ConversationController       GET/POST /api/conversations
|     |                            GET/POST /api/conversations/{id}/completions
|     |                            PATCH (rename) / DELETE
|     ConversationService          Orchestration principale :
|     |                              1. Appel ContextEngineClient
|     |                              2. Si clarification -> retour early
|     |                              3. Build system prompt (base + space + docs + web)
|     |                              4. Appel LLM via LlmGateway
|     |                              5. Sauvegarde LlmInteraction
|     Conversation                 Entity (title, thotspaceId)
|     LlmInteraction               Entity (prompt, response, sources)
|     SourceInfo / Converter       Value object + JSON JPA converter
|     CompletionRequest/Response   DTOs REST
|
|-- contextengine/
|     ContextEngineClient          RestClient -> context-engine:8082
|     |                            POST /api/context/analyze
|     |                            Fail-open (continue si erreur)
|     ContextEngineRequest         DTO (prompt, history, docContext, webSearch)
|     ContextEngineResponse        DTO (status, rewrittenQuery, webSearchResults...)
|
|-- document/
|     DocumentController           POST/GET/DELETE /api/conversations/{id}/documents
|     DocumentService              Upload, parse, build context prompt
|     DocumentGateway (interface)  Port vers parser externe
|     ExternalDocumentGateway      OkHttpClient -> document-parser:8000
|     Document                     Entity (filename, extractedText, pageCount...)
|
|-- thotspace/
|     ThotspaceController          CRUD /api/thotspaces
|     Thotspace                    Entity (name, systemPrompt, isDefault)
|
|-- llm/
|     LlmGateway (interface)       generate(List<ChatMessage>) -> String
|     LlmGatewayFactory            Factory + cache, resout provider+model
|     OllamaGateway                LangChain4j OllamaChatModel
|     MistralGateway               LangChain4j MistralAiChatModel
|     LlmProvider / LlmModel       Entities DB (config dynamique)
|     LlmModelController           GET /api/llm/models
|
|-- admin/
|     LlmAdminController           CRUD /api/admin/providers + models
|     ProviderRequest/Response      DTOs (apiKey masquee en ****)
```

### Flux principal (completion)

```
Frontend
  |
  | POST /api/conversations/{id}/completions
  |   { prompt, webSearch, modelId }
  v
ConversationController
  |
  v
ConversationService.complete()
  |
  |-- 1. ContextEngineClient.analyze(prompt, history, docContext, webSearch)
  |       |
  |       +--> POST context-engine:8082/api/context/analyze
  |       |      (fail-open: si down -> continue sans enrichissement)
  |       |
  |       +<-- { status, rewrittenQuery, webSearchContext, clarificationMessage }
  |
  |-- 2. Si status == "clarification_needed"
  |       -> Retour direct avec le message de clarification
  |
  |-- 3. Build system prompt
  |       base + thotspace.systemPrompt
  |             + documentContext (texte extrait, tronque 8000 chars)
  |             + webSearchContext (contexte des resultats Tavily)
  |
  |-- 4. LlmGatewayFactory.resolve(modelId)
  |       -> OllamaGateway ou MistralGateway
  |       -> generate(systemPrompt + history + userPrompt)
  |
  |-- 5. Sauvegarde LlmInteraction (prompt, response, sources)
  |
  v
CompletionResponse -> Frontend
```

---

## 3. Context Engine (Architecture Hexagonale)

```
                        POST /api/context/analyze
                        GET/PATCH /api/features
                                |
                                v
+---------------------------------------------------------------+
|                    INFRASTRUCTURE (in)                         |
|                                                               |
|  adapter/in/rest/                                             |
|    ContextAnalyzeController -----> AnalyzeContextUseCase      |
|    FeatureFlagController --------> ManageFeaturesUseCase      |
|    dto/                                                       |
|      ContextRequestDto   (REST -> domain)                     |
|      ContextResponseDto  (domain -> REST)                     |
+---------------------------------------------------------------+
                                |
                    implements ports IN
                                |
                                v
+---------------------------------------------------------------+
|                      APPLICATION                              |
|                                                               |
|  ContextAnalysisService  implements AnalyzeContextUseCase     |
|    |  Cree PipelineContext, appelle ContextPipeline.run()      |
|    |                                                          |
|  FeatureFlagService      implements ManageFeaturesUseCase     |
|       list / toggle / updateConfig                            |
+---------------------------------------------------------------+
                                |
                          delegue au
                                |
                                v
+---------------------------------------------------------------+
|                        DOMAIN                                 |
|                   (aucune dependance Spring)                   |
|                                                               |
|  port/in/                                                     |
|    AnalyzeContextUseCase         analyze(prompt,history,...)  |
|    ManageFeaturesUseCase         list / toggle / updateConfig |
|                                                               |
|  port/out/                                                    |
|    LlmPort                       analyze(system, user)        |
|    WebSearchPort                 searchAndExtract(query)      |
|    FeatureFlagPort               isEnabled(name)              |
|                                                               |
|  model/                                                       |
|    ContextAnalysis               Resultat final (status,      |
|    |                               rewrittenQuery, webSearch) |
|    StepResult                    CONTINUE / INTERRUPT         |
|    SearchResult                  (url, title, content)        |
|    ConversationMessage           (role, content)              |
|                                                               |
|  pipeline/                                                    |
|    ContextPipeline               Iterateur de steps :         |
|    |                               check feature flag         |
|    |                               check confiance            |
|    |                               fail-open si basse conf.   |
|    ContextStep (interface)       featureName() + execute()    |
|    PipelineContext               Etat mutable partage :       |
|                                    prompt, history, docCtx,   |
|                                    rewrittenQuery,            |
|                                    webSearchResults           |
|                                                               |
|  step/                                                        |
|    @Order(1) VaguenessDetector   Gate : prompt trop vague ?   |
|    |           feature: "vagueness-detection"                 |
|    |           -> LlmPort.analyze() -> JSON -> INTERRUPT      |
|    |                                                          |
|    @Order(2) QueryRewriter       Enrichissement : reformule   |
|    |           feature: "query-rewriting"                     |
|    |           -> LlmPort.analyze() -> rewrittenQuery         |
|    |                                                          |
|    @Order(3) WebSearchEnricher   Enrichissement : recherche   |
|               feature: "web-search"                           |
|               -> WebSearchPort.searchAndExtract()             |
+---------------------------------------------------------------+
                                |
                    implements ports OUT
                                |
                                v
+---------------------------------------------------------------+
|                    INFRASTRUCTURE (out)                        |
|                                                               |
|  adapter/out/llm/                                             |
|    OllamaLlmAdapter     implements LlmPort (LangChain4j)     |
|    MistralLlmAdapter     implements LlmPort (LangChain4j)     |
|                                                               |
|  adapter/out/websearch/                                       |
|    TavilyWebSearchAdapter  implements WebSearchPort           |
|    |  search (TavilyWebSearchEngine)                          |
|    |  + extract (TavilyExtractClient)                         |
|    |  + dedup + truncate + build context prompt               |
|    TavilyExtractClient     REST -> api.tavily.com/extract     |
|                                                               |
|  adapter/out/persistence/                                     |
|    FeatureFlagPersistenceAdapter  implements FeatureFlagPort   |
|    FeatureFlagJpaRepository       Spring Data                 |
|    FeatureFlagEntity              JPA (context_features)      |
|                                                               |
|  config/                                                      |
|    ContextEngineProperties   @ConfigurationProperties         |
|    LlmConfig                 @Bean LlmPort (ollama/mistral)   |
|    WebSearchConfig           @Bean WebSearchPort (tavily)     |
+---------------------------------------------------------------+
```

### Pipeline : ordre d'execution

```
Prompt utilisateur
       |
       v
+------------------+     feature flag ON ?     +---+
| VaguenessDetector|------- non -------------->| S |
| @Order(1)        |                           | K |
| Gate             |------- oui -------------->| I |
+------------------+                           | P |
       |                                       +---+
       | CONTINUE ou INTERRUPT
       |   (si INTERRUPT + conf >= 0.75 -> arret pipeline)
       |   (si INTERRUPT + conf < 0.75 + CONTINUE policy -> skip)
       v
+------------------+     feature flag ON ?     +---+
| QueryRewriter    |------- non -------------->| S |
| @Order(2)        |                           | K |
| Enrichissement   |------- oui -------------->| I |
+------------------+                           | P |
       |                                       +---+
       | Ecrit rewrittenQuery dans PipelineContext
       v
+------------------+     feature flag ON ?     +---+
| WebSearchEnricher|------- non -------------->| S |
| @Order(3)        |                           | K |
| Enrichissement   |------- oui + requested -->| I |
+------------------+                           | P |
       |                                       +---+
       | Ecrit webSearchResults + webSearchContext
       v
ContextAnalysis
  status: "continue" | "clarification_needed"
  rewrittenQuery, webSearchResults, webSearchContext
```

---

## 4. Frontend (Vue 3)

```
src/
|
|-- app/
|     App.vue                    Layout principal
|     |                            +-- ConversationSidebar (gauche)
|     |                            +-- <RouterView> (centre)
|     main.js                    Init Keycloak -> mount Vue
|     router/index.js            3 routes :
|                                  /           -> ChatPage
|                                  /thotspaces -> ThotspacePage
|                                  /admin      -> AdminPage
|
|-- pages/
|     ChatPage.vue               ChatWindow + DocumentAttachment + ModelSelect
|     ThotspacePage.vue           SpaceManager
|     AdminPage.vue               AdminPanel
|
|-- features/                    Feature-sliced architecture
|     |
|     |-- conversation/
|     |     api.js               fetch/create/rename/delete conversations
|     |     store.js             Pinia : conversations[], selected, load/create/delete
|     |     ui/ConversationSidebar.vue   Liste des conversations + actions
|     |
|     |-- chat/
|     |     api.js               sendCompletion(id, prompt, webSearch, modelId)
|     |                          fetchHistory(id)
|     |     ui/ChatWindow.vue    Zone de chat, messages, input, markdown render
|     |
|     |-- document/
|     |     api.js               upload/list/delete documents
|     |     store.js             Pinia : documents[], upload/load/delete
|     |     ui/DocumentAttachment.vue   Upload fichier + liste attachements
|     |
|     |-- llm-model/
|     |     api.js               fetchModels()
|     |     store.js             Pinia : models[], selectedModelId
|     |     ui/ModelSelect.vue   Dropdown selection modele
|     |
|     |-- thotspace/
|     |     api.js               CRUD thotspaces
|     |     store.js             Pinia : spaces[], selected
|     |     ui/SpaceManager.vue  Interface gestion espaces
|     |
|     |-- admin/
|     |     api.js               CRUD providers + models
|     |     ui/AdminPanel.vue    Config admin LLM
|     |
|     |-- context/
|           api.js               analyzeContext(prompt, ...) -- direct call
|
|-- shared/
      api/http.js               Fetch wrapper avec Bearer token Keycloak
      auth/keycloak.js           Init Keycloak, gestion token, refresh
      utils/date.js              Formatage dates
```

---

## 5. Document Parser (FastAPI)

```
document-parser/
|
|-- main.py                     FastAPI app
|     POST /parse               Upload fichier -> Docling -> markdown
|     GET /health               Healthcheck
|
|-- requirements.txt            fastapi, uvicorn, docling, python-multipart
|-- Dockerfile                  Python image + pip install
```

```
Backend                          Document Parser
   |                                  |
   | POST /parse                      |
   | Content-Type: multipart/form-data|
   | file: (binary)                   |
   +--------------------------------->|
   |                                  | Docling.convert()
   |                                  |   PDF/DOCX -> Markdown
   |<---------------------------------+
   | { filename, content_type,        |
   |   page_count, char_count,        |
   |   extracted_text }               |
```

---

## 6. Infrastructure Docker Compose

```
+----------+    +----------+    +---------+    +--------+    +--------+    +----------+
| postgres |    | keycloak |    | doc-    |    | context|    | backend|    | frontend |
| :5432    |    | :8080    |    | parser  |    | engine |    | :8081  |    | :3000    |
|          |    |          |    | :8000   |    | :8082  |    |        |    |          |
| schemas: |    | realm:   |    | FastAPI |    | Spring |    | Spring |    | Vue 3    |
|  public  |    |  chat-app|    | Docling |    | Boot   |    | Boot   |    | nginx    |
|  context_|    |          |    |         |    |        |    |        |    |          |
|  engine  |    | user:    |    |         |    | LLM    |    | OAuth2 |    |          |
|          |    | testuser |    |         |    | Tavily |    | JPA    |    |          |
+----------+    +----------+    +---------+    +--------+    +--------+    +----------+
     ^               ^                             ^             |  |  |        |
     |               |                             |             |  |  |        |
     +----depends----+                             +--depends----+  |  |        |
     |                                                              |  |        |
     +----------------------depends---------------------------------+  |        |
                                                                       |        |
                           REST sync /api/context/analyze  <-----------+        |
                           REST sync /parse                <-----------+        |
                           REST /api/*                     <--------------------+
```

### Ordre de demarrage

```
1. postgres         (healthcheck: pg_isready)
2. keycloak         (depends: postgres, healthcheck: /health/ready)
   document-parser  (aucune dependance)
3. context-engine   (depends: postgres, healthcheck: /actuator/health)
4. backend          (depends: postgres + keycloak + context-engine)
5. frontend         (depends: backend)
```
