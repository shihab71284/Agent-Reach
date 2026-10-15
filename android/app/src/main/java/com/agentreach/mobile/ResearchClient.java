package com.agentreach.mobile;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Small HTTP client for Jina Reader and Search. Runs only on a worker thread. */
public final class ResearchClient {
    private static final int MAX_BYTES = 2_500_000;
    private static final String USER_AGENT = "AgentReach-Android/1.0";

    public static final class Response {
        public final String content;
        public final String requestUrl;

        Response(String content, String requestUrl) {
            this.content = content;
            this.requestUrl = requestUrl;
        }
    }

    public Response read(String originalUrl) throws IOException {
        if (!SmartRouter.isHttpUrl(originalUrl)) throw new IOException("That does not look like a valid web address.");
        return get("https://r.jina.ai/" + originalUrl);
    }

    public Response search(String query) throws IOException {
        if (query == null || query.trim().isEmpty()) throw new IOException("Enter a topic to research.");
        String encoded;
        try {
            encoded = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20");
        } catch (Exception exception) {
            encoded = query.trim().replace(" ", "%20");
        }
        return get("https://s.jina.ai/" + encoded);
    }

    private Response get(String requestUrl) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(requestUrl).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(45_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "text/plain, text/markdown;q=0.9, */*;q=0.5");
            connection.setRequestProperty("X-Return-Format", "markdown");

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException(status == 429
                        ? "The public reader is busy. Wait a moment, then try again."
                        : "The source returned HTTP " + status + ". Try opening it in your browser.");
            }

            BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) throw new IOException("This page is too large for the mobile reader.");
                output.write(buffer, 0, count);
            }
            String content = new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) throw new IOException("The reader returned an empty page.");
            return new Response(content, requestUrl);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
