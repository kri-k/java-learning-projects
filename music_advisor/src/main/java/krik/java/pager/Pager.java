package krik.java.pager;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Pager<E> {
    private final List<E> items;
    private final int pageSize;
    private final Consumer<E> pageItemConsumer;
    private int curPage = 0;

    public Pager(
            List<E> items,
            int pageSize,
            Consumer<E> pageItemConsumer
    )
    {
        this.items = items;
        this.pageSize = pageSize;
        this.pageItemConsumer = pageItemConsumer;
    }

    public void run(
            Supplier<PageAction> commandSupplier,
            BiConsumer<Integer, Integer> onPageChange,
            BiConsumer<Integer, Integer> onWrongPage
    )
    {
        displayPage();
        onPageChange.accept(curPage + 1, getTotalPages());

        PageAction action = commandSupplier.get();
        while (action != PageAction.EXIT) {
            boolean isPageTurnedOver = switch (action) {
                case NEXT -> this.next();
                case PREV -> this.prev();
                default -> false;
            };

            if (isPageTurnedOver) {
                displayPage();
                onPageChange.accept(curPage + 1, getTotalPages());
            } else {
                onWrongPage.accept(curPage + 1, getTotalPages());
            }

            action = commandSupplier.get();
        }
    }

    private void displayPage() {
        int curPageFirstItem = curPage * pageSize;
        int nextPageFirstItem = Math.min(items.size(), curPageFirstItem + pageSize);
        for (int i = curPage * pageSize; i < nextPageFirstItem; i++) {
            pageItemConsumer.accept(items.get(i));
        }
    }

    private boolean next() {
        int prevPage = curPage;
        curPage = Math.min(curPage + 1, getTotalPages() - 1);
        return curPage != prevPage;
    }

    private boolean prev() {
        int nextPage = curPage;
        curPage = Math.max(curPage - 1, 0);
        return curPage != nextPage;
    }

    private int getTotalPages() {
        return items.size() / pageSize + (items.size() % pageSize == 0 ? 0 : 1);
    }
}
