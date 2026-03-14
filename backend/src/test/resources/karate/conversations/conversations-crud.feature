Feature: CRUD Conversations API

  Background:
    * url baseUrl

  Scenario: Creer une conversation et verifier la liste
    # Creer
    Given path '/api/conversations'
    And request { thotspaceId: null }
    When method post
    Then status 201
    And match response.id == '#number'
    And match response.title == 'Nouvelle conversation'
    * def conversationId = response.id

    # Lister
    Given path '/api/conversations'
    When method get
    Then status 200
    And match response == '#[_ > 0]'
    And match response[*].id contains conversationId

    # Renommer
    Given path '/api/conversations', conversationId
    And request { title: 'Ma conversation renommee' }
    When method patch
    Then status 200
    And match response.title == 'Ma conversation renommee'

    # Verifier le rename
    Given path '/api/conversations'
    When method get
    Then status 200
    And match response[?(@.id == conversationId)][0].title == 'Ma conversation renommee'

    # Supprimer
    Given path '/api/conversations', conversationId
    When method delete
    Then status 204

    # Verifier la suppression
    Given path '/api/conversations'
    When method get
    Then status 200
    And match response[*].id !contains conversationId

  Scenario: Renommer avec un titre vide retourne Sans titre
    Given path '/api/conversations'
    And request { thotspaceId: null }
    When method post
    Then status 201
    * def conversationId = response.id

    Given path '/api/conversations', conversationId
    And request { title: '   ' }
    When method patch
    Then status 200
    And match response.title == 'Sans titre'

    # Cleanup
    Given path '/api/conversations', conversationId
    When method delete
    Then status 204

  Scenario: Creer une conversation avec un thotspace inexistant retourne 500
    Given path '/api/conversations'
    And request { thotspaceId: 999999 }
    When method post
    Then status 500

  Scenario: Supprimer une conversation inexistante est idempotent
    Given path '/api/conversations', 999999
    When method delete
    Then assert responseStatus == 204 || responseStatus == 404

  Scenario: Recuperer les completions d'une conversation vide
    Given path '/api/conversations'
    And request { thotspaceId: null }
    When method post
    Then status 201
    * def conversationId = response.id

    Given path '/api/conversations', conversationId, 'completions'
    When method get
    Then status 200
    And match response == '#[0]'

    # Cleanup
    Given path '/api/conversations', conversationId
    When method delete
    Then status 204
