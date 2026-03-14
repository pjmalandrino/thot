Feature: Parcours upload de document E2E

  Background:
    * url baseUrl

  Scenario: Upload un document, poser une question, supprimer
    # 1. Creer une conversation
    Given path '/api/conversations'
    And request { thotspaceId: null }
    When method post
    Then status 201
    * def conversationId = response.id

    # 2. Upload un document texte
    Given path '/api/conversations', conversationId, 'documents'
    And multipart file file = { read: 'classpath:e2e/test-data/sample.html', filename: 'sample.html', contentType: 'text/html' }
    When method post
    Then status 201
    And match response.id == '#number'
    And match response.filename == '#string'
    * def documentId = response.id

    # 3. Verifier que le document est liste
    Given path '/api/conversations', conversationId, 'documents'
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].id == documentId

    # 4. Poser une question (le contexte du document devrait etre injecte)
    Given path '/api/conversations', conversationId, 'completions'
    And request { prompt: 'Que contient le document attache ?', webSearch: false }
    When method post
    Then status 201
    And match response.response == '#string'
    And match response.response != ''

    # 5. Supprimer le document
    Given path '/api/conversations', conversationId, 'documents', documentId
    When method delete
    Then status 204

    # 6. Cleanup
    Given path '/api/conversations', conversationId
    When method delete
    Then status 204
