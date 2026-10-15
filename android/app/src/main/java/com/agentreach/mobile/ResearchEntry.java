package com.agentreach.mobile;

import org.json.JSONException;
import org.json.JSONObject;

/** A compact, locally stored research item. */
public final class ResearchEntry {
    public String id;
    public String title;
    public String url;
    public String query;
    public String excerpt;
    public String channel;
    public long createdAt;
    public boolean saved;

    public ResearchEntry() {}

    public ResearchEntry(String title, String url, String query, String excerpt, String channel) {
        this.id = Long.toHexString(System.currentTimeMillis()) + Integer.toHexString((url + query).hashCode());
        this.title = title;
        this.url = url == null ? "" : url;
        this.query = query == null ? "" : query;
        this.excerpt = excerpt == null ? "" : excerpt;
        this.channel = channel == null ? "Web" : channel;
        this.createdAt = System.currentTimeMillis();
    }

    JSONObject toJson() throws JSONException {
        JSONObject value = new JSONObject();
        value.put("id", id);
        value.put("title", title);
        value.put("url", url);
        value.put("query", query);
        value.put("excerpt", excerpt);
        value.put("channel", channel);
        value.put("createdAt", createdAt);
        value.put("saved", saved);
        return value;
    }

    static ResearchEntry fromJson(JSONObject value) {
        ResearchEntry entry = new ResearchEntry();
        entry.id = value.optString("id", Long.toHexString(System.currentTimeMillis()));
        entry.title = value.optString("title", "Untitled research");
        entry.url = value.optString("url", "");
        entry.query = value.optString("query", "");
        entry.excerpt = value.optString("excerpt", "");
        entry.channel = value.optString("channel", "Web");
        entry.createdAt = value.optLong("createdAt", System.currentTimeMillis());
        entry.saved = value.optBoolean("saved", false);
        return entry;
    }
}
