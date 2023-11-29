package krik.java.spotify_api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SpotifyAuth {
    private static final int REDIRECT_URL_PORT = 8080;
    private static final String OAUTH_REDIRECT_URL = "http://localhost:%d".formatted(REDIRECT_URL_PORT);
    private static final String AUTHORIZATION_CODE_QUERY_PARAM = "code";
    private static final String OAUTH_CLIENT_ID = "test";
    private static final String OAUTH_CLIENT_SECRET = "test";
    private static final String DEFAULT_BASE_OAUTH_URL = "https://accounts.spotify.com";

    private final URI oauthBaseUrl;
    private final Consumer<String> displayAuthLinkF;
    private String authorizationCode = null;

    public record AccessTokenInfo(String accessToken, String tokenType, int expiresIn) {}

    public SpotifyAuth(String oauthBaseUrl, Consumer<String> displayAuthLinkF) {
        this.oauthBaseUrl = URI.create(oauthBaseUrl == null ? DEFAULT_BASE_OAUTH_URL : oauthBaseUrl);
        this.displayAuthLinkF = displayAuthLinkF;
    }

    public AccessTokenInfo getAccessTokenInfo() {
        waitForAuthorizationCode();
        var accessTokenInfo = requestAccessTokenInfo();
        System.out.printf("response:%n%s%n", accessTokenInfo);
        return accessTokenInfo;
    }

    void waitForAuthorizationCode() {
        HttpServer authorizationCodeListener = createAuthorizationCodeListener();
        authorizationCodeListener.start();

        displayAuthLinkF.accept(getAuthLink());
        System.out.println("waiting for code...");

        var authCodeWaiter = new Thread(() -> {
            synchronized (this) {
                while (authorizationCode == null) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        authorizationCodeListener.stop(1);
                        throw new RuntimeException(e);
                    }
                }
                authorizationCodeListener.stop(1);
            }
        });

        authCodeWaiter.start();
        try {
            authCodeWaiter.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("code received");
    }

    private HttpServer createAuthorizationCodeListener() {
        HttpServer listener;
        try {
            listener = HttpServer.create(new InetSocketAddress(REDIRECT_URL_PORT), 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        listener.createContext("/", this::handler);
        return listener;
    }

    private void handler(HttpExchange exchange) {
        BiConsumer<Integer, String> sendResponse = (code, response) -> {
            try {
                exchange.sendResponseHeaders(code, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        if (authorizationCode != null) {
            sendResponse.accept(200, "Has received the code already!");
            return;
        }

        var query = exchange.getRequestURI().getQuery();
        query = query == null ? "" : query;

        String code = null;
        String paramsSplitRegexp = "[&?]";  // split by `?` here in addition to `&`
                                            // because of the bug in the testing system
        for (var param : query.split(paramsSplitRegexp)) {
            var paramKeyValue = param.split("=", 2);
            if (paramKeyValue.length != 2) {
                continue;
            }
            if (Objects.equals(paramKeyValue[0], AUTHORIZATION_CODE_QUERY_PARAM)) {
                code = paramKeyValue[1];
                break;
            }
        }

        boolean codeWasFound = code != null;
        sendResponse.accept(
                codeWasFound ? 200 : 400,
                codeWasFound ?
                        "Got the code. Return back to your program." :
                        "Authorization code not found. Try again."
        );
        if (codeWasFound) {
            synchronized (this) {
                authorizationCode = code;
                this.notify();
            }
        }
    }

    private AccessTokenInfo requestAccessTokenInfo() {
        if (authorizationCode == null) {
            throw new RuntimeException("You need to get an authorization code first");
        }

        String auth = Base64.getEncoder()
                .encodeToString("%s:%s".formatted(OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET).getBytes());
        String requestBody = String.join("&",
                "code=" + URLEncoder.encode(authorizationCode, StandardCharsets.UTF_8),
                "redirect_uri=" + URLEncoder.encode(OAUTH_REDIRECT_URL, StandardCharsets.UTF_8),
                "grant_type=authorization_code"
        );
        HttpRequest accessTokenRequest = HttpRequest.newBuilder()
                .uri(URI.create("%s/api/token".formatted(oauthBaseUrl)))
                .header("content-type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic %s".formatted(auth))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response;

        System.out.println("making http request for access_token...");
        try {
            response = client.send(accessTokenRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException(response.body());
        }

        var gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
        return gson.fromJson(response.body(), AccessTokenInfo.class);
    }

    private String getAuthLink() {
        return "%s/authorize?client_id=%s&redirect_uri=%s&response_type=code"
                .formatted(this.oauthBaseUrl, OAUTH_CLIENT_ID, OAUTH_REDIRECT_URL);
    }
}
