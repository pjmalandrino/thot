Feature: Parcours utilisateur complet — Chat E2E

  Background:
    * url baseUrl

  Scenario: Creer un espace, une conversation, envoyer un message et nettoyer
    # 1. Creer un thotspace avec un system prompt custom
    Given path '/api/thotspaces'
    And request { name: 'E2E Test Space', description: 'Espace pour tests E2E', systemPrompt: 'Tu es un assistant de test. Reponds toujours par OK.' }
    When method post
    Then status 201
    And match response.id == '#number'
    * def spaceId = response.id

    # 2. Creer une conversation dans cet espace
    Given path '/api/conversations'
    And request { thotspaceId: '#(spaceId)' }
    When method post
    Then status 201
    And match response.title == 'Nouvelle conversation'
    * def conversationId = response.id

    # 3. Envoyer un message (completion LLM)
    Given path '/api/conversations', conversationId, 'completions'
    And request { prompt: 'Bonjour, ceci est un test E2E.', webSearch: false }
    When method post
    Then status 201
    And match response.id == '#number'
    And match response.prompt == 'Bonjour, ceci est un test E2E.'
    And match response.response == '#string'
    And match response.response != ''

    # 4. Verifier l'auto-titrage de la conversation
    Given path '/api/conversations'
    When method get
    Then status 200
    * def conv = karate.jsonPath(response, "$[?(@.id==" + conversationId + ")]")[0]
    And match conv.title != 'Nouvelle conversation'

    # 5. Verifier l'historique des completions
    Given path '/api/conversations', conversationId, 'completions'
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].prompt == 'Bonjour, ceci est un test E2E.'

    # 6. Renommer la conversation
    Given path '/api/conversations', conversationId
    And request { title: 'Conversation E2E Renommee' }
    When method patch
    Then status 200
    And match response.title == 'Conversation E2E Renommee'

    # 7. Cleanup : supprimer la conversation puis l'espace
    Given path '/api/conversations', conversationId
    When method delete
    Then status 204

    Given path '/api/thotspaces', spaceId
    When method delete
    Then status 204
