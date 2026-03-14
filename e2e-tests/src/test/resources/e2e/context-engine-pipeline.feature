Feature: Context Engineering Pipeline — Validation de la pertinence

  Le Context Engine enrichit les prompts utilisateur avant de les envoyer au LLM.
  Ces scenarios BDD valident que chaque etape du pipeline apporte une reelle valeur :
  detection de vagueness, reecriture, auto-search, filtrage, et budget.

  Background:
    * url contextEngineUrl
    # Le context-engine est un service interne sans auth
    # Note : les headers par defaut (Bearer token) sont ignores par le context-engine
    * configure headers = { 'Content-Type': 'application/json' }

  # ---------------------------------------------------------------------------
  # SCENARIO 1 : Detection de vagueness
  # VALEUR : Empecher les requetes trop vagues d'etre envoyees au LLM,
  #          ce qui produirait des reponses generiques et inutiles.
  # ---------------------------------------------------------------------------
  Scenario: Un prompt vague est intercepte avec des suggestions de clarification
    Given path '/api/context/analyze'
    And request
      """
      {
        "prompt": "Parle moi de ca",
        "conversationHistory": [],
        "documentContext": null,
        "webSearchRequested": false
      }
      """
    When method post
    Then status 200
    And match response.status == 'clarification_needed'
    And match response.clarificationMessage == '#string'
    And match response.clarificationMessage != ''
    And match response.suggestions == '#[_ > 0]'
    And match response.confidence == '#number'
    # VALEUR : L'utilisateur recoit des pistes concretes au lieu d'une reponse vide

  # ---------------------------------------------------------------------------
  # SCENARIO 2 : Un prompt clair passe sans interruption
  # VALEUR : Le pipeline ne bloque pas inutilement les bonnes requetes.
  # ---------------------------------------------------------------------------
  Scenario: Un prompt clair traverse le pipeline sans interruption
    Given path '/api/context/analyze'
    And request
      """
      {
        "prompt": "Explique le pattern Hexagonal Architecture en Java avec un exemple concret",
        "conversationHistory": [],
        "documentContext": null,
        "webSearchRequested": false
      }
      """
    When method post
    Then status 200
    And match response.status == 'continue'
    # La requete peut etre reecrite (enrichie) ou conservee telle quelle
    And match response.tokenUsage == '#object'
    And match response.tokenUsage.prompt == '#? _ > 0'
    # VALEUR : Les requetes claires sont traitees sans friction

  # ---------------------------------------------------------------------------
  # SCENARIO 3 : Reecriture de requete
  # VALEUR : Transforme un prompt conversationnel/ambigu en requete optimisee
  #          pour le LLM, ameliorant la qualite de la reponse.
  # ---------------------------------------------------------------------------
  Scenario: La reecriture ameliore un prompt conversationnel
    Given path '/api/context/analyze'
    And request
      """
      {
        "prompt": "c quoi spring boot jsp trop",
        "conversationHistory": [],
        "documentContext": null,
        "webSearchRequested": false
      }
      """
    When method post
    Then status 200
    # Si le prompt est trop vague, on accepte clarification OU continue
    And match response.status == '#? _ == "continue" || _ == "clarification_needed"'
    # Si continue, la query reecrite doit etre plus structuree que l'original
    * if (response.status == 'continue') karate.match(response.rewrittenQuery, '#string')

  # ---------------------------------------------------------------------------
  # SCENARIO 4 : Reecriture avec contexte conversationnel
  # VALEUR : La reecriture prend en compte l'historique, evitant les references
  #          ambigues ("il", "ca", "le meme") qui perdent le LLM.
  # ---------------------------------------------------------------------------
  Scenario: La reecriture integre le contexte conversationnel
    Given path '/api/context/analyze'
    And request
      """
      {
        "prompt": "Et pour les performances ?",
        "conversationHistory": [
          { "role": "user", "content": "Explique moi le framework Spring Boot" },
          { "role": "assistant", "content": "Spring Boot est un framework Java qui simplifie la creation d'applications." }
        ],
        "documentContext": null,
        "webSearchRequested": false
      }
      """
    When method post
    Then status 200
    And match response.status == 'continue'
    And match response.rewrittenQuery == '#string'
    # La requete reecrite devrait mentionner Spring Boot (contexte de l'historique)
    # car "les performances" seul serait trop vague
    * def query = response.rewrittenQuery.toLowerCase()
    * match query contains 'spring'
    # VALEUR : Le LLM recoit "performances de Spring Boot" au lieu de
    #          "performances de quoi ?" — reponse bien plus precise

  # ---------------------------------------------------------------------------
  # SCENARIO 5 : Auto Web Search Trigger
  # VALEUR : L'utilisateur n'a plus a deviner quand activer la recherche web.
  #          Le systeme detecte automatiquement les questions d'actualite.
  # ---------------------------------------------------------------------------
  Scenario: Une question d'actualite declenche automatiquement la recherche web
    Given path '/api/context/analyze'
    And request
      """
      {
        "prompt": "Quels sont les derniers resultats de la Ligue des Champions 2025 ?",
        "conversationHistory": [],
        "documentContext": null,
        "webSearchRequested": false
      }
      """
    When method post
    Then status 200
    And match response.status == 'continue'
    # Le LLM a detecte que cette question necessite des donnees fraiches
    And match response.autoWebSearchTriggered == true
    # VALEUR : L'utilisateur pose sa question naturellement,
    #          le systeme sait que ca necessite des donnees fraiches

  # ---------------------------------------------------------------------------
  # SCENARIO 6 : Question stable = pas d'auto-search
  # VALEUR : Le systeme ne gaspille pas de recherches web inutiles pour
  #          des questions stables (maths, concepts, histoire ancienne).
  # ---------------------------------------------------------------------------
  Scenario: Une question de connaissance stable ne declenche pas de recherche web
    Given path '/api/context/analyze'
    And request
      """
      {
        "prompt": "Quelle est la formule de l'aire d'un cercle ?",
        "conversationHistory": [],
        "documentContext": null,
        "webSearchRequested": false
      }
      """
    When method post
    Then status 200
    And match response.status == 'continue'
    And match response.autoWebSearchTriggered == false
    # VALEUR : Economie de latence et de couts API sur les questions stables

  # ---------------------------------------------------------------------------
  # SCENARIO 7 : Pipeline complet avec document et budget
  # VALEUR : Le pipeline orchestre tous les enrichissements ensemble :
  #          reecriture + document + budget = contexte optimal pour le LLM.
  # ---------------------------------------------------------------------------
  Scenario: Le pipeline enrichit un prompt avec document et calcule le budget
    Given path '/api/context/analyze'
    And request
      """
      {
        "prompt": "Resume les points cles du document sur Spring Boot",
        "conversationHistory": [
          { "role": "user", "content": "J'ai uploade un document sur Spring Boot" },
          { "role": "assistant", "content": "J'ai bien recu votre document sur Spring Boot." }
        ],
        "documentContext": "Spring Boot 3.2 introduit le support de virtual threads (Project Loom). La configuration se fait via spring.threads.virtual.enabled=true. Les performances sont ameliorees pour les applications I/O bound. Le RestClient remplace RestTemplate comme client HTTP recommande.",
        "webSearchRequested": false
      }
      """
    When method post
    Then status 200
    And match response.status == 'continue'
    And match response.tokenUsage == '#object'
    # Le budget de tokens doit etre present avec les sections attendues
    And match response.tokenUsage contains { prompt: '#number' }
    And match response.tokenUsage contains { conversationHistory: '#number' }
    And match response.tokenUsage contains { documentContext: '#number' }
    And match response.tokenUsage contains { maxContextTokens: '#number' }
    # Tous les compteurs doivent etre positifs
    And match response.tokenUsage.prompt == '#? _ > 0'
    And match response.tokenUsage.conversationHistory == '#? _ > 0'
    And match response.tokenUsage.documentContext == '#? _ > 0'
    # VALEUR : Le LLM recoit un contexte complet et maitrise :
    #          document + historique, le tout dans un budget de tokens

  # ---------------------------------------------------------------------------
  # SCENARIO 8 : Pipeline avec prompt vide / edge case
  # VALEUR : Le pipeline gere gracieusement les cas limites.
  # ---------------------------------------------------------------------------
  Scenario: Un prompt minimal est traite sans erreur serveur
    Given path '/api/context/analyze'
    And request
      """
      {
        "prompt": "?",
        "conversationHistory": [],
        "documentContext": null,
        "webSearchRequested": false
      }
      """
    When method post
    Then status 200
    # Devrait intercepter ou laisser passer, mais jamais crasher
    And match response.status == '#? _ == "continue" || _ == "clarification_needed"'
    # VALEUR : Robustesse — le systeme ne plante pas sur les edge cases
