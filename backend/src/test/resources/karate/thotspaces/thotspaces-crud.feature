Feature: CRUD Thotspaces API

  Background:
    * url baseUrl

  Scenario: Creer, modifier et supprimer un thotspace
    # Creer
    Given path '/api/thotspaces'
    And request { name: 'Espace Test Karate', description: 'Description test', systemPrompt: 'Tu es un expert en tests.' }
    When method post
    Then status 201
    And match response.id == '#number'
    And match response.name == 'Espace Test Karate'
    And match response.description == 'Description test'
    And match response.systemPrompt == 'Tu es un expert en tests.'
    * def spaceId = response.id

    # Lister et verifier la presence
    Given path '/api/thotspaces'
    When method get
    Then status 200
    And match response[*].id contains spaceId

    # Modifier
    Given path '/api/thotspaces', spaceId
    And request { name: 'Espace Modifie', description: 'Nouvelle description' }
    When method put
    Then status 200
    And match response.name == 'Espace Modifie'
    And match response.description == 'Nouvelle description'
    # Le system prompt ne doit pas changer si non fourni ? Non, le controller l'ecrase si non null
    # Ici on l'a fourni (ce sera null dans la request), donc il ne change pas car c'est un check != null
    And match response.systemPrompt == 'Tu es un expert en tests.'

    # Supprimer
    Given path '/api/thotspaces', spaceId
    When method delete
    Then status 204

    # Verifier la suppression
    Given path '/api/thotspaces'
    When method get
    Then status 200
    And match response[*].id !contains spaceId

  Scenario: Lister les thotspaces retourne au moins le defaut
    Given path '/api/thotspaces'
    When method get
    Then status 200
    And match response == '#[_ > 0]'
