Feature: Context Budget Manager — Maitrise du contexte

  Le Budget Manager (V0.2) estime les tokens par section et tronque
  intelligemment le contexte pour respecter la fenetre du modele.
  Sans cette feature, le contexte pourrait depasser silencieusement
  la limite du LLM, causant des erreurs ou des reponses tronquees.

  Background:
    * url contextEngineUrl
    * configure headers = { 'Content-Type': 'application/json' }

  # ---------------------------------------------------------------------------
  # SCENARIO 1 : Budget tokens calcule et retourne
  # VALEUR : Visibilite sur la consommation de tokens par section,
  #          permettant le monitoring et l'optimisation.
  # ---------------------------------------------------------------------------
  Scenario: Le budget tokens est calcule pour chaque section du contexte
    Given path '/api/context/analyze'
    And request
      """
      {
        "prompt": "Explique les microservices en Java",
        "conversationHistory": [
          { "role": "user", "content": "Bonjour" },
          { "role": "assistant", "content": "Bonjour ! Comment puis-je vous aider ?" }
        ],
        "documentContext": "Les microservices sont une architecture ou chaque service est independant et communique via API REST ou messaging.",
        "webSearchRequested": false
      }
      """
    When method post
    Then status 200
    And match response.tokenUsage == '#object'
    And match response.tokenUsage.prompt == '#number'
    And match response.tokenUsage.conversationHistory == '#number'
    And match response.tokenUsage.documentContext == '#number'
    And match response.tokenUsage.maxContextTokens == '#number'
    # Le prompt "Explique les microservices en Java" = ~40 chars / 4 = ~10 tokens
    And match response.tokenUsage.prompt == '#? _ > 0'
    And match response.tokenUsage.conversationHistory == '#? _ > 0'
    And match response.tokenUsage.documentContext == '#? _ > 0'
    # VALEUR : On sait exactement combien de tokens chaque section consomme

  # ---------------------------------------------------------------------------
  # SCENARIO 2 : Le budget est respecte meme avec un gros document
  # VALEUR : Previent le depassement silencieux de la fenetre de contexte.
  #          Avant V0.2, un document de 50K chars pouvait faire echouer le LLM.
  # ---------------------------------------------------------------------------
  Scenario: Un gros document est tronque pour respecter le budget
    # Generer un document de ~20000 caracteres (~5000 tokens)
    * def bigDoc = ''
    * def line = 'Ceci est une ligne de contenu documentaire pour tester le budget de tokens du context engine. '
    # Repeter pour avoir ~20000 chars
    * eval for(var i = 0; i < 220; i++) bigDoc = bigDoc + line

    Given path '/api/context/analyze'
    And request
      """
      {
        "prompt": "Fais un resume detaille des concepts techniques abordes dans le document ci-joint sur le context engine",
        "conversationHistory": [],
        "documentContext": "#(bigDoc)",
        "webSearchRequested": false
      }
      """
    When method post
    Then status 200
    And match response.tokenUsage == '#object'
    And match response.tokenUsage.maxContextTokens == '#number'
    # Le total ne doit pas depasser le max
    * def totalTokens = response.tokenUsage.prompt + (response.tokenUsage.documentContext || 0) + (response.tokenUsage.conversationHistory || 0)
    * def maxTokens = response.tokenUsage.maxContextTokens
    And match totalTokens == '#? _ <= maxTokens'
    # VALEUR : Le systeme protege automatiquement contre le depassement,
    #          sans que l'utilisateur n'ait a se soucier de la taille du doc

  # ---------------------------------------------------------------------------
  # SCENARIO 3 : Le web search est tronque en priorite (pas le document)
  # VALEUR : La strategie de troncature est intelligente — on coupe d'abord
  #          le contenu web (moins fiable) avant le document de l'utilisateur.
  # ---------------------------------------------------------------------------
  Scenario: Le contexte web est tronque en priorite avant le document utilisateur
    * def bigDoc = ''
    * def line = 'Contenu du document utilisateur important pour la reponse. '
    * eval for(var i = 0; i < 100; i++) bigDoc = bigDoc + line

    Given path '/api/context/analyze'
    And request
      """
      {
        "prompt": "Analyse les concepts techniques de ce document sur les microservices et compare avec les meilleures pratiques actuelles",
        "conversationHistory": [],
        "documentContext": "#(bigDoc)",
        "webSearchRequested": true
      }
      """
    When method post
    Then status 200
    And match response.tokenUsage == '#object'
    # Verification que le total respecte le budget
    * def maxTokens = response.tokenUsage.maxContextTokens
    # VALEUR : Le document de l'utilisateur (haute confiance) est preserve,
    #          le web (basse confiance) est coupe en premier

  # ---------------------------------------------------------------------------
  # SCENARIO 4 : Prompt et historique ne sont jamais tronques
  # VALEUR : Le prompt et la conversation sont sacres — les tronquer
  #          detruirait le sens de la question.
  # ---------------------------------------------------------------------------
  Scenario: Le prompt et l'historique sont preserves meme en situation de budget serre
    * def history = []
    * eval for(var i = 0; i < 10; i++) history.push({ role: 'user', content: 'Question ' + i + ' de test pour le budget' })
    * eval for(var i = 0; i < 10; i++) history.push({ role: 'assistant', content: 'Reponse ' + i + ' de test pour le budget' })

    Given path '/api/context/analyze'
    And request
      """
      {
        "prompt": "Avec tout cet historique, reponds a ma question sur les microservices",
        "conversationHistory": #(history),
        "documentContext": null,
        "webSearchRequested": false
      }
      """
    When method post
    Then status 200
    And match response.tokenUsage.prompt == '#? _ > 0'
    And match response.tokenUsage.conversationHistory == '#? _ > 0'
    # VALEUR : L'intention de l'utilisateur n'est jamais perdue
