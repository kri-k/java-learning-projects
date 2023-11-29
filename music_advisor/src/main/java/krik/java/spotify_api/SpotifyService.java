package krik.java.spotify_api;

import com.google.gson.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class SpotifyService {
    private static final String DEFAULT_API_URL = "https://api.spotify.com";

    public record Playlist(String id, String name, String webUrl) {
        public static Playlist fromJsonObject(JsonObject jsonObject) {
            return new Playlist(
                    jsonObject.get("id").getAsString(),
                    jsonObject.get("name").getAsString(),
                    jsonObject.getAsJsonObject("external_urls").get("spotify").getAsString()
            );
        }
    }

    public record Album(String id, String name, String webUrl, Artist[] artists) {
        public static Album fromJsonObject(JsonObject jsonObject) {
            var artists = jsonObject.getAsJsonArray("artists")
                    .asList().stream()
                    .map(JsonElement::getAsJsonObject)
                    .map(Artist::fromJsonObject)
                    .toArray(Artist[]::new);

            return new Album(
                    jsonObject.get("id").getAsString(),
                    jsonObject.get("name").getAsString(),
                    jsonObject.getAsJsonObject("external_urls").get("spotify").getAsString(),
                    artists
            );
        }
    }

    public record Artist(String id, String name) {
        public static Artist fromJsonObject(JsonObject jsonObject) {
            return new Artist(
                    jsonObject.get("id").getAsString(),
                    jsonObject.get("name").getAsString()
            );
        }
    }

    public record Category(String id, String name) {
        public static Category fromJsonObject(JsonObject jsonObject) {
            return new Category(
                    jsonObject.get("id").getAsString(),
                    jsonObject.get("name").getAsString()
            );
        }
    }

    public static class ResponseError extends RuntimeException {
        ResponseError(String error) {
            super(error);
        }
    }

    private final URI apiUrl;
    private final SpotifyAuth.AccessTokenInfo accessTokenInfo;
    private final HttpClient client;

    public SpotifyService(String apiUrl, SpotifyAuth.AccessTokenInfo accessTokenInfo) {
        this.apiUrl = URI.create(apiUrl == null ? DEFAULT_API_URL : apiUrl);
        this.accessTokenInfo = accessTokenInfo;
        this.client = HttpClient.newHttpClient();
    }

    public List<Playlist> getFeatured() {
        return getPaginatedItems("/v1/browse/featured-playlists", "playlists")
                .stream()
                .map(Playlist::fromJsonObject)
                .toList();
    }

    public List<Album> getNewReleases() {
        return getPaginatedItems("/v1/browse/new-releases", "albums")
                .stream()
                .map(Album::fromJsonObject)
                .toList();
    }

    public List<Category> getTopCategories() {
        return getPaginatedItems("/v1/browse/categories", "categories")
                .stream()
                .map(Category::fromJsonObject)
                .toList();
    }

    public Optional<List<Playlist>> getCategoryPlaylists(String categoryName) {
        var categories = getTopCategories();
        var targetCategory = categories.stream()
                .filter(category -> category.name().equals(categoryName))
                .findFirst();

        if (targetCategory.isEmpty()) {
            return Optional.empty();
        }

        String path = "/v1/browse/categories/%s/playlists".formatted(targetCategory.orElseThrow().id());
        return Optional.of(getPaginatedItems(path, "playlists")
                .stream()
                .map(Playlist::fromJsonObject)
                .toList());
    }

    private List<JsonObject> getPaginatedItems(String path, String itemsName) {
        return getPaginatedItems(
                path,
                response -> response.getAsJsonObject(itemsName).getAsJsonArray("items"),
                response -> {
                    var nextPageElem = response.getAsJsonObject(itemsName).get("next");
                    return Optional.ofNullable(nextPageElem.isJsonNull() ? null : nextPageElem.getAsString());
                }
        );
    }

    private List<JsonObject> getPaginatedItems(
            String path,
            Function<JsonObject, JsonArray> itemsSelector,
            Function<JsonObject, Optional<String>> nextPageSelector
    )
    {
        List<JsonObject> items = new ArrayList<>();
        JsonObject response;
        while (true) {
            response = sendGetRequest(path);
            items.addAll(itemsSelector.apply(response)
                    .asList().stream()
                    .map(JsonElement::getAsJsonObject)
                    .toList()
            );

            var nextPagePath = nextPageSelector.apply(response);
            if (nextPagePath.isEmpty()) {
                break;
            }
            path = nextPagePath.get();
        }
        return items;
    }

    private JsonObject sendGetRequest(String path) {
        var request = createGetRequest(path);
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            System.out.printf("Error while making request %s%n", request);
            throw new RuntimeException(e);
        }

        boolean isSuccess = response.statusCode() >= 200 && response.statusCode() <= 299;
        boolean isClientError = response.statusCode() >= 400 && response.statusCode() <= 499;

        if (!(isSuccess || isClientError)) {
            // seems like 5xx or unexpected 3xx
            throw new RuntimeException(response.body());
        }

        var jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

        if (isClientError || jsonResponse.has("error")) {
            var errorDescription = jsonResponse.getAsJsonObject("error");
            var errorMessage = errorDescription.get("message").getAsString();
            throw new ResponseError(errorMessage);
        }

        return jsonResponse;
    }

    private HttpRequest createGetRequest(String path) {
        URI uri = URI.create(path);

        if (uri.getHost() == null) {
            try {
                uri = new URI(
                        apiUrl.getScheme(),
                        apiUrl.getUserInfo(),
                        apiUrl.getHost(),
                        apiUrl.getPort(),
                        uri.getPath(),
                        uri.getQuery(),
                        uri.getFragment()
                );
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        return HttpRequest.newBuilder()
                .GET()
                .uri(uri)
                .header("Authorization", "Bearer %s".formatted(accessTokenInfo.accessToken()))
                .build();
    }
}
