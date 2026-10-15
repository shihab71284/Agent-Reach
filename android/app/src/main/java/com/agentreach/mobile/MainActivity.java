package com.agentreach.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Native, private-by-default mobile companion for Agent Reach. */
public final class MainActivity extends Activity {
    private static final int VOICE_REQUEST = 2401;
    private static final String PAGE_HOME = "home";
    private static final String PAGE_LIBRARY = "library";
    private static final String PAGE_SETTINGS = "settings";
    private static final String PAGE_READER = "reader";
    private static final String PAGE_LOADING = "loading";

    private FrameLayout pageHost;
    private LinearLayout navBar;
    private final List<TextView> navItems = new ArrayList<>();
    private String currentPage = PAGE_HOME;
    private EditText commandInput;
    private TextView routeHint;
    private SmartRouter.Channel preferredChannel = SmartRouter.Channel.WEB;
    private HistoryStore historyStore;
    private final ResearchClient researchClient = new ResearchClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger requestGeneration = new AtomicInteger();
    private ResearchEntry currentEntry;
    private LocalSummarizer.Summary currentSummary;
    private SmartRouter.Decision currentDecision;
    private boolean librarySavedOnly;

    @Override protected void onCreate(Bundle savedInstanceState) {
        setTheme(android.R.style.Theme_Material_NoActionBar);
        super.onCreate(savedInstanceState);
        configureWindow();
        historyStore = new HistoryStore(this);
        buildShell();
        showHome();
        pageHost.post(new Runnable() {
            @Override public void run() {
                handleIncoming(getIntent());
            }
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
    }

    @Override protected void onDestroy() {
        requestGeneration.incrementAndGet();
        worker.shutdownNow();
        super.onDestroy();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(AgentTheme.INK);
        window.setNavigationBarColor(AgentTheme.INK);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            window.getDecorView().setSystemUiVisibility(0);
        }
    }

    private void buildShell() {
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AgentTheme.INK);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                root.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
                return insets;
            }
        });

        pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        View divider = new View(this);
        divider.setBackgroundColor(AgentTheme.BORDER);
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AgentTheme.dp(this, 1)));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(AgentTheme.dp(this, 10), AgentTheme.dp(this, 7),
                AgentTheme.dp(this, 10), AgentTheme.dp(this, 7));
        navBar.setBackgroundColor(AgentTheme.INK);
        addNavItem("⌂\nExplore", PAGE_HOME);
        addNavItem("◇\nLibrary", PAGE_LIBRARY);
        addNavItem("⚙\nSettings", PAGE_SETTINGS);
        root.addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AgentTheme.dp(this, 68)));
        setContentView(root);
    }

    private void addNavItem(String title, final String page) {
        TextView item = AgentTheme.text(this, title, 12, AgentTheme.MUTED);
        item.setGravity(Gravity.CENTER);
        item.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        item.setLineSpacing(0, 0.9f);
        item.setContentDescription(title.replace("\n", " "));
        item.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (PAGE_HOME.equals(page)) showHome();
                else if (PAGE_LIBRARY.equals(page)) showLibrary();
                else showSettings();
            }
        });
        navItems.add(item);
        navBar.addView(item, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private void selectNavigation(int index) {
        for (int item = 0; item < navItems.size(); item++) {
            TextView view = navItems.get(item);
            view.setTextColor(item == index ? AgentTheme.CYAN : AgentTheme.MUTED);
            view.setBackground(item == index
                    ? AgentTheme.background(AgentTheme.SURFACE, 14, AgentTheme.BORDER, 1, this)
                    : null);
        }
    }

    private void showHome() {
        requestGeneration.incrementAndGet();
        currentPage = PAGE_HOME;
        currentEntry = null;
        currentSummary = null;
        selectNavigation(0);
        navBar.setVisibility(View.VISIBLE);

        LinearLayout content = pageColumn();
        content.addView(appHeader("PRIVATE RESEARCH COPILOT"));
        content.addView(AgentTheme.spacer(this, 24));

        TextView eyebrow = AgentTheme.label(this, "SEARCH · READ · DISTILL");
        eyebrow.setTextColor(AgentTheme.CYAN);
        content.addView(eyebrow);
        content.addView(AgentTheme.spacer(this, 10));
        TextView title = AgentTheme.text(this, "Reach beyond\nthe obvious.", 38, AgentTheme.TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLetterSpacing(-0.025f);
        content.addView(title);
        content.addView(AgentTheme.spacer(this, 10));
        TextView subtitle = AgentTheme.text(this,
                "Ask a question or paste a link. Agent Reach finds the right lane, reads it cleanly, and builds a private smart brief.",
                16, AgentTheme.MUTED);
        content.addView(subtitle);

        SignalView signal = new SignalView(this);
        signal.setActive(true);
        LinearLayout.LayoutParams signalParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AgentTheme.dp(this, 190));
        signalParams.topMargin = AgentTheme.dp(this, 5);
        signalParams.bottomMargin = AgentTheme.dp(this, 5);
        content.addView(signal, signalParams);

        LinearLayout commandCard = AgentTheme.card(this);
        TextView inputLabel = AgentTheme.label(this, "What should we reach?");
        commandCard.addView(inputLabel);
        commandCard.addView(AgentTheme.spacer(this, 8));
        commandInput = new EditText(this);
        commandInput.setHint("Ask anything, or paste a URL…");
        commandInput.setHintTextColor(Color.rgb(105, 122, 150));
        commandInput.setTextColor(AgentTheme.TEXT);
        commandInput.setTextSize(17);
        commandInput.setGravity(Gravity.TOP | Gravity.START);
        commandInput.setMinLines(2);
        commandInput.setMaxLines(4);
        commandInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        commandInput.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        commandInput.setPadding(0, AgentTheme.dp(this, 6), 0, AgentTheme.dp(this, 8));
        commandInput.setBackgroundColor(Color.TRANSPARENT);
        commandInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO ||
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.isCtrlPressed())) {
                    runCommand(commandInput.getText().toString());
                    return true;
                }
                return false;
            }
        });
        commandCard.addView(commandInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        routeHint = AgentTheme.text(this, "◎ Smart routing is ready", 12, AgentTheme.MUTED);
        routeHint.setPadding(0, AgentTheme.dp(this, 3), 0, AgentTheme.dp(this, 12));
        commandCard.addView(routeHint);
        commandInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateRouteHint(text.toString());
            }
            @Override public void afterTextChanged(Editable editable) {}
        });

        LinearLayout commandActions = new LinearLayout(this);
        commandActions.setOrientation(LinearLayout.HORIZONTAL);
        commandActions.setGravity(Gravity.CENTER_VERTICAL);
        TextView voice = AgentTheme.button(this, "⌁  Voice", false);
        voice.setContentDescription("Start voice search");
        voice.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startVoiceSearch(); }
        });
        commandActions.addView(voice, wrap());
        TextView go = AgentTheme.button(this, "Reach  →", true);
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        goParams.leftMargin = AgentTheme.dp(this, 10);
        commandActions.addView(go, goParams);
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { runCommand(commandInput.getText().toString()); }
        });
        commandCard.addView(commandActions);
        content.addView(commandCard);

        content.addView(AgentTheme.spacer(this, 24));
        content.addView(sectionTitle("Research lanes", "Choose a source, or let smart routing decide."));
        content.addView(sourceStrip());

        List<ResearchEntry> recent = historyStore.all();
        if (!recent.isEmpty()) {
            content.addView(AgentTheme.spacer(this, 28));
            content.addView(sectionTitle("Continue reaching", "Your latest briefs stay on this device."));
            int count = Math.min(3, recent.size());
            for (int index = 0; index < count; index++) content.addView(historyCard(recent.get(index)));
        }

        content.addView(AgentTheme.spacer(this, 24));
        content.addView(privacyCard());
        content.addView(AgentTheme.spacer(this, 20));
        setPage(scroll(content));
    }

    private View appHeader(String badgeText) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        BrandMarkView logo = new BrandMarkView(this);
        header.addView(logo, new LinearLayout.LayoutParams(AgentTheme.dp(this, 44), AgentTheme.dp(this, 44)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(AgentTheme.dp(this, 11), 0, 0, 0);
        TextView name = AgentTheme.text(this, "AGENT REACH", 16, AgentTheme.TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setLetterSpacing(0.09f);
        names.addView(name);
        TextView descriptor = AgentTheme.text(this, "Mobile intelligence", 12, AgentTheme.MUTED);
        names.addView(descriptor);
        header.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView badge = AgentTheme.pill(this, "●  " + badgeText, AgentTheme.GREEN);
        badge.setTextSize(9);
        header.addView(badge);
        return header;
    }

    private View sourceStrip() {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        SmartRouter.Channel[] lanes = {SmartRouter.Channel.WEB, SmartRouter.Channel.GITHUB,
                SmartRouter.Channel.YOUTUBE, SmartRouter.Channel.REDDIT, SmartRouter.Channel.X,
                SmartRouter.Channel.RSS};
        for (SmartRouter.Channel lane : lanes) {
            final SmartRouter.Channel sourceLane = lane;
            TextView chip = AgentTheme.pill(this, sourceLane.glyph + "  " + sourceLane.label,
                    sourceLane == preferredChannel ? AgentTheme.CYAN : AgentTheme.MUTED);
            chip.setContentDescription("Use " + sourceLane.label + " research lane");
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    preferredChannel = sourceLane;
                    updateRouteHint(commandInput == null ? "" : commandInput.getText().toString());
                    Toast.makeText(MainActivity.this, sourceLane.label + " lane selected", Toast.LENGTH_SHORT).show();
                    showHomePreservingInput();
                }
            });
            LinearLayout.LayoutParams params = wrap();
            params.rightMargin = AgentTheme.dp(this, 9);
            row.addView(chip, params);
        }
        scroller.addView(row);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AgentTheme.dp(this, 12);
        scroller.setLayoutParams(params);
        return scroller;
    }

    private void showHomePreservingInput() {
        String text = commandInput == null ? "" : commandInput.getText().toString();
        showHome();
        commandInput.setText(text);
        commandInput.setSelection(commandInput.length());
    }

    private void updateRouteHint(String value) {
        if (routeHint == null) return;
        if (TextUtils.isEmpty(value.trim())) {
            routeHint.setText(preferredChannel.glyph + "  " + preferredChannel.label + " lane selected · routing happens on-device");
            return;
        }
        SmartRouter.Decision decision = SmartRouter.route(value);
        SmartRouter.Channel channel = preferredChannel == SmartRouter.Channel.WEB ? decision.channel : preferredChannel;
        routeHint.setText(channel.glyph + "  " + channel.label + " · " +
                (preferredChannel == SmartRouter.Channel.WEB ? decision.reason : preferredChannel.description));
        routeHint.setTextColor(AgentTheme.CYAN);
    }

    private void runCommand(String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Ask a question or paste a link first.", Toast.LENGTH_SHORT).show();
            if (commandInput != null) commandInput.requestFocus();
            return;
        }
        hideKeyboard();
        SmartRouter.Decision routed = SmartRouter.route(input);
        SmartRouter.Decision decision = routed;
        if (!routed.isUrl && preferredChannel != SmartRouter.Channel.WEB) {
            decision = new SmartRouter.Decision(routed.input, routed.normalizedInput, false,
                    preferredChannel, preferredChannel.description + " selected");
        }
        final SmartRouter.Decision requestDecision = decision;
        final int generation = requestGeneration.incrementAndGet();
        showLoading(requestDecision);

        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    String effectiveQuery = requestDecision.isUrl ? "" : qualifiedQuery(requestDecision.channel, requestDecision.input);
                    ResearchClient.Response response = requestDecision.isUrl
                            ? researchClient.read(requestDecision.normalizedInput)
                            : researchClient.search(effectiveQuery);
                    final LocalSummarizer.Summary summary = LocalSummarizer.summarize(response.content,
                            requestDecision.input, requestDecision.isUrl ? requestDecision.normalizedInput : "");
                    if (generation != requestGeneration.get()) return;
                    mainHandler.post(new Runnable() {
                        @Override public void run() {
                            if (generation != requestGeneration.get()) return;
                            ResearchEntry entry = new ResearchEntry(summary.title,
                                    requestDecision.isUrl ? requestDecision.normalizedInput : summary.sourceUrl,
                                    requestDecision.isUrl ? "" : requestDecision.input,
                                    summary.excerpt(), requestDecision.channel.label);
                            historyStore.add(entry);
                            currentEntry = entry;
                            currentSummary = summary;
                            currentDecision = requestDecision;
                            showReader(summary, requestDecision, entry);
                        }
                    });
                } catch (final Exception exception) {
                    if (generation != requestGeneration.get()) return;
                    mainHandler.post(new Runnable() {
                        @Override public void run() {
                            if (generation == requestGeneration.get()) showError(requestDecision, friendlyError(exception));
                        }
                    });
                }
            }
        });
    }

    private String qualifiedQuery(SmartRouter.Channel channel, String query) {
        switch (channel) {
            case GITHUB: return query + " site:github.com";
            case YOUTUBE: return query + " site:youtube.com";
            case REDDIT: return query + " site:reddit.com";
            case X: return query + " site:x.com OR site:twitter.com";
            case BILIBILI: return query + " site:bilibili.com";
            case XIAOHONGSHU: return query + " site:xiaohongshu.com";
            case WECHAT: return query + " site:mp.weixin.qq.com";
            case V2EX: return query + " site:v2ex.com";
            default: return query;
        }
    }

    private void showLoading(SmartRouter.Decision decision) {
        currentPage = PAGE_LOADING;
        navBar.setVisibility(View.GONE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(AgentTheme.dp(this, 28), AgentTheme.dp(this, 46),
                AgentTheme.dp(this, 28), AgentTheme.dp(this, 30));

        TextView label = AgentTheme.pill(this, decision.channel.glyph + "  " + decision.channel.label,
                AgentTheme.CYAN);
        content.addView(label, wrapCentered());
        SignalView signal = new SignalView(this);
        signal.setActive(true);
        content.addView(signal, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AgentTheme.dp(this, 260)));
        TextView title = AgentTheme.text(this,
                decision.isUrl ? "Reading beyond the page…" : "Reaching across the web…", 26, AgentTheme.TEXT);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);
        content.addView(AgentTheme.spacer(this, 12));
        TextView detail = AgentTheme.text(this,
                decision.isUrl ? "Removing clutter · mapping ideas · building your brief"
                        : "Finding sources · ranking context · extracting key points",
                14, AgentTheme.MUTED);
        detail.setGravity(Gravity.CENTER);
        content.addView(detail);
        content.addView(AgentTheme.spacer(this, 26));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.getIndeterminateDrawable().setTint(AgentTheme.BLUE);
        content.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AgentTheme.dp(this, 3)));
        content.addView(AgentTheme.spacer(this, 28));
        TextView cancel = AgentTheme.button(this, "Cancel", false);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showHome(); }
        });
        content.addView(cancel, wrapCentered());
        setPage(content);
    }

    private void showReader(final LocalSummarizer.Summary summary, final SmartRouter.Decision decision, final ResearchEntry entry) {
        currentPage = PAGE_READER;
        navBar.setVisibility(View.GONE);
        LinearLayout content = pageColumn();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = AgentTheme.button(this, "←", false);
        back.setContentDescription("Back to explore");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showHome(); }
        });
        top.addView(back, new LinearLayout.LayoutParams(AgentTheme.dp(this, 52), AgentTheme.dp(this, 48)));
        TextView brand = AgentTheme.text(this, "AGENT REACH  /  BRIEF", 12, AgentTheme.MUTED);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setLetterSpacing(0.1f);
        brand.setPadding(AgentTheme.dp(this, 13), 0, 0, 0);
        top.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView channel = AgentTheme.pill(this, decision.channel.glyph + " " + decision.channel.label,
                AgentTheme.CYAN);
        top.addView(channel);
        content.addView(top);
        content.addView(AgentTheme.spacer(this, 24));

        TextView freshness = AgentTheme.label(this, decision.isUrl ? "SMART READER" : "RESEARCH BRIEF");
        freshness.setTextColor(AgentTheme.GREEN);
        content.addView(freshness);
        content.addView(AgentTheme.spacer(this, 10));
        TextView title = AgentTheme.text(this, summary.title, 30, AgentTheme.TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLetterSpacing(-0.02f);
        title.setTextIsSelectable(true);
        content.addView(title);
        content.addView(AgentTheme.spacer(this, 12));
        TextView stats = AgentTheme.text(this,
                summary.readMinutes + " min read   ·   " + formatNumber(summary.wordCount) + " words   ·   distilled on-device",
                13, AgentTheme.MUTED);
        content.addView(stats);
        content.addView(AgentTheme.spacer(this, 18));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        final TextView save = AgentTheme.button(this, entry.saved ? "★  Saved" : "☆  Save", false);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                entry.saved = historyStore.toggleSaved(entry.id);
                save.setText(entry.saved ? "★  Saved" : "☆  Save");
                save.setTextColor(entry.saved ? AgentTheme.CYAN : AgentTheme.TEXT);
            }
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView share = AgentTheme.button(this, "↗  Share", false);
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        shareParams.leftMargin = AgentTheme.dp(this, 9);
        actions.addView(share, shareParams);
        share.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { shareBrief(summary, decision); }
        });
        content.addView(actions);

        content.addView(AgentTheme.spacer(this, 22));
        LinearLayout summaryCard = AgentTheme.card(this);
        summaryCard.setBackground(AgentTheme.background(Color.rgb(11, 28, 48), 20,
                Color.rgb(24, 92, 148), 1, this));
        LinearLayout briefHeader = new LinearLayout(this);
        briefHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView sparkle = AgentTheme.text(this, "✦", 24, AgentTheme.CYAN);
        briefHeader.addView(sparkle);
        TextView briefTitle = AgentTheme.text(this, " SMART BRIEF", 13, AgentTheme.TEXT);
        briefTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        briefTitle.setLetterSpacing(0.12f);
        briefHeader.addView(briefTitle);
        summaryCard.addView(briefHeader);
        summaryCard.addView(AgentTheme.spacer(this, 13));
        if (summary.bullets.isEmpty()) {
            summaryCard.addView(AgentTheme.text(this, "This source is concise—open the reader view below for the complete context.",
                    15, AgentTheme.MUTED));
        } else {
            for (String bullet : summary.bullets) {
                TextView point = AgentTheme.text(this, "•  " + bullet, 15, AgentTheme.TEXT);
                point.setPadding(0, 0, 0, AgentTheme.dp(this, 12));
                point.setTextIsSelectable(true);
                summaryCard.addView(point);
            }
        }
        content.addView(summaryCard);

        if (!summary.links.isEmpty()) {
            content.addView(AgentTheme.spacer(this, 26));
            content.addView(sectionTitle(decision.isUrl ? "Links in this page" : "Source trail",
                    "Follow the evidence without losing your brief."));
            int maxLinks = Math.min(6, summary.links.size());
            for (int index = 0; index < maxLinks; index++) content.addView(linkCard(summary.links.get(index)));
        }

        content.addView(AgentTheme.spacer(this, 28));
        content.addView(sectionTitle("Reader view", "Clean text, no feeds or pop-ups."));
        LinearLayout readerCard = AgentTheme.card(this);
        TextView body = AgentTheme.text(this, truncate(summary.displayText, 100_000), 16, Color.rgb(214, 224, 239));
        body.setLineSpacing(AgentTheme.dp(this, 3), 1.18f);
        body.setTextIsSelectable(true);
        Linkify.addLinks(body, Linkify.WEB_URLS);
        body.setLinkTextColor(AgentTheme.CYAN);
        body.setMovementMethod(LinkMovementMethod.getInstance());
        readerCard.addView(body);
        content.addView(readerCard);

        final String openUrl = decision.isUrl ? decision.normalizedInput :
                (!summary.sourceUrl.isEmpty() ? summary.sourceUrl : SmartRouter.searchUrl(decision.channel, decision.input));
        content.addView(AgentTheme.spacer(this, 18));
        TextView open = AgentTheme.button(this, "Open original source  ↗", true);
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openExternal(openUrl); }
        });
        content.addView(open);
        content.addView(AgentTheme.spacer(this, 24));
        setPage(scroll(content));
    }

    private View linkCard(final LocalSummarizer.Link link) {
        LinearLayout card = AgentTheme.card(this);
        card.setPadding(AgentTheme.dp(this, 15), AgentTheme.dp(this, 13),
                AgentTheme.dp(this, 15), AgentTheme.dp(this, 13));
        TextView title = AgentTheme.text(this, link.title + "  ↗", 14, AgentTheme.TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        card.addView(title);
        TextView host = AgentTheme.text(this, displayHost(link.url), 12, AgentTheme.MUTED);
        host.setPadding(0, AgentTheme.dp(this, 4), 0, 0);
        card.addView(host);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openExternal(link.url); }
        });
        LinearLayout.LayoutParams params = cardParams();
        card.setLayoutParams(params);
        return card;
    }

    private void showError(final SmartRouter.Decision decision, String message) {
        currentPage = PAGE_READER;
        navBar.setVisibility(View.GONE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(AgentTheme.dp(this, 26), AgentTheme.dp(this, 52),
                AgentTheme.dp(this, 26), AgentTheme.dp(this, 30));
        TextView icon = AgentTheme.text(this, "◎", 64, AgentTheme.RED);
        content.addView(icon, wrapCentered());
        TextView title = AgentTheme.text(this, "Reach interrupted", 28, AgentTheme.TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(title);
        content.addView(AgentTheme.spacer(this, 12));
        TextView detail = AgentTheme.text(this, message, 15, AgentTheme.MUTED);
        detail.setGravity(Gravity.CENTER);
        content.addView(detail);
        content.addView(AgentTheme.spacer(this, 25));
        TextView retry = AgentTheme.button(this, "Try again", true);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { runCommand(decision.input); }
        });
        content.addView(retry, matchWithMargins(0, 0));
        content.addView(AgentTheme.spacer(this, 10));
        TextView browser = AgentTheme.button(this, "Open in browser", false);
        browser.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                openExternal(decision.isUrl ? decision.normalizedInput
                        : SmartRouter.searchUrl(decision.channel, decision.input));
            }
        });
        content.addView(browser, matchWithMargins(0, 0));
        content.addView(AgentTheme.spacer(this, 10));
        TextView back = AgentTheme.button(this, "Back to Explore", false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showHome(); }
        });
        content.addView(back, matchWithMargins(0, 0));
        setPage(content);
    }

    private void showLibrary() {
        requestGeneration.incrementAndGet();
        currentPage = PAGE_LIBRARY;
        navBar.setVisibility(View.VISIBLE);
        selectNavigation(1);
        LinearLayout content = pageColumn();
        content.addView(appHeader("ON-DEVICE LIBRARY"));
        content.addView(AgentTheme.spacer(this, 28));
        TextView title = AgentTheme.text(this, "Your signal archive", 30, AgentTheme.TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);
        content.addView(AgentTheme.spacer(this, 8));
        content.addView(AgentTheme.text(this,
                "Return to recent briefs or keep the ones worth remembering.", 15, AgentTheme.MUTED));
        content.addView(AgentTheme.spacer(this, 18));

        LinearLayout filters = new LinearLayout(this);
        TextView all = AgentTheme.button(this, "All", !librarySavedOnly);
        TextView saved = AgentTheme.button(this, "★  Saved", librarySavedOnly);
        all.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                librarySavedOnly = false;
                showLibrary();
            }
        });
        saved.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                librarySavedOnly = true;
                showLibrary();
            }
        });
        filters.addView(all, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams savedParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        savedParams.leftMargin = AgentTheme.dp(this, 9);
        filters.addView(saved, savedParams);
        content.addView(filters);
        content.addView(AgentTheme.spacer(this, 18));

        List<ResearchEntry> entries = librarySavedOnly ? historyStore.saved() : historyStore.all();
        if (entries.isEmpty()) {
            LinearLayout empty = AgentTheme.card(this);
            TextView glyph = AgentTheme.text(this, librarySavedOnly ? "☆" : "◇", 42, AgentTheme.CYAN);
            glyph.setGravity(Gravity.CENTER);
            empty.addView(glyph);
            TextView emptyTitle = AgentTheme.text(this,
                    librarySavedOnly ? "No saved briefs yet" : "Your library is quiet", 20, AgentTheme.TEXT);
            emptyTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            emptyTitle.setGravity(Gravity.CENTER);
            emptyTitle.setPadding(0, AgentTheme.dp(this, 8), 0, AgentTheme.dp(this, 7));
            empty.addView(emptyTitle);
            TextView emptyText = AgentTheme.text(this,
                    librarySavedOnly ? "Tap Save on any research brief to keep it here."
                            : "Your completed research will appear here automatically.", 14, AgentTheme.MUTED);
            emptyText.setGravity(Gravity.CENTER);
            empty.addView(emptyText);
            content.addView(empty);
        } else {
            for (ResearchEntry entry : entries) content.addView(historyCard(entry));
        }
        content.addView(AgentTheme.spacer(this, 20));
        setPage(scroll(content));
    }

    private View historyCard(final ResearchEntry entry) {
        LinearLayout card = AgentTheme.card(this);
        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        TextView lane = AgentTheme.pill(this, entry.channel, AgentTheme.CYAN);
        lane.setTextSize(10);
        meta.addView(lane);
        TextView date = AgentTheme.text(this, formatDate(entry.createdAt), 11, AgentTheme.MUTED);
        date.setGravity(Gravity.END);
        meta.addView(date, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(meta);
        TextView title = AgentTheme.text(this, entry.title, 17, AgentTheme.TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, AgentTheme.dp(this, 12), 0, AgentTheme.dp(this, 7));
        card.addView(title);
        TextView excerpt = AgentTheme.text(this, entry.excerpt, 13, AgentTheme.MUTED);
        excerpt.setMaxLines(3);
        excerpt.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(excerpt);
        TextView action = AgentTheme.text(this, entry.saved ? "★  SAVED   ·   OPEN →" : "OPEN BRIEF →", 11,
                entry.saved ? AgentTheme.CYAN : AgentTheme.BLUE);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setLetterSpacing(0.08f);
        action.setPadding(0, AgentTheme.dp(this, 13), 0, 0);
        card.addView(action);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                runCommand(!entry.url.isEmpty() ? entry.url : entry.query);
            }
        });
        card.setLayoutParams(cardParams());
        return card;
    }

    private void showSettings() {
        requestGeneration.incrementAndGet();
        currentPage = PAGE_SETTINGS;
        navBar.setVisibility(View.VISIBLE);
        selectNavigation(2);
        LinearLayout content = pageColumn();
        content.addView(appHeader("LOCAL-FIRST"));
        content.addView(AgentTheme.spacer(this, 28));
        TextView title = AgentTheme.text(this, "Control center", 30, AgentTheme.TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);
        content.addView(AgentTheme.spacer(this, 8));
        content.addView(AgentTheme.text(this,
                "Transparent defaults. No account. No hidden data collection.", 15, AgentTheme.MUTED));

        content.addView(AgentTheme.spacer(this, 24));
        content.addView(settingCard("◉", "Private by design",
                "Queries are sent only when you tap Reach. Reading uses Jina Reader over HTTPS; summaries and your library stay on this device.",
                AgentTheme.GREEN));
        content.addView(settingCard("✦", "Smart routing",
                "Platform detection and source selection happen locally before any network request.", AgentTheme.CYAN));
        content.addView(settingCard("⌁", "Share into Agent Reach",
                "Use Android's Share menu from any browser or app, then choose Agent Reach for a clean brief.", AgentTheme.BLUE));

        content.addView(AgentTheme.spacer(this, 24));
        content.addView(sectionTitle("Data controls", historyStore.all().size() + " briefs stored locally"));
        TextView clear = AgentTheme.button(this, "Clear recent briefs", false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                historyStore.clearRecent();
                Toast.makeText(MainActivity.this, "Recent briefs cleared. Saved items were kept.", Toast.LENGTH_LONG).show();
                showSettings();
            }
        });
        content.addView(clear, matchWithMargins(12, 0));

        content.addView(AgentTheme.spacer(this, 26));
        LinearLayout about = AgentTheme.card(this);
        about.addView(AgentTheme.label(this, "ABOUT"));
        TextView version = AgentTheme.text(this, "Agent Reach for Android  1.0.0", 17, AgentTheme.TEXT);
        version.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        version.setPadding(0, AgentTheme.dp(this, 10), 0, AgentTheme.dp(this, 8));
        about.addView(version);
        about.addView(AgentTheme.text(this,
                "A dependency-free native companion for the open-source Agent Reach project. Supports Android 8.0 and newer.",
                14, AgentTheme.MUTED));
        TextView source = AgentTheme.button(this, "View source on GitHub  ↗", false);
        source.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                openExternal("https://github.com/shihab71284/Agent-Reach");
            }
        });
        about.addView(source, matchWithMargins(16, 0));
        content.addView(about);
        content.addView(AgentTheme.spacer(this, 20));
        setPage(scroll(content));
    }

    private View settingCard(String glyph, String title, String detail, int accent) {
        LinearLayout card = AgentTheme.card(this);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        TextView icon = AgentTheme.pill(this, glyph, accent);
        icon.setTextSize(18);
        row.addView(icon, new LinearLayout.LayoutParams(AgentTheme.dp(this, 48), AgentTheme.dp(this, 48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(AgentTheme.dp(this, 13), 0, 0, 0);
        TextView heading = AgentTheme.text(this, title, 16, AgentTheme.TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(heading);
        TextView text = AgentTheme.text(this, detail, 13, AgentTheme.MUTED);
        text.setPadding(0, AgentTheme.dp(this, 5), 0, 0);
        copy.addView(text);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);
        card.setLayoutParams(cardParams());
        return card;
    }

    private View privacyCard() {
        LinearLayout card = AgentTheme.card(this);
        card.setBackground(AgentTheme.background(Color.rgb(10, 27, 35), 20,
                Color.rgb(24, 80, 79), 1, this));
        TextView title = AgentTheme.text(this, "●  PRIVATE BY DEFAULT", 12, AgentTheme.GREEN);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLetterSpacing(0.12f);
        card.addView(title);
        TextView detail = AgentTheme.text(this,
                "No sign-in, ads, analytics, or remote history. Your smart briefs live only on this phone.",
                14, AgentTheme.TEXT);
        detail.setPadding(0, AgentTheme.dp(this, 9), 0, 0);
        card.addView(detail);
        return card;
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView heading = AgentTheme.text(this, title, 20, AgentTheme.TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        group.addView(heading);
        TextView sub = AgentTheme.text(this, subtitle, 13, AgentTheme.MUTED);
        sub.setPadding(0, AgentTheme.dp(this, 4), 0, AgentTheme.dp(this, 2));
        group.addView(sub);
        return group;
    }

    private void startVoiceSearch() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "What should Agent Reach research?");
        try {
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "Voice input is not available on this device.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                if (commandInput == null) showHome();
                commandInput.setText(matches.get(0));
                commandInput.setSelection(commandInput.length());
                runCommand(matches.get(0));
            }
        }
    }

    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        String value = "";
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            value = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            value = intent.getDataString();
        }
        if (value != null && !value.trim().isEmpty()) {
            showHome();
            commandInput.setText(value.trim());
            commandInput.setSelection(commandInput.length());
            runCommand(value.trim());
            intent.setAction(null);
        }
    }

    private void shareBrief(LocalSummarizer.Summary summary, SmartRouter.Decision decision) {
        StringBuilder text = new StringBuilder(summary.title).append("\n\n");
        for (String bullet : summary.bullets) text.append("• ").append(bullet).append("\n");
        String url = decision.isUrl ? decision.normalizedInput : summary.sourceUrl;
        if (!url.isEmpty()) text.append("\n").append(url);
        text.append("\n\nBriefed with Agent Reach");
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, summary.title);
        share.putExtra(Intent.EXTRA_TEXT, text.toString());
        startActivity(Intent.createChooser(share, "Share smart brief"));
    }

    private void openExternal(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "No browser could open this link.", Toast.LENGTH_LONG).show();
        }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    private LinearLayout pageColumn() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AgentTheme.dp(this, 20), AgentTheme.dp(this, 18),
                AgentTheme.dp(this, 20), AgentTheme.dp(this, 12));
        return content;
    }

    private ScrollView scroll(View content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void setPage(View view) {
        pageHost.removeAllViews();
        pageHost.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapCentered() {
        LinearLayout.LayoutParams params = wrap();
        params.gravity = Gravity.CENTER_HORIZONTAL;
        return params;
    }

    private LinearLayout.LayoutParams matchWithMargins(int topDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AgentTheme.dp(this, topDp);
        params.bottomMargin = AgentTheme.dp(this, bottomDp);
        return params;
    }

    private LinearLayout.LayoutParams cardParams() {
        return matchWithMargins(11, 0);
    }

    private String friendlyError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) return "Something unexpected happened. Check your connection and try again.";
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("unable to resolve host") || lower.contains("failed to connect") || lower.contains("network")) {
            return "Agent Reach could not find the network. Check your connection and try again.";
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "The source took too long to answer. It may be blocking automated readers.";
        }
        return message;
    }

    private String displayHost(String value) {
        try {
            String host = Uri.parse(value).getHost();
            return host == null ? value : host.replaceFirst("^www\\.", "");
        } catch (Exception ignored) {
            return value;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "\n\n[Reader view shortened for performance]";
    }

    private String formatDate(long timestamp) {
        return new SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    private String formatNumber(int value) {
        return String.format(Locale.getDefault(), "%,d", value);
    }

    @Override public void onBackPressed() {
        if (!PAGE_HOME.equals(currentPage)) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }
}
