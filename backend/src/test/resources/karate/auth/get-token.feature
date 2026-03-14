@ignore
Feature: Obtenir un token JWT Keycloak

  Scenario: Resource Owner Password Grant
    Given url tokenUrl
    And form field grant_type = 'password'
    And form field client_id = clientId
    And form field username = username
    And form field password = password
    When method post
    Then status 200
    * def accessToken = response.access_token
