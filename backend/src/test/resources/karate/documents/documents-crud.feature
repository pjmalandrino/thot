Feature: Documents API

  Background:
    * url baseUrl

  Scenario: Upload, lister et supprimer un document
    # Creer une conversation d'abord
    Given path '/api/conversations'
    And request { thotspaceId: null }
    When method post
    Then status 201
    * def conversationId = response.id

    # Upload un fichier HTML (Docling ne supporte pas .txt)
    Given path '/api/conversations', conversationId, 'documents'
    And multipart file file = { read: 'classpath:karate/documents/test-file.html', filename: 'test-file.html', contentType: 'text/html' }
    When method post
    Then status 201
    And match response.id == '#number'
    And match response.filename == '#string'
    * def documentId = response.id

    # Lister les documents
    Given path '/api/conversations', conversationId, 'documents'
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].id == documentId

    # Supprimer le document
    Given path '/api/conversations', conversationId, 'documents', documentId
    When method delete
    Then status 204

    # Verifier la suppression
    Given path '/api/conversations', conversationId, 'documents'
    When method get
    Then status 200
    And match response == '#[0]'

    # Cleanup conversation
    Given path '/api/conversations', conversationId
    When method delete
    Then status 204

  Scenario: Supprimer un document d'une mauvaise conversation retourne 500
    # Creer conversation A
    Given path '/api/conversations'
    And request { thotspaceId: null }
    When method post
    Then status 201
    * def convA = response.id

    # Upload dans A
    Given path '/api/conversations', convA, 'documents'
    And multipart file file = { read: 'classpath:karate/documents/test-file.html', filename: 'test-file.html', contentType: 'text/html' }
    When method post
    Then status 201
    * def docId = response.id

    # Creer conversation B
    Given path '/api/conversations'
    And request { thotspaceId: null }
    When method post
    Then status 201
    * def convB = response.id

    # Tenter de supprimer le doc de A via B → erreur
    Given path '/api/conversations', convB, 'documents', docId
    When method delete
    Then status 500

    # Cleanup
    Given path '/api/conversations', convA
    When method delete
    Then status 204
    Given path '/api/conversations', convB
    When method delete
    Then status 204

  Scenario: Lister les documents d'une conversation vide retourne un tableau vide
    Given path '/api/conversations'
    And request { thotspaceId: null }
    When method post
    Then status 201
    * def conversationId = response.id

    Given path '/api/conversations', conversationId, 'documents'
    When method get
    Then status 200
    And match response == '#[0]'

    # Cleanup
    Given path '/api/conversations', conversationId
    When method delete
    Then status 204
