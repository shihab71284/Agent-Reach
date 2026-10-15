package com.agentreach.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fast, deterministic extractive summaries that run fully on-device. */
public final class LocalSummarizer {
    private static final Pattern TITLE_META = Pattern.compile("(?im)^Title:\\s*(.+)$");
    private static final Pattern URL_META = Pattern.compile("(?im)^URL Source:\\s*(https?://\\S+)");
    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,3}\\s+(.+)$");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^]\\n]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern SENTENCE = Pattern.compile("[^.!?。！？\\n]+[.!?。！？]?");
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "and", "are", "as", "at", "be", "been", "but", "by", "for", "from",
            "has", "have", "he", "her", "his", "i", "in", "is", "it", "its", "of", "on", "or",
            "our", "she", "that", "the", "their", "this", "to", "was", "we", "were", "what",
            "when", "where", "which", "who", "will", "with", "you", "your"
    ));

    public static final class Link {
        public final String title;
        public final String url;
        Link(String title, String url) { this.title = title; this.url = url; }
    }

    public static final class Summary {
        public final String title;
        public final String sourceUrl;
        public final List<String> bullets;
        public final List<Link> links;
        public final String displayText;
        public final int wordCount;
        public final int readMinutes;

        Summary(String title, String sourceUrl, List<String> bullets, List<Link> links,
                String displayText, int wordCount) {
            this.title = title;
            this.sourceUrl = sourceUrl;
            this.bullets = bullets;
            this.links = links;
            this.displayText = displayText;
            this.wordCount = wordCount;
            this.readMinutes = Math.max(1, (int) Math.ceil(wordCount / 220.0));
        }

        public String excerpt() {
            String joined = join(bullets, " ");
            if (joined.isEmpty()) joined = displayText.replace('\n', ' ').trim();
            return joined.length() > 420 ? joined.substring(0, 417).trim() + "…" : joined;
        }
    }

    private LocalSummarizer() {}

    public static Summary summarize(String markdown, String query, String fallbackUrl) {
        String raw = markdown == null ? "" : markdown.replace("\r\n", "\n");
        String title = findTitle(raw, query, fallbackUrl);
        String sourceUrl = findFirst(URL_META, raw);
        if (sourceUrl.isEmpty()) sourceUrl = fallbackUrl == null ? "" : fallbackUrl;
        List<Link> links = extractLinks(raw);
        String display = cleanMarkdown(raw);
        int words = countWords(display);
        List<String> bullets = rankSentences(display, query, title);
        return new Summary(title, sourceUrl, bullets, links, display, words);
    }

    private static String findTitle(String raw, String query, String fallbackUrl) {
        String title = findFirst(TITLE_META, raw);
        if (title.isEmpty()) title = findFirst(HEADING, raw);
        title = stripMarkup(title).trim();
        if (!title.isEmpty() && title.length() <= 180) return title;
        if (query != null && !query.trim().isEmpty()) return capitalize(query.trim());
        if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
            try {
                String host = java.net.URI.create(fallbackUrl).getHost();
                if (host != null) return host.replaceFirst("^www\\.", "");
            } catch (Exception ignored) {}
        }
        return "Research brief";
    }

    private static List<Link> extractLinks(String raw) {
        ArrayList<Link> links = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Matcher matcher = MARKDOWN_LINK.matcher(raw);
        while (matcher.find() && links.size() < 12) {
            String title = stripMarkup(matcher.group(1)).trim();
            String url = matcher.group(2).trim();
            if (url.contains("r.jina.ai/http")) continue;
            if (title.length() < 2 || !seen.add(url)) continue;
            links.add(new Link(title.length() > 90 ? title.substring(0, 87) + "…" : title, url));
        }
        return links;
    }

    static String cleanMarkdown(String raw) {
        String value = raw;
        value = value.replaceAll("(?im)^Title:\\s*.*$", "");
        value = value.replaceAll("(?im)^URL Source:\\s*.*$", "");
        value = value.replaceAll("(?im)^Published Time:\\s*.*$", "");
        value = value.replaceAll("(?im)^Markdown Content:\\s*$", "");
        value = value.replaceAll("!\\[[^]]*]\\([^)]*\\)", "");
        value = value.replaceAll("\\[([^]\\n]+)]\\((https?://[^)\\s]+)\\)", "$1\n$2");
        value = value.replaceAll("(?m)^#{1,6}\\s+", "");
        value = value.replaceAll("(?m)^\\s*[-*+]\\s+", "• ");
        value = value.replaceAll("(?m)^\\s*>\\s?", "");
        value = value.replace("**", "").replace("__", "").replace("`", "");
        value = value.replaceAll("(?m)^[-=_]{3,}\\s*$", "");
        value = value.replaceAll("[ \\t]+\\n", "\n");
        value = value.replaceAll("\\n{3,}", "\n\n");
        return value.trim();
    }

    private static List<String> rankSentences(String display, String query, String title) {
        ArrayList<String> candidates = new ArrayList<>();
        Matcher matcher = SENTENCE.matcher(display.replace("• ", ""));
        while (matcher.find()) {
            String sentence = matcher.group().replaceAll("\\s+", " ").trim();
            int words = countWords(sentence);
            if (words >= 7 && words <= 55 && !sentence.startsWith("http") &&
                    !sentence.toLowerCase(Locale.US).contains("cookie policy")) {
                candidates.add(sentence);
            }
        }
        if (candidates.isEmpty()) {
            String[] lines = display.split("\\n");
            for (String line : lines) {
                String clean = line.replace("• ", "").trim();
                if (countWords(clean) >= 4 && !clean.startsWith("http")) candidates.add(clean);
                if (candidates.size() >= 3) break;
            }
        }

        final Map<String, Integer> frequencies = frequencies(display);
        final Set<String> focus = keywords((query == null ? "" : query) + " " + (title == null ? "" : title));
        final Map<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < candidates.size(); index++) positions.put(candidates.get(index), index);

        ArrayList<String> ranked = new ArrayList<>(candidates);
        Collections.sort(ranked, new Comparator<String>() {
            @Override public int compare(String left, String right) {
                return Double.compare(score(right, frequencies, focus, positions), score(left, frequencies, focus, positions));
            }
        });

        List<String> chosen = new ArrayList<>();
        for (String sentence : ranked) {
            if (chosen.size() == 3) break;
            boolean duplicate = false;
            Set<String> sentenceWords = keywords(sentence);
            for (String existing : chosen) {
                Set<String> overlap = new HashSet<>(sentenceWords);
                overlap.retainAll(keywords(existing));
                if (overlap.size() >= Math.max(4, sentenceWords.size() * 0.65)) duplicate = true;
            }
            if (!duplicate) chosen.add(sentence);
        }
        Collections.sort(chosen, new Comparator<String>() {
            @Override public int compare(String left, String right) {
                return Integer.compare(positions.containsKey(left) ? positions.get(left) : 0,
                        positions.containsKey(right) ? positions.get(right) : 0);
            }
        });
        return chosen;
    }

    private static double score(String sentence, Map<String, Integer> frequencies,
                                Set<String> focus, Map<String, Integer> positions) {
        Set<String> words = keywords(sentence);
        double value = 0;
        for (String word : words) {
            value += Math.min(5, frequencies.containsKey(word) ? frequencies.get(word) : 0);
            if (focus.contains(word)) value += 6;
        }
        int position = positions.containsKey(sentence) ? positions.get(sentence) : 20;
        value += Math.max(0, 12 - position * 0.5);
        int length = countWords(sentence);
        if (length >= 12 && length <= 32) value += 5;
        return value;
    }

    private static Map<String, Integer> frequencies(String text) {
        Map<String, Integer> values = new HashMap<>();
        for (String raw : text.toLowerCase(Locale.US).split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() >= 3 && !STOP_WORDS.contains(raw)) {
                values.put(raw, values.containsKey(raw) ? values.get(raw) + 1 : 1);
            }
        }
        return values;
    }

    private static Set<String> keywords(String text) {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        for (String raw : text.toLowerCase(Locale.US).split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() >= 3 && !STOP_WORDS.contains(raw)) words.add(raw);
        }
        return words;
    }

    private static int countWords(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return 0;
        return clean.split("\\s+").length;
    }

    private static String findFirst(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String stripMarkup(String value) {
        if (value == null) return "";
        return value.replaceAll("[*_`#]", "").replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1");
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) return value;
        return value.substring(0, 1).toUpperCase(Locale.getDefault()) + value.substring(1);
    }

    private static String join(List<String> values, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(delimiter);
            builder.append(value);
        }
        return builder.toString();
    }
}
