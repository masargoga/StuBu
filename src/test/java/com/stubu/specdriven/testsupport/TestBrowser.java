package com.stubu.specdriven.testsupport;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A minimal HTTP "browser" for full-stack tests: keeps cookies, never follows redirects on its own
 * (so tests can assert on every hop) and understands relative and absolute {@code Location} headers.
 */
public final class TestBrowser {

    private final URI baseUri;
    private final HttpClient client;

    public TestBrowser(int port) {
        this.baseUri = URI.create("http://localhost:" + port);
        this.client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public URI baseUri() {
        return baseUri;
    }

    /** GET a path of the application, or an absolute URL. */
    public HttpResponse<String> get(String pathOrUrl) {
        return send(HttpRequest.newBuilder(resolve(pathOrUrl)).GET().build());
    }

    /** GET the same URL another way: with an explicit {@code Cookie} header instead of the cookie jar. */
    public HttpResponse<String> getWithCookie(String path, String cookie) {
        HttpClient plain = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        try {
            return plain.send(HttpRequest.newBuilder(resolve(path)).header("Cookie", cookie).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw failure(e);
        }
    }

    public HttpResponse<String> postForm(String path, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(resolve(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    /** The {@code Location} header of a redirect, resolved against the application URL. */
    public URI location(HttpResponse<?> response) {
        return resolve(response.headers().firstValue("Location")
                .orElseThrow(() -> new AssertionError("No Location header in response " + response.statusCode()
                        + " from " + response.uri())));
    }

    /** Starts the login with a provider: the application's redirect to the identity provider. */
    public HttpResponse<String> startLogin(String providerId) {
        return get("/oauth2/authorization/" + providerId);
    }

    /**
     * Performs the complete browser side of a login with the given provider: the redirect to the
     * identity provider, the provider's authentication, and the callback to the application. Returns the
     * application's answer to the callback, normally a redirect to the home page or the login page.
     */
    public HttpResponse<String> signIn(String providerId) {
        HttpResponse<String> toProvider = startLogin(providerId);
        HttpResponse<String> fromProvider = get(location(toProvider).toString());
        return get(location(fromProvider).toString());
    }

    private URI resolve(String pathOrUrl) {
        return baseUri.resolve(pathOrUrl);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw failure(e);
        }
    }

    private static IllegalStateException failure(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        return new IllegalStateException("HTTP request failed", e);
    }

    /** All {@code Set-Cookie} header values of a response. */
    public static List<String> setCookies(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie");
    }
}
