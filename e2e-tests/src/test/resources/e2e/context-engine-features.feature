Feature: Feature Flags — Controle dynamique du pipeline

  Les feature flags permettent d'activer/desactiver chaque etape du pipeline
  sans redeploiement. Ceci valide que le systeme est modulaire et que chaque
  feature apporte bien une valeur mesurable (on/off comparison).

  Background:
    * url contextEngineUrl
    * configure headers = { 'Content-Type': 'application/json' }

  # ---------------------------------------------------------------------------
  # SCENARIO 1 : Lister les features disponibles
  # VALEUR : Visibilite sur les capacites du pipeline, administrable en runtime.
  # ---------------------------------------------------------------------------
  Scenario: Toutes les features du pipeline sont listees avec leur etat
    Given path '/api/features'
    When method get
    Then status 200
    # V0.1 features
    And match response[*].featureName contains 'vagueness-detection'
    And match response[*].featureName contains 'query-rewriting'
    And match response[*].featureName contains 'web-search'
    # V0.2 features
    And match response[*].featureName contains 'auto-web-search'
    And match response[*].featureName contains 'web-search-relevance'
    And match response[*].featureName contains 'context-budget'
    # Chaque feature a les champs attendus
    And match each response contains { id: '#number', featureName: '#string', enabled: '#boolean' }
    # VALEUR : 6 features administrables independamment

  # ---------------------------------------------------------------------------
  # SCENARIO 2 : Desactiver le vagueness detector change le comportement
  # VALEUR : Prouve que le VaguenessDetector apporte une vraie protection.
  #          Sans lui, un prompt vague passe sans avertissement.
  # ---------------------------------------------------------------------------
  Scenario: Desactiver le vagueness detector laisse passer les prompts vagues
    # D'abord verifier que le vagueness detector bloque un prompt vague
    Given path '/api/context/analyze'
    And request { "prompt": "aide moi", "conversationHistory": [], "documentContext": null, "webSearchRequested": false }
    When method post
    Then status 200
    * def statusAvec = response.status
    # Devrait etre clarification_needed (intercepte)

    # Desactiver le vagueness detector
    Given path '/api/features/vagueness-detection/toggle'
    And param enabled = false
    When method patch
    Then status 200
    And match response.enabled == false

    # Le meme prompt vague devrait passer maintenant
    Given path '/api/context/analyze'
    And request { "prompt": "aide moi", "conversationHistory": [], "documentContext": null, "webSearchRequested": false }
    When method post
    Then status 200
    * def statusSans = response.status

    # Reactiver le vagueness detector (cleanup)
    Given path '/api/features/vagueness-detection/toggle'
    And param enabled = true
    When method patch
    Then status 200

    # Comparer : avec le detector, le status devait etre clarification_needed
    And match statusAvec == 'clarification_needed'
    # Sans le detector, le prompt passe
    And match statusSans == 'continue'
    # VALEUR : Le vagueness detector est un vrai garde-fou, pas un placebo

  # ---------------------------------------------------------------------------
  # SCENARIO 3 : Desactiver le query rewriter
  # VALEUR : Prouve que la reecriture transforme reellement le prompt.
  # ---------------------------------------------------------------------------
  Scenario: Desactiver le query rewriter retourne le prompt original
    # Avec le rewriter actif
    Given path '/api/context/analyze'
    And request { "prompt": "comment faire du java", "conversationHistory": [], "documentContext": null, "webSearchRequested": false }
    When method post
    Then status 200
    * def queryAvec = response.rewrittenQuery

    # Desactiver le query rewriter
    Given path '/api/features/query-rewriting/toggle'
    And param enabled = false
    When method patch
    Then status 200

    # Sans le rewriter
    Given path '/api/context/analyze'
    And request { "prompt": "comment faire du java", "conversationHistory": [], "documentContext": null, "webSearchRequested": false }
    When method post
    Then status 200
    * def querySans = response.rewrittenQuery

    # Reactiver (cleanup)
    Given path '/api/features/query-rewriting/toggle'
    And param enabled = true
    When method patch
    Then status 200

    # La query reecrite devrait etre differente de l'originale
    # (le rewriter enrichit/clarifie le prompt)
    And match queryAvec != querySans
    # VALEUR : Le rewriter transforme reellement le prompt,
    #          la difference est mesurable

  # ---------------------------------------------------------------------------
  # SCENARIO 4 : Desactiver le context budget
  # VALEUR : Prouve que le budget manager produit des donnees de suivi.
  # ---------------------------------------------------------------------------
  Scenario: Desactiver le budget manager supprime le suivi des tokens
    # Avec le budget manager actif
    Given path '/api/context/analyze'
    And request { "prompt": "Explique Docker", "conversationHistory": [], "documentContext": "Docker est un outil de containerisation.", "webSearchRequested": false }
    When method post
    Then status 200
    * def tokenUsageAvec = response.tokenUsage

    # Desactiver le budget manager
    Given path '/api/features/context-budget/toggle'
    And param enabled = false
    When method patch
    Then status 200

    # Sans le budget manager
    Given path '/api/context/analyze'
    And request { "prompt": "Explique Docker", "conversationHistory": [], "documentContext": "Docker est un outil de containerisation.", "webSearchRequested": false }
    When method post
    Then status 200
    * def tokenUsageSans = response.tokenUsage

    # Reactiver (cleanup)
    Given path '/api/features/context-budget/toggle'
    And param enabled = true
    When method patch
    Then status 200

    # Avec le manager, on a des donnees de tokens
    And match tokenUsageAvec != null
    And match tokenUsageAvec.prompt == '#number'
    # Sans le manager, pas de donnees de budget (objet vide ou sans prompt)
    And match tokenUsageSans.prompt == '#notpresent'
    # VALEUR : Le budget manager fournit une observabilite reelle

  # ---------------------------------------------------------------------------
  # SCENARIO 5 : Toggle on/off d'une feature est idempotent
  # VALEUR : La gestion des flags est robuste et fiable.
  # ---------------------------------------------------------------------------
  Scenario: Activer une feature deja active ne cause pas d'erreur
    Given path '/api/features/query-rewriting/toggle'
    And param enabled = true
    When method patch
    Then status 200
    And match response.enabled == true

    # Re-activer
    Given path '/api/features/query-rewriting/toggle'
    And param enabled = true
    When method patch
    Then status 200
    And match response.enabled == true
    # VALEUR : Pas de side-effects ou d'erreurs sur double-toggle
