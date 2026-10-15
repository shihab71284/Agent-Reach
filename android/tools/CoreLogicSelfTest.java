package com.agentreach.mobile;

/** Zero-dependency smoke tests for the two pure-Java intelligence components. */
public final class CoreLogicSelfTest {
    private static int checks;

    public static void main(String[] args) {
        SmartRouter.Decision github = SmartRouter.route("https://github.com/Panniantong/agent-reach");
        check(github.isUrl, "GitHub URL should be recognized as a URL");
        check(github.channel == SmartRouter.Channel.GITHUB, "GitHub URL should use GitHub lane");
        check(SmartRouter.route("https://github.com.attacker.example/repo").channel == SmartRouter.Channel.WEB,
                "Lookalike domain must not be trusted");
        check(SmartRouter.route("What do people think on Reddit about Android?").channel == SmartRouter.Channel.REDDIT,
                "Community query should use Reddit lane");
        check(SmartRouter.route("youtu.be/demo").channel == SmartRouter.Channel.YOUTUBE,
                "Bare YouTube URL should be normalized and routed");
        SmartRouter.Decision shared = SmartRouter.route("Interesting article\nhttps://example.com/read-me");
        check(shared.isUrl && "https://example.com/read-me".equals(shared.normalizedInput),
                "URLs embedded in Android share text should be extracted");

        String markdown = "Title: A local-first workflow\n" +
                "URL Source: https://example.com/research\n\n" +
                "# A local-first workflow\n\n" +
                "Local-first software keeps useful state on the user's own device while allowing deliberate network requests. " +
                "A research assistant routes each question before connecting so that it can select a relevant source. " +
                "Extractive summaries rank supplied sentences and avoid inventing facts absent from the article. " +
                "Readers can inspect the complete source and recover the original context.\n\n" +
                "[Project](https://github.com/example/project)";
        LocalSummarizer.Summary summary = LocalSummarizer.summarize(markdown, "local research", "");
        check("A local-first workflow".equals(summary.title), "Title metadata should be extracted");
        check("https://example.com/research".equals(summary.sourceUrl), "Source metadata should be extracted");
        check(!summary.bullets.isEmpty() && summary.bullets.size() <= 3, "Brief should contain up to three bullets");
        check(summary.links.size() == 1, "Markdown link should be extracted");
        check(summary.displayText.contains("https://github.com/example/project"), "Reader view should retain link target");

        System.out.println("Core logic self-test passed: " + checks + " checks");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
