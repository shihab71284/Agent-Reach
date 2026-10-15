package com.agentreach.mobile;

import java.net.URI;
import java.net.URLEncoder;
import java.util.Locale;

/** Offline intent and URL router. It never sends text anywhere. */
public final class SmartRouter {
    public enum Channel {
        WEB("Web", "◎", "Broad web research"),
        GITHUB("GitHub", "⌘", "Repositories, issues and code"),
        YOUTUBE("YouTube", "▶", "Videos and transcripts"),
        REDDIT("Reddit", "◉", "Community discussions"),
        X("X / Twitter", "𝕏", "Real-time public conversation"),
        RSS("RSS", "◔", "Feeds and publications"),
        BILIBILI("Bilibili", "B", "Videos and subtitles"),
        XIAOHONGSHU("Xiaohongshu", "小", "Social notes and reviews"),
        WECHAT("WeChat", "微", "Public articles"),
        V2EX("V2EX", "V", "Developer discussions");

        public final String label;
        public final String glyph;
        public final String description;

        Channel(String label, String glyph, String description) {
            this.label = label;
            this.glyph = glyph;
            this.description = description;
        }
    }

    public static final class Decision {
        public final String input;
        public final String normalizedInput;
        public final boolean isUrl;
        public final Channel channel;
        public final String reason;

        Decision(String input, String normalizedInput, boolean isUrl, Channel channel, String reason) {
            this.input = input;
            this.normalizedInput = normalizedInput;
            this.isUrl = isUrl;
            this.channel = channel;
            this.reason = reason;
        }
    }

    private SmartRouter() {}

    public static Decision route(String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim();
        String normalized = normalizeUrl(input);
        boolean url = isHttpUrl(normalized);
        if (!url) {
            String sharedUrl = firstEmbeddedUrl(input);
            if (!sharedUrl.isEmpty()) {
                normalized = sharedUrl;
                url = true;
            }
        }
        String lower = input.toLowerCase(Locale.US);
        String host = url ? hostOf(normalized) : "";

        if (matchesHost(host, "github.com") || (!url && containsAny(lower, "github", "repository", " repo ", "pull request", "source code"))) {
            return decision(input, normalized, url, Channel.GITHUB, url ? "GitHub link detected" : "Code research intent detected");
        }
        if (matchesHost(host, "youtube.com") || matchesHost(host, "youtu.be") || (!url && containsAny(lower, "youtube", "video transcript", "watch video"))) {
            return decision(input, normalized, url, Channel.YOUTUBE, url ? "YouTube link detected" : "Video research intent detected");
        }
        if (matchesHost(host, "reddit.com") || (!url && containsAny(lower, "reddit", "what do people think", "community opinion", "discussion thread"))) {
            return decision(input, normalized, url, Channel.REDDIT, url ? "Reddit link detected" : "Community research intent detected");
        }
        if (matchesHost(host, "x.com") || matchesHost(host, "twitter.com") || (!url && containsAny(lower, "twitter", "tweets about", "posts on x"))) {
            return decision(input, normalized, url, Channel.X, url ? "X / Twitter link detected" : "Real-time conversation intent detected");
        }
        if (matchesHost(host, "bilibili.com") || (!url && containsAny(lower, "bilibili", "b站"))) {
            return decision(input, normalized, url, Channel.BILIBILI, "Bilibili source detected");
        }
        if (matchesHost(host, "xiaohongshu.com") || matchesHost(host, "xhslink.com") || (!url && containsAny(lower, "xiaohongshu", "小红书"))) {
            return decision(input, normalized, url, Channel.XIAOHONGSHU, "Xiaohongshu source detected");
        }
        if (matchesHost(host, "mp.weixin.qq.com") || (!url && containsAny(lower, "wechat article", "微信公众号"))) {
            return decision(input, normalized, url, Channel.WECHAT, "WeChat publication detected");
        }
        if (matchesHost(host, "v2ex.com") || (!url && containsAny(lower, "v2ex"))) {
            return decision(input, normalized, url, Channel.V2EX, "Developer community source detected");
        }
        if (looksLikeFeed(lower, host)) {
            return decision(input, normalized, url, Channel.RSS, "Feed or publication detected");
        }
        return decision(input, normalized, url, Channel.WEB,
                url ? "Ready for distraction-free reading" : "Best matched to broad web research");
    }

    public static String searchUrl(Channel channel, String query) {
        String encoded = encode(query);
        switch (channel) {
            case GITHUB: return "https://github.com/search?q=" + encoded;
            case YOUTUBE: return "https://www.youtube.com/results?search_query=" + encoded;
            case REDDIT: return "https://www.reddit.com/search/?q=" + encoded;
            case X: return "https://x.com/search?q=" + encoded;
            case BILIBILI: return "https://search.bilibili.com/all?keyword=" + encoded;
            case XIAOHONGSHU: return "https://www.xiaohongshu.com/search_result?keyword=" + encoded;
            case WECHAT: return "https://weixin.sogou.com/weixin?type=2&query=" + encoded;
            case V2EX: return "https://www.google.com/search?q=site%3Av2ex.com+" + encoded;
            case RSS:
            case WEB:
            default: return "https://www.google.com/search?q=" + encoded;
        }
    }

    static String normalizeUrl(String input) {
        if (input == null) return "";
        String value = input.trim();
        if (value.matches("(?i)^www\\.[^\\s]+$") ||
                value.matches("(?i)^[a-z0-9.-]+\\.[a-z]{2,}(/[^\\s]*)?$")) {
            return "https://" + value;
        }
        return value;
    }

    static boolean isHttpUrl(String input) {
        try {
            URI uri = URI.create(input);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Decision decision(String input, String normalized, boolean url, Channel channel, String reason) {
        return new Decision(input, normalized, url, channel, reason);
    }

    private static String hostOf(String value) {
        try {
            String host = URI.create(value).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    private static boolean looksLikeFeed(String lower, String host) {
        return lower.endsWith(".rss") || lower.endsWith(".xml") || lower.contains(" rss ") ||
                lower.startsWith("rss ") || lower.contains("atom feed") || matchesHost(host, "feedburner.com");
    }

    private static boolean matchesHost(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private static String firstEmbeddedUrl(String input) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("https?://[^\\s<>]+", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(input);
        if (!matcher.find()) return "";
        String value = matcher.group();
        while (value.endsWith(".") || value.endsWith(",") || value.endsWith(")") ||
                value.endsWith("]") || value.endsWith("!")) {
            value = value.substring(0, value.length() - 1);
        }
        return isHttpUrl(value) ? value : "";
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception ignored) {
            return value.replace(" ", "+");
        }
    }
}
