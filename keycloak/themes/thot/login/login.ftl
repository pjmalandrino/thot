<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>THOT</title>
  <link rel="preconnect" href="https://fonts.googleapis.com"/>
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin="anonymous"/>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&family=IBM+Plex+Mono:wght@400;500;600&display=swap" rel="stylesheet"/>
  <link href="${url.resourcesPath}/css/login.css" rel="stylesheet"/>
</head>
<body>

  <div class="page">

    <div class="brand">
      <div class="brand-mark">
        <img src="${url.resourcesPath}/img/thot-logo.png" alt="THOT" class="brand-logo"/>
      </div>
      <div class="brand-name">
        <span class="brand-title">THOT</span>
        <span class="brand-tagline">Intelligence augmentée</span>
      </div>
    </div>

    <div class="card">

      <p class="card-subtitle">${msg("loginAccountTitle")}</p>

      <#if message?has_content>
        <div class="alert alert-${message.type}">
          ${kcSanitize(message.summary)?no_esc}
        </div>
      </#if>

      <form id="kc-form-login" action="${url.loginAction}" method="post">

        <div class="field">
          <label for="username">
            <#if !realm.loginWithEmailAllowed>
              ${msg("username")}
            <#elseif !realm.registrationEmailAsUsername>
              ${msg("usernameOrEmail")}
            <#else>
              ${msg("email")}
            </#if>
          </label>
          <input
            id="username"
            name="username"
            type="text"
            value="${(login.username!'')}"
            autocomplete="username"
            autofocus
          />
          <#if messagesPerField.existsError('username')>
            <span class="field-error">${kcSanitize(messagesPerField.get('username'))?no_esc}</span>
          </#if>
        </div>

        <div class="field">
          <label for="password">${msg("password")}</label>
          <input
            id="password"
            name="password"
            type="password"
            autocomplete="current-password"
          />
          <#if messagesPerField.existsError('password')>
            <span class="field-error">${kcSanitize(messagesPerField.get('password'))?no_esc}</span>
          </#if>
        </div>

        <input
          type="hidden"
          id="id-hidden-input"
          name="credentialId"
          <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>
        />

        <button id="kc-login" type="submit">
          ${msg("doLogIn")}
        </button>

      </form>

    </div>

  </div>

</body>
</html>
