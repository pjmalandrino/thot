function fn() {
  // URLs et credentials configurables via -D
  // Usage : mvn verify -DbaseUrl=... -DkeycloakUrl=... -DtestUsername=... -DtestPassword=...
  // Defaut = env local standard (docker compose up depuis la racine)
  var config = {
    baseUrl: karate.properties['baseUrl'] || 'http://localhost:8081',
    keycloakUrl: karate.properties['keycloakUrl'] || 'http://localhost:8080',
    testUsername: karate.properties['testUsername'] || 'testuser',
    testPassword: karate.properties['testPassword'] || 'password',
    testClientId: karate.properties['testClientId'] || 'chat-frontend'
  };

  // Obtenir un token JWT via Resource Owner Password Grant
  var tokenUrl = config.keycloakUrl + '/realms/chat-app/protocol/openid-connect/token';
  var result = karate.call('classpath:e2e/auth/login.feature', {
    tokenUrl: tokenUrl,
    username: config.testUsername,
    password: config.testPassword,
    clientId: config.testClientId
  });
  config.authToken = result.accessToken;

  // Header Authorization par defaut pour tous les appels
  karate.configure('headers', { 'Authorization': 'Bearer ' + config.authToken });

  // Timeouts genereux (LLM peut etre lent)
  karate.configure('connectTimeout', 15000);
  karate.configure('readTimeout', 120000);

  return config;
}
