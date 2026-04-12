package org.telegram.feed;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedCache {

    private static final class Entry {
        long updatedAtMs;
        @NonNull List<FeedItem> items = new ArrayList<>();
    }

    private static final long MAX_CACHE_AGE_MS = 2 * 60 * 1000L;
    private static final int MAX_CACHE_ITEMS = 100;
    private static final Map<Integer, Entry> cache = new HashMap<>();

    private FeedCache() {
    }

    public static synchronized void put(int account, @NonNull List<FeedItem> items) {
        Entry entry = cache.get(account);
        if (entry == null) {
            entry = new Entry();
            cache.put(account, entry);
        }
        entry.updatedAtMs = System.currentTimeMillis();
        int start = Math.max(0, items.size() - MAX_CACHE_ITEMS);
        entry.items = new ArrayList<>(items.subList(start, items.size()));
    }

    @NonNull
    public static synchronized List<FeedItem> get(int account) {
        Entry entry = cache.get(account);
        if (entry == null) {
            return new ArrayList<>();
        }
        long age = System.currentTimeMillis() - entry.updatedAtMs;
        if (age > MAX_CACHE_AGE_MS) {
            return new ArrayList<>();
        }
        return new ArrayList<>(entry.items);
    }

    public static synchronized void clear(int account) {
        cache.remove(account);
    }
}
