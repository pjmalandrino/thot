Feature: Gestion des Thotspaces E2E

  Background:
    * url baseUrl

  Scenario: CRUD complet d'un thotspace avec scope de conversations
    # 1. Creer un espace
    Given path '/api/thotspaces'
    And request { name: 'E2E Space Management', description: 'Test E2E', systemPrompt: 'System prompt custom E2E' }
    When method post
    Then status 201
    * def spaceId = response.id

    # 2. Creer une conversation dans cet espace
    Given path '/api/conversations'
    And request { thotspaceId: '#(spaceId)' }
    When method post
    Then status 201
    * def convId = response.id

    # 3. Verifier le filtrage par espace
    Given path '/api/conversations'
    And param thotspaceId = spaceId
    When method get
    Then status 200
    And match response[*].id contains convId

    # 4. Modifier l'espace
    Given path '/api/thotspaces', spaceId
    And request { name: 'E2E Space Modifie', description: 'Description modifiee' }
    When method put
    Then status 200
    And match response.name == 'E2E Space Modifie'

    # 5. Cleanup
    Given path '/api/conversations', convId
    When method delete
    Then status 204

    Given path '/api/thotspaces', spaceId
    When method delete
    Then status 204
