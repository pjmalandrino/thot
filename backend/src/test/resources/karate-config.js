function fn() {
  var config = {
    baseUrl: karate.properties['baseUrl'] || 'http://localhost:8081',
    keycloakUrl: karate.properties['keycloakUrl'] || 'http://localhost:8080',
    testUsername: karate.properties['testUsername'] || 'testuser',
    testPassword: karate.properties['testPassword'] || 'password',
    testClientId: karate.properties['testClientId'] || 'chat-frontend'
  };

  // Obtenir un token JWT via Resource Owner Password Grant
  var tokenUrl = config.keycloakUrl + '/realms/chat-app/protocol/openid-connect/token';
  var result = karate.call('classpath:karate/auth/get-token.feature', {
    tokenUrl: tokenUrl,
    username: config.testUsername,
    password: config.testPassword,
    clientId: config.testClientId
  });
  config.authToken = result.accessToken;

  // Header Authorization par defaut pour tous les appels
  karate.configure('headers', { 'Authorization': 'Bearer ' + config.authToken });

  // Timeouts
  karate.configure('connectTimeout', 10000);
  karate.configure('readTimeout', 30000);

  return config;
}
