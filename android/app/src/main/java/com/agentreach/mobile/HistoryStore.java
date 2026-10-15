package com.agentreach.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Keeps recent research on-device. No account, analytics, or cloud database. */
public final class HistoryStore {
    private static final String PREFS = "agent_reach_library";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ITEMS = 60;
    private final SharedPreferences preferences;

    public HistoryStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<ResearchEntry> all() {
        ArrayList<ResearchEntry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY_ENTRIES, "[]"));
            for (int index = 0; index < array.length(); index++) {
                entries.add(ResearchEntry.fromJson(array.getJSONObject(index)));
            }
        } catch (JSONException ignored) {
            // A malformed local cache should never prevent the app from opening.
        }
        Collections.sort(entries, new Comparator<ResearchEntry>() {
            @Override public int compare(ResearchEntry left, ResearchEntry right) {
                return Long.compare(right.createdAt, left.createdAt);
            }
        });
        return entries;
    }

    public synchronized List<ResearchEntry> saved() {
        ArrayList<ResearchEntry> result = new ArrayList<>();
        for (ResearchEntry entry : all()) {
            if (entry.saved) result.add(entry);
        }
        return result;
    }

    public synchronized void add(ResearchEntry incoming) {
        List<ResearchEntry> entries = all();
        for (int index = entries.size() - 1; index >= 0; index--) {
            ResearchEntry existing = entries.get(index);
            if ((!incoming.url.isEmpty() && incoming.url.equals(existing.url)) ||
                    (!incoming.query.isEmpty() && incoming.query.equalsIgnoreCase(existing.query))) {
                incoming.saved = existing.saved;
                entries.remove(index);
            }
        }
        entries.add(0, incoming);
        if (entries.size() > MAX_ITEMS) entries = entries.subList(0, MAX_ITEMS);
        write(entries);
    }

    public synchronized boolean toggleSaved(String id) {
        List<ResearchEntry> entries = all();
        boolean value = false;
        for (ResearchEntry entry : entries) {
            if (entry.id.equals(id)) {
                entry.saved = !entry.saved;
                value = entry.saved;
                break;
            }
        }
        write(entries);
        return value;
    }

    public synchronized void clearRecent() {
        List<ResearchEntry> kept = new ArrayList<>();
        for (ResearchEntry entry : all()) {
            if (entry.saved) kept.add(entry);
        }
        write(kept);
    }

    private void write(List<ResearchEntry> entries) {
        JSONArray array = new JSONArray();
        for (ResearchEntry entry : entries) {
            try {
                array.put(entry.toJson());
            } catch (JSONException ignored) {
                // Skip only the malformed item.
            }
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply();
    }
}
