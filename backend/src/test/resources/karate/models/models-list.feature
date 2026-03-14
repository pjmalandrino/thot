Feature: LLM Models API

  Background:
    * url baseUrl

  Scenario: Lister les modeles actifs
    Given path '/api/llm/models'
    When method get
    Then status 200
    And match response == '#array'

  Scenario: Les modeles contiennent les champs attendus
    Given path '/api/llm/models'
    When method get
    Then status 200
    And match each response contains { id: '#number', modelName: '#string', displayName: '#string', providerName: '#string' }
