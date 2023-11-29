package krik.java;

import krik.java.pager.PageAction;
import krik.java.pager.Pager;
import krik.java.spotify_api.SpotifyAuth;
import krik.java.spotify_api.SpotifyService;
import krik.java.viewer.Viewer;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Application {
    private static final int DEFAULT_PAGE_SIZE = 5;

    private final String accessLink;
    private final String resourceLink;
    private final int pageSize;
    private final Viewer viewer;
    private final Scanner scanner;

    public Application(String accessLink, String resourceLink, Integer pageSize, Viewer viewer, Scanner scanner) {
        this.accessLink = accessLink;
        this.resourceLink = resourceLink;
        this.pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        this.viewer = viewer;
        this.scanner = scanner;
    }

    void run() {
        SpotifyAuth spotifyAuth = new SpotifyAuth(
                accessLink,
                (authLink) -> viewer.showMessage("use this link to request the access code:%n%s", authLink)
        );
        SpotifyService spotifyService = null;

        while (true) {
            var cmd = scanner.next();

            switch (cmd) {
                case "auth" -> {
                    var accessTokenInfo = spotifyAuth.getAccessTokenInfo();
                    spotifyService = new SpotifyService(resourceLink, accessTokenInfo);
                    viewer.showMessage("Success!");
                    continue;
                }
                case "exit" -> {
                    return;
                }
            }

            if (spotifyService == null) {
                viewer.showMessage("Please, provide access for application.");
                continue;
            }

            try {
                switch (cmd) {
                    case "new" -> runPager(
                            spotifyService.getNewReleases(),
                            (album) -> {
                                String artists = Arrays.stream(album.artists())
                                        .map(SpotifyService.Artist::name)
                                        .collect(Collectors.joining(", "));
                                viewer.showMessage("%s%n[%s]%n%s%n", album.name(), artists, album.webUrl());
                            }
                    );
                    case "featured" -> runPager(
                            spotifyService.getFeatured(),
                            (playlist) -> viewer.showMessage("%s%n%s%n", playlist.name(), playlist.webUrl())
                    );
                    case "categories" -> runPager(
                            spotifyService.getTopCategories(),
                            (category) -> viewer.showMessage(category.name())
                    );
                    case "playlists" -> {
                        var categoryName = scanner.nextLine().strip();
                        spotifyService.getCategoryPlaylists(categoryName).ifPresentOrElse(
                                playlists -> runPager(
                                        playlists,
                                        (playlist -> viewer.showMessage("%s%n%s%n", playlist.name(), playlist.webUrl()))
                                ),
                                () -> viewer.showMessage("Unknown category name.")
                        );
                    }
                    default -> viewer.showMessage("Unknown command %s", cmd);
                }
            } catch (SpotifyService.ResponseError e) {
                viewer.showMessage(e.getMessage());
            }
            scanner.nextLine();
        }
    }

    private <E> void runPager(List<E> items, Consumer<E> pageItemConsumer) {
        Pager<E> pager = new Pager<>(items, pageSize, pageItemConsumer);
        pager.run(
                getPageActionSupplier(),
                (curPage, totalPages) -> viewer.showMessage("---PAGE %d OF %d---", curPage, totalPages),
                (curPage, totalPages) -> viewer.showMessage("No more pages.")
        );
    }

    private Supplier<PageAction> getPageActionSupplier() {
        return () -> {
            var cmd = scanner.next();
            while (true) {
                try {
                    return PageAction.valueOf(cmd.toUpperCase());
                } catch (IllegalArgumentException e) {
                    viewer.showMessage("Unknown page command");
                    cmd = scanner.next();
                }
            }
        };
    }
}
