package com.stubu.specdriven.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A minimal OpenID Connect identity provider (authorization code flow) for tests and local
 * development. It signs real RS256 ID tokens, so the application's regular token validation runs
 * unchanged.
 *
 * <ul>
 *   <li><b>Scripted mode</b> (tests): {@link #signInAs(String)} decides who the next authorization
 *       request signs in as; the authorization endpoint redirects straight back with a code.
 *   <li><b>Interactive mode</b> (local development): the authorization endpoint shows a small form
 *       asking for the email address to sign in with.
 * </ul>
 *
 * Failure scenarios are scripted with {@link #failNextAuthorization(String)} and
 * {@link #failTokenEndpoint(int)}.
 */
public final class TestOidcProvider implements AutoCloseable {

    public static final String CLIENT_ID = "test-client";
    public static final String CLIENT_SECRET = "test-secret";
    public static final int DROP_CONNECTION = -1;

    private record IssuedCode(String clientId, String nonce, String email, boolean emailVerified) {
    }

    private record Scripted(String email, boolean emailVerified) {
    }

    private final HttpServer server;
    private final RSAKey signingKey;
    private final boolean interactive;
    private final String issuer;
    private final Map<String, IssuedCode> codes = new ConcurrentHashMap<>();

    private volatile Scripted nextSignIn;
    private volatile String nextAuthorizationError;
    private volatile int tokenEndpointFailureStatus;

    private TestOidcProvider(int port, boolean interactive) throws IOException, JOSEException {
        this.interactive = interactive;
        this.signingKey = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        this.issuer = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/authorize", this::authorize);
        server.createContext("/token", this::token);
        server.createContext("/jwks", this::jwks);
        server.start();
    }

    /** Starts a provider on a free port in scripted mode. */
    public static TestOidcProvider start() {
        return start(0, false);
    }

    public static TestOidcProvider start(int port, boolean interactive) {
        try {
            return new TestOidcProvider(port, interactive);
        } catch (IOException | JOSEException e) {
            throw new IllegalStateException("Could not start the test OIDC provider", e);
        }
    }

    public String issuer() {
        return issuer;
    }

    /** Properties that register this provider as {@code stubu.iam.providers.<id>}. */
    public Map<String, String> applicationProperties(String id) {
        String prefix = "stubu.iam.providers." + id + ".";
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put(prefix + "display-name", "Test IdP");
        properties.put(prefix + "client-id", CLIENT_ID);
        properties.put(prefix + "client-secret", CLIENT_SECRET);
        properties.put(prefix + "authorization-uri", issuer + "/authorize");
        properties.put(prefix + "token-uri", issuer + "/token");
        properties.put(prefix + "jwk-set-uri", issuer + "/jwks");
        properties.put(prefix + "issuer-uri", issuer);
        return properties;
    }

    /** The next authorization request signs in as this user (verified email). */
    public void signInAs(String email) {
        signInAs(email, true);
    }

    public void signInAs(String email, boolean emailVerified) {
        nextSignIn = new Scripted(email, emailVerified);
    }

    /** The next authorization request comes back with an OAuth2 error (e.g. {@code server_error}). */
    public void failNextAuthorization(String errorCode) {
        nextAuthorizationError = errorCode;
    }

    /**
     * From now on the token endpoint answers every request with this HTTP status, or, with
     * {@link #DROP_CONNECTION}, closes the connection without answering (an unreachable provider).
     */
    public void failTokenEndpoint(int status) {
        tokenEndpointFailureStatus = status;
    }

    /** Forgets all scripted behaviour and issued codes. */
    public void reset() {
        nextSignIn = null;
        nextAuthorizationError = null;
        tokenEndpointFailureStatus = 0;
        codes.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // --- endpoints -------------------------------------------------------------------------------

    private void authorize(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> params = "POST".equals(exchange.getRequestMethod())
                    ? parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    : parseForm(exchange.getRequestURI().getRawQuery());
            String redirectUri = params.get("redirect_uri");
            String state = params.get("state");
            if (redirectUri == null) {
                respond(exchange, 400, "text/plain", "redirect_uri is required");
                return;
            }
            String error = nextAuthorizationError;
            if (error != null) {
                nextAuthorizationError = null;
                redirect(exchange, redirectUri, Map.of("error", error, "state", nullToEmpty(state)));
                return;
            }
            if (interactive && !"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "text/html; charset=utf-8", loginForm(params));
                return;
            }
            Scripted signIn = interactive
                    ? new Scripted(params.getOrDefault("email", ""), true)
                    : nextSignIn;
            if (signIn == null) {
                respond(exchange, 400, "text/plain", "No sign-in scripted for the test OIDC provider");
                return;
            }
            String code = UUID.randomUUID().toString();
            codes.put(code, new IssuedCode(params.get("client_id"), params.get("nonce"), signIn.email(),
                    signIn.emailVerified()));
            redirect(exchange, redirectUri, Map.of("code", code, "state", nullToEmpty(state)));
        } catch (RuntimeException e) {
            respond(exchange, 500, "text/plain", String.valueOf(e));
        }
    }

    private void token(HttpExchange exchange) throws IOException {
        if (tokenEndpointFailureStatus == DROP_CONNECTION) {
            exchange.getRequestBody().readAllBytes();
            exchange.close();
            return;
        }
        if (tokenEndpointFailureStatus != 0) {
            respond(exchange, tokenEndpointFailureStatus, "application/json", "{\"error\":\"server_error\"}");
            return;
        }
        Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8));
        IssuedCode issued = codes.remove(form.get("code"));
        if (issued == null || !"authorization_code".equals(form.get("grant_type"))) {
            respond(exchange, 400, "application/json", "{\"error\":\"invalid_grant\"}");
            return;
        }
        try {
            String idToken = idToken(issued);
            String body = "{\"access_token\":\"" + UUID.randomUUID() + "\",\"token_type\":\"Bearer\","
                    + "\"expires_in\":300,\"scope\":\"openid profile email\",\"id_token\":\"" + idToken + "\"}";
            respond(exchange, 200, "application/json", body);
        } catch (JOSEException e) {
            respond(exchange, 500, "application/json", "{\"error\":\"server_error\"}");
        }
    }

    private void jwks(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "application/json", new JWKSet(signingKey.toPublicJWK()).toString());
    }

    private String idToken(IssuedCode issued) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("sub-" + issued.email())
                .audience(List.of(issued.clientId()))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("email", issued.email())
                .claim("email_verified", issued.emailVerified())
                .claim("name", issued.email());
        if (issued.nonce() != null) {
            claims.claim("nonce", issued.nonce());
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static String loginForm(Map<String, String> params) {
        StringBuilder hidden = new StringBuilder();
        params.forEach((name, value) -> hidden.append("<input type=\"hidden\" name=\"").append(html(name))
                .append("\" value=\"").append(html(value)).append("\">"));
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8"><title>Test identity provider</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>body{font-family:sans-serif;max-width:26rem;margin:4rem auto;padding:0 1rem}
                input[type=email]{width:100%%;padding:.6rem;font-size:1rem;box-sizing:border-box}
                button{margin-top:1rem;padding:.7rem 1.2rem;font-size:1rem}</style></head><body>
                <h1>Test identity provider</h1>
                <p>Local development only. Enter the email address to sign in with, for example
                <code>alice.employee@example.com</code>.</p>
                <form method="post" action="/authorize">%s
                <label for="email">Email address</label>
                <input id="email" name="email" type="email" required autofocus>
                <button type="submit">Sign in</button></form></body></html>
                """.formatted(hidden);
    }

    private static void redirect(HttpExchange exchange, String target, Map<String, String> params)
            throws IOException {
        StringBuilder location = new StringBuilder(target).append(target.contains("?") ? '&' : '?');
        params.forEach((name, value) -> location.append(URLEncoder.encode(name, StandardCharsets.UTF_8)).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&'));
        location.setLength(location.length() - 1);
        exchange.getResponseHeaders().set("Location", URI.create(location.toString()).toString());
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Map<String, String> parseForm(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.put(name, value);
        }
        return params;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
