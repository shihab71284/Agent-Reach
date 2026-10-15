package com.agentreach.mobile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalSummarizerTest {
    private static final String ARTICLE =
            "Title: A practical local-first research workflow\n" +
            "URL Source: https://example.com/research\n\n" +
            "# A practical local-first research workflow\n\n" +
            "Local-first software keeps useful state on the user's own device while still allowing deliberate network requests. " +
            "A research assistant can route a question before connecting, which reduces unnecessary disclosure and selects a better source. " +
            "Extractive summaries rank sentences from the supplied article and do not invent facts that are absent from it. " +
            "The complete source should remain available so readers can inspect evidence and recover context.\n\n" +
            "[Project source](https://github.com/example/research)";

    @Test public void extractsMetadataAndLinks() {
        LocalSummarizer.Summary result = LocalSummarizer.summarize(
                ARTICLE, "local first research", "https://fallback.example");
        assertEquals("A practical local-first research workflow", result.title);
        assertEquals("https://example.com/research", result.sourceUrl);
        assertEquals(1, result.links.size());
        assertEquals("https://github.com/example/research", result.links.get(0).url);
    }

    @Test public void createsGroundedBrief() {
        LocalSummarizer.Summary result = LocalSummarizer.summarize(
                ARTICLE, "privacy research source", "");
        assertFalse(result.bullets.isEmpty());
        assertTrue(result.bullets.size() <= 3);
        assertTrue(result.wordCount > 40);
        assertTrue(result.readMinutes >= 1);
        for (String bullet : result.bullets) assertTrue(result.displayText.contains(bullet));
    }

    @Test public void cleansMarkdownForReaderMode() {
        String clean = LocalSummarizer.cleanMarkdown("## Heading\n- **Useful** [link](https://example.com)");
        assertTrue(clean.contains("Heading"));
        assertTrue(clean.contains("• Useful"));
        assertTrue(clean.contains("https://example.com"));
        assertFalse(clean.contains("**"));
    }
}
