package com.agentreach.mobile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmartRouterTest {
    @Test public void recognizesPlatformUrls() {
        SmartRouter.Decision github = SmartRouter.route("https://github.com/Panniantong/agent-reach");
        assertTrue(github.isUrl);
        assertEquals(SmartRouter.Channel.GITHUB, github.channel);

        SmartRouter.Decision youtube = SmartRouter.route("youtu.be/example");
        assertTrue(youtube.isUrl);
        assertEquals(SmartRouter.Channel.YOUTUBE, youtube.channel);
    }

    @Test public void doesNotTrustLookalikeDomains() {
        SmartRouter.Decision spoof = SmartRouter.route("https://github.com.attacker.example/repo");
        assertTrue(spoof.isUrl);
        assertEquals(SmartRouter.Channel.WEB, spoof.channel);
    }

    @Test public void extractsUrlsFromAndroidShareText() {
        SmartRouter.Decision shared = SmartRouter.route("Useful page\nhttps://example.com/article");
        assertTrue(shared.isUrl);
        assertEquals("https://example.com/article", shared.normalizedInput);
    }

    @Test public void routesNaturalLanguageLocally() {
        assertEquals(SmartRouter.Channel.REDDIT,
                SmartRouter.route("What do people think about local-first apps?").channel);
        assertEquals(SmartRouter.Channel.GITHUB,
                SmartRouter.route("Find a source code repository for RSS parsing").channel);
        assertFalse(SmartRouter.route("latest Android research").isUrl);
    }

    @Test public void createsSourceSpecificSearchUrls() {
        String value = SmartRouter.searchUrl(SmartRouter.Channel.YOUTUBE, "agent research");
        assertTrue(value.startsWith("https://www.youtube.com/results"));
        assertTrue(value.contains("agent+research"));
    }
}
