package ai.victor.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.graphics.pdf.PdfRenderer;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final int BG = 0xff090d1c, PANEL = 0xff161c32;
    private int BLUE = 0xff59dff5, PURPLE = 0xffa48dff;
    private static final int TEXT = 0xfff1f4ff, MUTED = 0xff9caac5, GREEN = 0xff51e6a5, YELLOW = 0xffffd769;
    private static final int PICK = 30, CREATE = 31, MIC_PERMISSION = 32;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(), voiceWorker = Executors.newSingleThreadExecutor();
    private Store store;
    private final List<Store.Chat> chats = new ArrayList<>();
    private Store.Chat current;
    private FrameLayout shell;
    private LinearLayout chatList;
    private ScrollView scroll;
    private EditText input;
    private TextView status, mic, attachmentLabel;
    private Uri selectedUri;
    private String selectedName = "", pendingExport = "", pendingExportName = "";
    private SpeechRecognizer recognizer;
    private MediaPlayer player;
    private File playingFile;
    private final ArrayDeque<byte[]> audioQueue = new ArrayDeque<>();
    private boolean busy = false, listening = false, synthesizing = false;
    private int generation = 0, voiceGeneration = 0;
    private String primaryStatus = "Not tested", geminiStatus = "Not tested", voiceStatus = "Not tested";
    private String lastError = "No errors recorded this session.";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        store = new Store(this); applyAccent(); chats.addAll(store.load());
        if (chats.isEmpty()) { current = new Store.Chat(); chats.add(current); } else current = chats.get(0);
        if (!store.bool("onboarded", false)) showWelcome(); else showChat();
    }
    @Override protected void onDestroy() {
        generation++; stopAudio(); if (recognizer != null) recognizer.destroy(); io.shutdownNow(); voiceWorker.shutdownNow(); super.onDestroy();
    }
    private void applyAccent() { boolean violet = store.get("accent", "Cyan").equals("Violet"); BLUE = violet ? 0xffae92ff : 0xff59dff5; PURPLE = violet ? 0xff59dff5 : 0xffa48dff; }
    private int d(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable box(int color, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{color, color == PANEL ? 0xff11172b : color});
        g.setCornerRadius(d(radius)); if (stroke != 0) g.setStroke(d(1), stroke); return g;
    }
    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(color); t.setTextSize(sp); t.setIncludeFontPadding(false); return t;
    }
    private Button button(String s, boolean bright) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextSize(13); b.setTextColor(bright ? BG : TEXT);
        b.setBackground(box(bright ? BLUE : PANEL, bright ? 0 : 0xff35405d, 15)); b.setMinHeight(d(48));
        return b;
    }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : d(w), h < 0 ? h : d(h)); }
    private void pad(View v, int x, int y) { v.setPadding(d(x), d(y), d(x), d(y)); }
    private void gap(LinearLayout l, int height) { l.addView(new View(this), lp(1, height)); }
    private void title(LinearLayout l, String name, String caption) {
        TextView a = text(name, 21, TEXT); a.setLetterSpacing(.08f); l.addView(a);
        if (caption != null) { gap(l, 5); l.addView(text(caption, 13, MUTED)); }
        gap(l, 17);
    }
    private void setupShell() {
        shell = new FrameLayout(this); shell.setBackgroundColor(BG);
        shell.addView(new View(this) {
            final Paint p = new Paint(3);
            @Override protected void onDraw(Canvas c) {
                float w = getWidth(), h = getHeight();
                p.setShader(new RadialGradient(w * .88f, h * .15f, w * .87f, new int[]{0x302b58a7, 0x082b58a7, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, h, p);
                p.setShader(new RadialGradient(0, h * .75f, w * .8f, new int[]{0x252c70a2, 0x082c70a2, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, h, p);
                p.setShader(null);
            }
        });
        setContentView(shell);
    }
    private void showWelcome() {
        setupShell(); LinearLayout page = vertical(); page.setGravity(Gravity.CENTER); pad(page, 28, 25);
        shell.addView(page, new FrameLayout.LayoutParams(-1, -1));
        TextView glyph = text("◇", 85, BLUE); glyph.setGravity(Gravity.CENTER); page.addView(glyph, lp(-1, 115));
        TextView hero = text("VICTOR AI", 36, TEXT); hero.setGravity(Gravity.CENTER); hero.setLetterSpacing(.20f); page.addView(hero);
        gap(page, 12); TextView sub = text("Your personal AI assistant.", 18, MUTED); sub.setGravity(Gravity.CENTER); page.addView(sub);
        gap(page, 36); TextView desc = text("A more natural way to think, create and explore. Connect your own services to begin.", 14, MUTED); desc.setGravity(Gravity.CENTER); page.addView(desc);
        gap(page, 45); Button configure = button("CONNECT SERVICES  →", true); page.addView(configure, lp(-1, 54));
        configure.setOnClickListener(v -> { store.setBool("onboarded", true); showSettings(); });
        gap(page, 12); Button skip = button("Explore without connecting", false); page.addView(skip, lp(-1, 50));
        skip.setOnClickListener(v -> { store.setBool("onboarded", true); showChat(); });
    }
    private void showChat() {
        setupShell(); LinearLayout page = vertical(); shell.addView(page, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); pad(top, 14, 12); page.addView(top, lp(-1, 80));
        TextView menu = text("☰", 26, BLUE); menu.setGravity(Gravity.CENTER); menu.setContentDescription("Open navigation"); top.addView(menu, lp(44, 48)); menu.setOnClickListener(v -> navigation());
        LinearLayout brand = vertical(); LinearLayout.LayoutParams brandLp = lp(0, -2); brandLp.weight = 1; top.addView(brand, brandLp);
        TextView name = text("VICTOR  AI", 21, TEXT); name.setLetterSpacing(.14f); brand.addView(name);
        status = text("", 11, MUTED); brand.addView(status); refreshStatus();
        TextView add = text("＋", 30, BLUE); add.setGravity(Gravity.CENTER); add.setContentDescription("New conversation"); top.addView(add, lp(44, 48)); add.setOnClickListener(v -> newChat());
        TextView settings = text("⚙", 25, MUTED); settings.setGravity(Gravity.CENTER); settings.setContentDescription("Settings"); top.addView(settings, lp(44, 48)); settings.setOnClickListener(v -> showSettings());
        View line = new View(this); line.setBackgroundColor(0xff2b3654); page.addView(line, lp(-1, 1));
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams sc = lp(-1, 0); sc.weight = 1; page.addView(scroll, sc);
        chatList = vertical(); pad(chatList, 16, 18); scroll.addView(chatList); renderMessages();
        LinearLayout compose = vertical(); pad(compose, 12, 9); compose.setBackground(box(0xff10162a, 0xff2b3654, 23));
        LinearLayout.LayoutParams cp = lp(-1, -2); cp.setMargins(d(9), d(3), d(9), d(9)); page.addView(compose, cp);
        attachmentLabel = text("", 12, BLUE); attachmentLabel.setVisibility(View.GONE); compose.addView(attachmentLabel);
        attachmentLabel.setOnClickListener(v -> { selectedUri = null; selectedName = ""; attachmentLabel.setVisibility(View.GONE); });
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); compose.addView(row, lp(-1, -2));
        TextView attach = text("＋", 26, MUTED); attach.setGravity(Gravity.CENTER); attach.setContentDescription("Attach a file"); row.addView(attach, lp(40, 52)); attach.setOnClickListener(v -> pickFile());
        input = new EditText(this); input.setTextColor(TEXT); input.setHintTextColor(MUTED); input.setTextSize(15); input.setHint("Ask VICTOR anything…");
        input.setSingleLine(false); input.setMaxLines(4); input.setBackgroundColor(Color.TRANSPARENT); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams inp = lp(0, -2); inp.weight = 1; row.addView(input, inp);
        mic = text("◉", 28, BLUE); mic.setGravity(Gravity.CENTER); mic.setContentDescription("Speak to VICTOR"); row.addView(mic, lp(48, 52)); mic.setOnClickListener(v -> toggleMic());
        TextView send = text("➤", 26, BLUE); send.setGravity(Gravity.CENTER); send.setContentDescription("Send message"); row.addView(send, lp(44, 52)); send.setOnClickListener(v -> sendMessage());
    }
    private void refreshStatus() {
        if (status == null) return;
        String line = !Services.online(this) ? "● OFFLINE · History available" : busy ? "◌ Working on it…" :
                primaryStatus.startsWith("Connected") ? "● PRIMARY ONLINE" : geminiStatus.startsWith("Connected") ? "● GEMINI READY" : "○ CONNECT IN SETTINGS";
        status.setText(line); status.setTextColor(line.contains("ONLINE") || line.contains("READY") ? GREEN : line.contains("OFFLINE") ? YELLOW : MUTED);
    }
    private void newChat() {
        if (busy) { toast("Wait for the current response to finish."); return; }
        stopAudio(); selectedUri = null; current = new Store.Chat(); chats.add(0, current); saveChats(); showChat();
    }
    private void saveChats() { try { store.save(chats); } catch (Exception e) { toast("Could not save chat history."); } }
    private void renderMessages() {
        if (chatList == null) return; chatList.removeAllViews();
        if (current.messages.isEmpty()) {
            gap(chatList, 60); TextView orb = text("✧", 65, PURPLE); orb.setGravity(Gravity.CENTER); chatList.addView(orb);
            TextView hello = text("Good to have you here.", 23, TEXT); hello.setGravity(Gravity.CENTER); chatList.addView(hello);
            gap(chatList, 10); TextView hint = text("Ask a question, speak naturally, or bring an idea to life.", 14, MUTED); hint.setGravity(Gravity.CENTER); chatList.addView(hint);
        } else for (Store.Message m : current.messages) addBubble(m, false);
        scrollDown();
    }
    private void scrollDown() { if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN)); }
    private void addBubble(Store.Message m, boolean animate) {
        if (chatList == null) return;
        boolean user = m.role.equals("user"), error = m.role.equals("error");
        LinearLayout outer = vertical(); LinearLayout.LayoutParams outerLp = lp(-1, -2); outerLp.bottomMargin = d(16); chatList.addView(outer, outerLp);
        TextView who = text(user ? "YOU" : error ? "NOTICE" : "VICTOR  ✧", 11, user ? BLUE : error ? YELLOW : PURPLE);
        who.setLetterSpacing(.14f); who.setGravity(user ? Gravity.RIGHT : Gravity.LEFT); outer.addView(who); gap(outer, 7);
        LinearLayout bubble = vertical(); pad(bubble, 16, 14); bubble.setBackground(box(user ? 0xff1d3151 : PANEL, error ? 0xff9e844e : user ? 0xff365577 : 0xff343b66, 18));
        LinearLayout.LayoutParams bp = lp(-2, -2); bp.gravity = user ? Gravity.RIGHT : Gravity.LEFT; bp.width = Math.min(d(420), (int)(getResources().getDisplayMetrics().widthPixels * .87f)); outer.addView(bubble, bp);
        TextView body = text(m.text, 15, TEXT); body.setTextIsSelectable(true); body.setLineSpacing(d(3), 1f); body.setTag("body"); bubble.addView(body, lp(-1, -2));
        TextView time = text(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(m.time)), 11, MUTED);
        LinearLayout.LayoutParams tp = lp(-1, -2); tp.topMargin = d(10); bubble.addView(time, tp);
        HorizontalScrollView actionsScroll = new HorizontalScrollView(this); actionsScroll.setHorizontalScrollBarEnabled(false); outer.addView(actionsScroll, lp(-1, 43));
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL); actionsScroll.addView(actions);
        action(actions, "COPY", () -> { ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("VICTOR message", m.text)); toast("Copied"); });
        action(actions, "DELETE", () -> new AlertDialog.Builder(this).setTitle("Delete message?").setPositiveButton("Delete", (a,b) -> {
            current.messages.remove(m); saveChats(); renderMessages(); }).setNegativeButton("Cancel", null).show());
        if (!user && !error) {
            action(actions, "PLAY / STOP", () -> { if (player != null && player.isPlaying()) stopAudio(); else speakText(m.text); });
            Matcher matcher = Pattern.compile("```([^\\n`]*)\\n([\\s\\S]*?)\\n?```").matcher(m.text);
            int block = 0; while (matcher.find() && block++ < 6) {
                String lang = matcher.group(1).trim().split("\\s+")[0], code = matcher.group(2);
                String filename = "victor-code" + extension(lang); int number = block;
                action(actions, "COPY CODE " + number, () -> { ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Code", code)); toast("Code copied"); });
                action(actions, "SAVE CODE " + number, () -> export(code, filename));
            }
        }
        if (animate && store.bool("animations", true)) { AlphaAnimation fade = new AlphaAnimation(0f, 1f); fade.setDuration(160); outer.startAnimation(fade); }
        scrollDown();
    }
    private String extension(String language) {
        switch(language.toLowerCase(Locale.ROOT)) {
            case "python": case "py": return ".py"; case "html": return ".html"; case "css": return ".css";
            case "js": case "javascript": return ".js"; case "java": return ".java"; case "json": return ".json";
            case "sh": case "bash": return ".sh"; case "xml": return ".xml"; case "kotlin": case "kt": return ".kt";
            case "sql": return ".sql"; case "typescript": case "ts": return ".ts"; default: return ".txt";
        }
    }
    private void action(LinearLayout row, String label, Runnable click) {
        TextView t = text(label, 10, MUTED); t.setGravity(Gravity.CENTER_VERTICAL); t.setMinHeight(d(40)); t.setPadding(d(6), 0, d(9), 0);
        row.addView(t); t.setOnClickListener(v -> click.run());
    }
    private void sendMessage() {
        if (busy) { toast("VICTOR is still responding."); return; }
        if (input == null) return;
        String prompt = input.getText().toString().trim(); Uri attachmentUri = selectedUri;
        if (prompt.isEmpty() && attachmentUri == null) return;
        if (!Services.online(this)) { toast("No internet connection. Your history is still available."); refreshStatus(); return; }
        boolean search = prompt.toLowerCase(Locale.ROOT).matches("^(search (the )?web|look up online|search online|web search)( for)?[ :].*");
        if (search && (!store.bool("gemini_enabled", false) || !store.hasKey("gemini"))) { toast("Live web search requires Gemini enabled in Settings."); return; }
        boolean visual = attachmentUri != null && (getContentResolver().getType(attachmentUri) != null &&
                (getContentResolver().getType(attachmentUri).startsWith("image/") || getContentResolver().getType(attachmentUri).equals("application/pdf")));
        if (visual && (!store.bool("gemini_enabled", false) || !store.hasKey("gemini"))) { toast("Images and PDFs require Gemini backup enabled and configured."); return; }
        if (!visual && !store.hasKey("primary") && (!store.bool("gemini_enabled", false) || !store.hasKey("gemini"))) { toast("Connect an AI provider in Settings."); return; }
        final String name = selectedName;
        selectedUri = null; selectedName = ""; if (attachmentLabel != null) attachmentLabel.setVisibility(View.GONE);
        input.setText(""); busy = true; refreshStatus();
        Store.Chat chat = current; Store.Message user = new Store.Message("user", prompt.isEmpty() ? "Please analyze the attached file." : prompt);
        if (attachmentUri != null) user.text += "\n📎 " + name;
        chat.messages.add(user); if (chat.messages.size() == 1) chat.title = prompt.isEmpty() ? name : prompt.substring(0, Math.min(42, prompt.length()));
        chat.updated = System.currentTimeMillis(); saveChats(); renderMessages();
        Store.Message response = new Store.Message("assistant", "Thinking…"); chat.messages.add(response); addBubble(response, true);
        earlySpoken = "";
        final int task = ++generation;
        io.execute(() -> {
            StringBuilder partial = new StringBuilder(); boolean fallback = false;
            try {
                Services.Attachment attachment = attachmentUri == null ? null : loadAttachment(attachmentUri, name);
                if (attachment != null) {
                    if (attachment.visual()) {
                        user.text += attachment.mime.equals("image/jpeg") && name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? " (first pages rendered)" : "";
                    } else { user.attachment = "Attachment " + attachment.name + ":\n" + attachment.text; }
                    ui.post(() -> { saveChats(); renderMessages(); });
                }
                Services.TextSink sink = token -> {
                    partial.append(token);
                    String latest = partial.toString();
                    if (task == generation) ui.post(() -> {
                        if (task != generation || current != chat) return;
                        response.text = latest; updateLastBubble(response);
                        maybeEarlySpeak(latest, task);
                    });
                };
                String key = store.key("primary"), gkey = store.key("gemini");
                if (attachment != null && attachment.visual() || search) {
                    fallback = true; Services.gemini(chat, gkey, store.get("gemini_model", "gemini-2.5-flash"), attachment, true, search, sink);
                } else if (!key.isEmpty()) {
                    try { Services.primary(chat, store.get("endpoint", "https://api.gonkarouter.io/v1"), key,
                            store.get("model", "zai-org/GLM-5.3-Flash"), true, sink); }
                    catch (Exception first) {
                        if (partial.length() > 0 || !store.bool("gemini_enabled", false) || gkey.isEmpty()) throw first;
                        fallback = true;
                        primaryStatus = Services.explain(first);
                        ui.post(() -> { if (task == generation) { response.text = "Switching to Gemini backup…"; updateLastBubble(response); } });
                        Services.gemini(chat, gkey, store.get("gemini_model", "gemini-2.5-flash"), null, true, false, sink);
                    }
                } else if (store.bool("gemini_enabled", false) && !gkey.isEmpty()) {
                    fallback = true; Services.gemini(chat, gkey, store.get("gemini_model", "gemini-2.5-flash"), null, true, false, sink);
                } else throw new Services.ApiError("No AI service configured.", 0);
                final boolean usedBackup = fallback;
                ui.post(() -> {
                    if (task != generation) return;
                    if (usedBackup) geminiStatus = "Connected"; else primaryStatus = "Connected";
                    response.text = partial.toString();
                    if (usedBackup) response.text += "\n\n— Gemini backup";
                    finishSpeak(partial.toString(), task); busy = false; refreshStatus(); saveChats(); renderMessages();
                });
            } catch (Exception error) {
                String message = Services.explain(error); lastError = message;
                final boolean failedBackup = fallback;
                ui.post(() -> {
                    if (task != generation) return;
                    if (failedBackup) geminiStatus = message; else primaryStatus = message;
                    if (partial.length() == 0) { response.role = "error"; response.text = message; }
                    else { response.text = partial + "\n\n[Response interrupted: " + message + "]"; }
                    busy = false; refreshStatus(); saveChats(); renderMessages();
                });
            }
        });
    }
    private String earlySpoken = "";
    private void maybeEarlySpeak(String text, int task) {
        if (task != generation || !store.bool("voice_enabled", true) || !store.bool("auto_speak", false) || !store.hasKey("eleven") || earlySpoken.length() > 0) return;
        Matcher end = Pattern.compile("[.!?]\\s").matcher(text);
        if (end.find() && end.end() >= 65 && end.end() <= 300) { earlySpoken = text.substring(0, end.end()); enqueueSpeech(earlySpoken); }
    }
    private void finishSpeak(String text, int task) {
        if (task != generation) return;
        if (store.bool("voice_enabled", true) && store.bool("auto_speak", false) && store.hasKey("eleven")) {
            String rest = text.substring(Math.min(earlySpoken.length(), text.length())).trim(); if (!rest.isEmpty()) enqueueSpeech(rest);
        }
        earlySpoken = "";
    }
    private void updateLastBubble(Store.Message m) {
        if (chatList == null || current == null || !current.messages.contains(m)) return;
        // Updating just the bubble avoids reflowing the entire conversation for every streamed token.
        int index = current.messages.indexOf(m); if (index < chatList.getChildCount()) {
            View outer = chatList.getChildAt(index); if (outer instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout)outer;
                if (layout.getChildCount() > 2 && layout.getChildAt(2) instanceof LinearLayout) {
                    LinearLayout bubble = (LinearLayout)layout.getChildAt(2);
                    if (bubble.getChildCount() > 0 && bubble.getChildAt(0) instanceof TextView) {
                        ((TextView)bubble.getChildAt(0)).setText(m.text); scrollDown(); return;
                    }
                }
            }
        }
        renderMessages();
    }
    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "text/html", "text/css", "text/javascript", "application/javascript", "application/json", "text/csv", "text/markdown", "application/pdf", "image/png", "image/jpeg", "image/webp", "image/gif", "application/octet-stream"});
        startActivityForResult(i, PICK);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (result != RESULT_OK || data == null) return;
        if (request == PICK && data.getData() != null) {
            selectedUri = data.getData(); selectedName = fileName(selectedUri);
            try { getContentResolver().takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            if (attachmentLabel != null) { attachmentLabel.setText("📎 " + selectedName + "   × remove"); attachmentLabel.setVisibility(View.VISIBLE); }
        } else if (request == CREATE && data.getData() != null) {
            try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData(), "w")) {
                if (out == null) throw new Exception(); out.write(pendingExport.getBytes(java.nio.charset.StandardCharsets.UTF_8)); toast("Saved " + pendingExportName);
            } catch (Exception e) { toast("Could not save file: " + e.getClass().getSimpleName()); }
            pendingExport = "";
        }
    }
    private String fileName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) { String name = c.getString(0); if (name != null) return name; }
        } catch (Exception ignored) { } return "attachment";
    }
    private Services.Attachment loadAttachment(Uri uri, String name) throws Exception {
        Services.Attachment a = new Services.Attachment(); a.name = name;
        String mime = getContentResolver().getType(uri); a.mime = mime == null ? "application/octet-stream" : mime;
        String lower = name.toLowerCase(Locale.ROOT);
        if (a.mime.startsWith("image/")) {
            if (!a.mime.matches("image/(jpeg|png|webp|gif)")) throw new Services.ApiError("This image type is not supported. Use JPEG, PNG, WebP or GIF.", 0);
            a.image = readLimited(uri, 4_000_000); return a;
        }
        if (a.mime.equals("application/pdf") || lower.endsWith(".pdf")) {
            a.mime = "image/jpeg"; a.image = renderPdf(uri); return a;
        }
        if (!(a.mime.startsWith("text/") || a.mime.equals("application/json") || a.mime.equals("application/javascript") || a.mime.equals("application/octet-stream") &&
                lower.matches(".*\\.(txt|md|html|css|js|ts|json|csv|xml|py|java|kt|sh|sql)$")))
            throw new Services.ApiError("Unsupported file type. Select text, code, CSV, JSON, PDF or an image.", 0);
        byte[] content = readLimited(uri, 110_000);
        for (byte b : content) if (b == 0) throw new Services.ApiError("Binary file cannot be sent as text.", 0);
        a.text = new String(content, java.nio.charset.StandardCharsets.UTF_8); return a;
    }
    private byte[] readLimited(Uri uri, int max) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new Services.ApiError("Cannot open selected file.", 0);
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) { if (out.size() + n > max) throw new Services.ApiError("File is too large. Limit: " + max/1000 + " KB.", 0); out.write(buf, 0, n); }
            return out.toByteArray();
        }
    }
    private byte[] renderPdf(Uri uri) throws Exception {
        try (ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "r")) {
            if (fd == null) throw new Services.ApiError("Cannot read PDF.", 0);
            try (PdfRenderer renderer = new PdfRenderer(fd)) {
                if (renderer.getPageCount() == 0) throw new Services.ApiError("PDF has no pages.", 0);
                int count = Math.min(3, renderer.getPageCount()); List<Bitmap> pages = new ArrayList<>(); int height = 0;
                for (int i = 0; i < count; i++) {
                    try (PdfRenderer.Page page = renderer.openPage(i)) {
                        int w = 900, h = Math.max(1, Math.min(1500, (int)(900f * page.getHeight() / page.getWidth())));
                        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.eraseColor(Color.WHITE);
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); pages.add(b); height += h;
                    }
                }
                Bitmap combined = Bitmap.createBitmap(900, height, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(combined); canvas.drawColor(Color.WHITE);
                int y = 0; for (Bitmap page : pages) { canvas.drawBitmap(page, 0, y, null); y += page.getHeight(); page.recycle(); }
                ByteArrayOutputStream out = new ByteArrayOutputStream(); combined.compress(Bitmap.CompressFormat.JPEG, 77, out); combined.recycle();
                if (out.size() > 4_000_000) throw new Services.ApiError("Rendered PDF is too large. Try a shorter document.", 0);
                return out.toByteArray();
            }
        }
    }
    private void export(String content, String name) {
        pendingExport = content; pendingExportName = name;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, name); startActivityForResult(i, CREATE);
    }
    private void toggleMic() {
        if (listening) { if (recognizer != null) recognizer.stopListening(); listening = false; updateMic(); return; }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { toast("No speech recognition service installed on this device."); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("Microphone access").setMessage("VICTOR needs microphone access to turn your speech into text. Speech may be processed by your device's speech provider.")
                    .setPositiveButton("Continue", (a,b) -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION)).setNegativeButton("Cancel", null).show(); return;
        }
        startListening();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == MIC_PERMISSION) { if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startListening(); else toast("Microphone permission denied."); }
    }
    private void startListening() {
        stopAudio();
        if (recognizer != null) recognizer.destroy();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle b) { listening = true; updateMic(); }
            @Override public void onBeginningOfSpeech() { listening = true; updateMic(); }
            @Override public void onRmsChanged(float rms) { if (listening && mic != null) mic.setScaleX(1f + Math.min(.3f, Math.max(0, rms) / 35f)); }
            @Override public void onBufferReceived(byte[] b) { }
            @Override public void onEndOfSpeech() { listening = false; updateMic(); }
            @Override public void onError(int code) { listening = false; updateMic(); toast("Speech recognition failed (code " + code + "). Try again."); }
            @Override public void onResults(Bundle b) {
                listening = false; updateMic(); ArrayList<String> values = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (values != null && !values.isEmpty() && input != null) { input.setText(values.get(0)); sendMessage(); }
                else toast("No speech recognized.");
            }
            @Override public void onPartialResults(Bundle b) {
                ArrayList<String> values = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (values != null && !values.isEmpty() && input != null) input.setText(values.get(0));
            }
            @Override public void onEvent(int type, Bundle b) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        listening = true; updateMic();
        try { recognizer.startListening(intent); } catch (Exception e) { listening = false; updateMic(); toast("Could not start speech recognition."); }
    }
    private void updateMic() {
        if (mic == null) return;
        boolean speaking = player != null && player.isPlaying();
        mic.setTextColor(listening ? YELLOW : speaking ? GREEN : BLUE);
        mic.setContentDescription(listening ? "Listening, tap to stop" : speaking ? "VICTOR is speaking" : "Speak to VICTOR");
        mic.setScaleX(1f); mic.setScaleY(1f);
        if (listening || speaking) {
            // The pulse runs only while recognition is active or audio is actually playing.
            mic.animate().scaleX(1.14f).scaleY(1.14f).setDuration(180).withEndAction(() -> {
                if (listening || player != null && player.isPlaying()) mic.animate().scaleX(1f).scaleY(1f).setDuration(180).withEndAction(this::updateMic).start();
            }).start();
        }
    }
    private void speakText(String text) {
        if (!store.bool("voice_enabled", true)) { toast("Enable voice responses in Settings."); return; }
        stopAudio(); enqueueSpeech(text);
    }
    private void enqueueSpeech(String text) {
        if (text.trim().isEmpty()) return;
        final int id = voiceGeneration;
        // Split long ElevenLabs input without delaying the first piece.
        for (int pos = 0; pos < text.length(); pos += 850) {
            String chunk = text.substring(pos, Math.min(text.length(), pos + 850));
            voiceWorker.execute(() -> {
                if (id != voiceGeneration) return;
                try {
                    byte[] data = Services.speech(store.key("eleven"), store.get("voice_id", ""), chunk,
                            Float.parseFloat(store.get("speed", "1.0")));
                    voiceStatus = "Connected";
                    ui.post(() -> { if (id == voiceGeneration) { audioQueue.add(data); playNext(); } });
                } catch (Exception e) {
                    String message = Services.explain(e); lastError = message; voiceStatus = message;
                    ui.post(() -> { if (id == voiceGeneration) toast("Voice unavailable: " + message); });
                }
            });
        }
    }
    private void playNext() {
        if (player != null || audioQueue.isEmpty()) return;
        byte[] data = audioQueue.poll();
        try {
            File audio = File.createTempFile("victor_", ".mp3", getCacheDir());
            try (FileOutputStream out = new FileOutputStream(audio)) { out.write(data); }
            playingFile = audio; player = new MediaPlayer(); player.setDataSource(audio.getAbsolutePath());
            player.setOnCompletionListener(mp -> { releasePlayer(); playNext(); });
            player.setOnErrorListener((mp, what, extra) -> { releasePlayer(); toast("Audio playback failed."); playNext(); return true; });
            player.setOnPreparedListener(mp -> { mp.start(); updateMic(); }); player.prepareAsync();
        } catch (Exception e) { releasePlayer(); toast("Could not play generated audio."); playNext(); }
    }
    private void releasePlayer() {
        if (player != null) { try { player.release(); } catch (Exception ignored) { } player = null; }
        if (playingFile != null) { playingFile.delete(); playingFile = null; } updateMic();
    }
    private void stopAudio() { voiceGeneration++; audioQueue.clear(); releasePlayer(); }
    private LinearLayout screen(String heading, String subtitle) {
        setupShell(); LinearLayout page = vertical(); shell.addView(page, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout top = new LinearLayout(this); pad(top, 17, 16); top.setGravity(Gravity.CENTER_VERTICAL); page.addView(top, lp(-1, 70));
        TextView back = text("‹", 32, BLUE); back.setGravity(Gravity.CENTER); back.setContentDescription("Back"); top.addView(back, lp(46, 46)); back.setOnClickListener(v -> showChat());
        TextView label = text(heading, 21, TEXT); label.setLetterSpacing(.08f); top.addView(label);
        ScrollView sc = new ScrollView(this); sc.setFillViewport(true); sc.setVerticalScrollBarEnabled(false); page.addView(sc, lp(-1, -1));
        LinearLayout inner = vertical(); pad(inner, 20, 22); sc.addView(inner);
        if (subtitle != null) { inner.addView(text(subtitle, 14, MUTED)); gap(inner, 23); }
        return inner;
    }
    private void card(LinearLayout parent, LinearLayout content) {
        content.setBackground(box(PANEL, 0xff303b5e, 20)); pad(content, 18, 18);
        LinearLayout.LayoutParams p = lp(-1, -2); p.bottomMargin = d(16); parent.addView(content, p);
    }
    private void section(LinearLayout parent, String heading, String subtitle, Runnable action) {
        LinearLayout row = vertical(); card(parent, row);
        TextView a = text(heading + "  ↗", 16, TEXT); row.addView(a);
        gap(row, 7); row.addView(text(subtitle, 13, MUTED)); row.setOnClickListener(v -> action.run()); row.setMinimumHeight(d(76));
    }
    private void showSettings() {
        LinearLayout page = screen("SETTINGS", "Everything connected, on your terms.");
        title(page, "CONNECTIONS", "Configure live services and models");
        section(page, "AI connection", "GonkaRouter primary · Gemini backup · Tests", this::showAISettings);
        section(page, "Voice", "ElevenLabs · voices · playback", this::showVoiceSettings);
        gap(page, 10); title(page, "APP", "Local preferences");
        LinearLayout app = vertical(); card(page, app);
        Button theme = button("ACCENT THEME · " + store.get("accent", "Cyan"), false); app.addView(theme, lp(-1, 48));
        theme.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Accent theme").setItems(new String[]{"Cyan", "Violet"}, (a, which) -> { store.put("accent", which == 0 ? "Cyan" : "Violet"); applyAccent(); showSettings(); }).show());
        CheckBox animations = checkbox("Message animations", store.bool("animations", true)); app.addView(animations);
        animations.setOnCheckedChangeListener((b,v) -> store.setBool("animations", v));
        gap(app, 10); Button clear = button("Clear all local conversations", false); app.addView(clear, lp(-1, 50));
        clear.setOnClickListener(v -> confirmClear());
        gap(page, 10); title(page, "DIAGNOSTICS", "Real test results, never assumed");
        LinearLayout diagnostics = vertical(); card(page, diagnostics); diagnostics.addView(text("Internet   " + (Services.online(this) ? "Available" : "Offline"), 14, Services.online(this) ? GREEN : YELLOW));
        gap(diagnostics, 9); diagnostics.addView(text("Primary AI   " + primaryStatus, 13, MUTED));
        gap(diagnostics, 8); diagnostics.addView(text("Gemini   " + (store.bool("gemini_enabled", false) ? geminiStatus : "Disabled"), 13, MUTED));
        gap(diagnostics, 8); diagnostics.addView(text("ElevenLabs   " + voiceStatus, 13, MUTED));
        gap(diagnostics, 12); TextView details = text("Show recent error  ▾", 13, BLUE); diagnostics.addView(details);
        details.setOnClickListener(v -> details.setText("Recent error: " + lastError));
        gap(page, 12); Button done = button("BACK TO CHAT", true); page.addView(done, lp(-1, 50)); done.setOnClickListener(v -> showChat());
    }
    private EditText field(LinearLayout panel, String label, String value, boolean secret) {
        TextView heading = text(label, 12, MUTED); panel.addView(heading); gap(panel, 7);
        EditText edit = new EditText(this); edit.setSingleLine(true); edit.setTextSize(15); edit.setTextColor(TEXT); edit.setHintTextColor(MUTED);
        edit.setBackground(box(0xff0e1428, 0xff374464, 12)); edit.setPadding(d(13), 0, d(13), 0);
        if (secret) { edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); edit.setHint("Enter new key to replace saved key"); }
        else { edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); edit.setText(value); }
        panel.addView(edit, lp(-1, 50)); gap(panel, 15); return edit;
    }
    private CheckBox checkbox(String label, boolean value) {
        CheckBox c = new CheckBox(this); c.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE)); c.setText(label); c.setTextColor(TEXT); c.setTextSize(14); c.setChecked(value); c.setMinHeight(d(48)); return c;
    }
    private void setResult(TextView view, String message, boolean good) {
        view.setText((good ? "● Connected · " : "● Needs attention · ") + message);
        view.setTextColor(good ? GREEN : YELLOW);
    }
    private void showAISettings() {
        LinearLayout page = screen("AI CONNECTION", "Live requests. Your credentials stay on this device.");
        LinearLayout primary = vertical(); card(page, primary); title(primary, "PRIMARY  /  GONKAROUTER", "OpenAI-compatible streaming chat");
        EditText endpoint = field(primary, "HTTPS API BASE URL", store.get("endpoint", "https://api.gonkarouter.io/v1"), false);
        EditText pkey = field(primary, "API KEY  ·  " + (store.hasKey("primary") ? "Saved securely" : "Not saved"), "", true);
        EditText model = field(primary, "MODEL ID", store.get("model", "zai-org/GLM-5.3-Flash"), false);
        TextView pstatus = text("Primary AI: " + primaryStatus, 13, MUTED); primary.addView(pstatus); gap(primary, 14);
        Button save = button("SAVE PRIMARY SETTINGS", true); primary.addView(save, lp(-1, 50));
        save.setOnClickListener(v -> {
            String url = endpoint.getText().toString().trim();
            try { Services.endpoint(url); } catch (Exception e) { toast(Services.explain(e)); return; }
            if (model.getText().toString().trim().isEmpty()) { toast("Enter a model ID."); return; }
            try {
                if (!pkey.getText().toString().trim().isEmpty()) { store.key("primary", pkey.getText().toString().trim()); pkey.setText(""); }
                store.put("endpoint", url); store.put("model", model.getText().toString().trim());
                primaryStatus = "Not tested"; pstatus.setText("Primary AI: Not tested"); toast("Saved. Run a connection test.");
            } catch (Exception e) { toast("Could not secure the API key on this device."); }
        });
        gap(primary, 10); Button test = button("TEST PRIMARY · REAL API CALL", false); primary.addView(test, lp(-1, 50));
        test.setOnClickListener(v -> {
            save.performClick(); if (!Services.online(this)) { setResult(pstatus, "No internet connection.", false); return; }
            pstatus.setText("Testing with a real chat request…"); test.setEnabled(false);
            io.execute(() -> {
                boolean ok = false; String result;
                try { String reply = Services.testPrimary(store.get("endpoint", "https://api.gonkarouter.io/v1"), store.key("primary"), store.get("model", ""));
                    result = "Model replied: " + reply.substring(0, Math.min(65, reply.length())); ok = true;
                } catch (Exception e) { result = Services.explain(e); lastError = result; }
                boolean passed = ok; String message = result;
                ui.post(() -> { primaryStatus = passed ? "Connected" : message; setResult(pstatus, message, passed); test.setEnabled(true); refreshStatus(); });
            });
        });
        gap(primary, 10); Button models = button("LOAD AVAILABLE MODELS", false); primary.addView(models, lp(-1, 50));
        models.setOnClickListener(v -> {
            models.setEnabled(false);
            io.execute(() -> {
                try { List<String> names = Services.primaryModels(store.get("endpoint", "https://api.gonkarouter.io/v1"), store.key("primary"));
                    ui.post(() -> { models.setEnabled(true); if (names.isEmpty()) { toast("Provider returned no models; enter an ID manually."); return; }
                        new AlertDialog.Builder(this).setTitle("Available models").setItems(names.toArray(new String[0]), (a, which) -> model.setText(names.get(which))).show(); });
                } catch (Exception e) { ui.post(() -> { models.setEnabled(true); toast("Model list unavailable: " + Services.explain(e) + " Enter a model ID manually."); }); }
            });
        });
        gap(primary, 8); Button remove = button("REMOVE PRIMARY KEY", false); primary.addView(remove, lp(-1, 48));
        remove.setOnClickListener(v -> { store.clearKey("primary"); primaryStatus = "Not connected"; pstatus.setText(primaryStatus); toast("Primary key removed."); });

        LinearLayout backup = vertical(); card(page, backup); title(backup, "BACKUP  /  GEMINI", "Used only when primary fails before any text, or for images and PDF pages");
        CheckBox enable = checkbox("Enable Gemini fallback", store.bool("gemini_enabled", false)); backup.addView(enable);
        EditText gkey = field(backup, "GEMINI API KEY  ·  " + (store.hasKey("gemini") ? "Saved securely" : "Not saved"), "", true);
        EditText gmodel = field(backup, "MODEL ID", store.get("gemini_model", "gemini-2.5-flash"), false);
        TextView gstatus = text("Gemini: " + geminiStatus, 13, MUTED); backup.addView(gstatus); gap(backup, 12);
        Button gsave = button("SAVE BACKUP SETTINGS", true); backup.addView(gsave, lp(-1, 50));
        gsave.setOnClickListener(v -> {
            if (!gmodel.getText().toString().trim().matches("[a-zA-Z0-9._-]{3,80}")) { toast("Invalid model ID."); return; }
            try {
                if (!gkey.getText().toString().trim().isEmpty()) { store.key("gemini", gkey.getText().toString().trim()); gkey.setText(""); }
                store.put("gemini_model", gmodel.getText().toString().trim()); store.setBool("gemini_enabled", enable.isChecked());
                geminiStatus = "Not tested"; gstatus.setText("Gemini: Not tested"); toast("Saved. Run a connection test.");
            } catch (Exception e) { toast("Could not secure Gemini key."); }
        });
        gap(backup, 10); Button gtest = button("TEST GEMINI · REAL API CALL", false); backup.addView(gtest, lp(-1, 50));
        gtest.setOnClickListener(v -> {
            gsave.performClick(); if (!Services.online(this)) { setResult(gstatus, "No internet connection.", false); return; }
            gtest.setEnabled(false); gstatus.setText("Testing with a real Gemini request…");
            io.execute(() -> {
                boolean ok = false; String message;
                try { String reply = Services.testGemini(store.key("gemini"), store.get("gemini_model", ""));
                    message = "Model replied: " + reply.substring(0, Math.min(65, reply.length())); ok = true;
                } catch (Exception e) { message = Services.explain(e); lastError = message; }
                boolean passed = ok; String result = message;
                ui.post(() -> { geminiStatus = passed ? "Connected" : result; setResult(gstatus, result, passed); gtest.setEnabled(true); refreshStatus(); });
            });
        });
        gap(backup, 8); Button gremove = button("REMOVE GEMINI KEY", false); backup.addView(gremove, lp(-1, 48));
        gremove.setOnClickListener(v -> { store.clearKey("gemini"); store.setBool("gemini_enabled", false); enable.setChecked(false); geminiStatus = "Not connected"; gstatus.setText(geminiStatus); toast("Gemini key removed."); });
        gap(page, 12); page.addView(text("GonkaRouter's signup page is not its API. Create a key at gonkarouter.io/dashboard. Model IDs must match the provider's current model list.", 12, MUTED));
    }
    private void showVoiceSettings() {
        LinearLayout page = screen("VOICE", "A voice that answers when you need it.");
        LinearLayout panel = vertical(); card(page, panel); title(panel, "ELEVENLABS", "Generated speech · played through your device");
        EditText key = field(panel, "API KEY  ·  " + (store.hasKey("eleven") ? "Saved securely" : "Not saved"), "", true);
        TextView voiceName = text("Selected voice: " + store.get("voice_name", "None"), 14, TEXT); panel.addView(voiceName); gap(panel, 10);
        TextView vstatus = text("ElevenLabs: " + voiceStatus, 13, MUTED); panel.addView(vstatus); gap(panel, 12);
        Button save = button("SAVE VOICE KEY", true); panel.addView(save, lp(-1, 50));
        save.setOnClickListener(v -> {
            try { if (!key.getText().toString().trim().isEmpty()) { store.key("eleven", key.getText().toString().trim()); key.setText(""); voiceStatus = "Not tested"; vstatus.setText("ElevenLabs: Not tested"); toast("Key saved securely."); }
                else toast(store.hasKey("eleven") ? "Key already saved." : "Enter an API key first.");
            } catch (Exception e) { toast("Could not secure voice key."); }
        });
        gap(panel, 10); Button load = button("TEST CONNECTION & LOAD VOICES", false); panel.addView(load, lp(-1, 50));
        load.setOnClickListener(v -> {
            if (!key.getText().toString().trim().isEmpty()) save.performClick();
            if (!Services.online(this)) { setResult(vstatus, "No internet connection.", false); return; }
            load.setEnabled(false); vstatus.setText("Checking ElevenLabs voices…");
            io.execute(() -> {
                try { List<Services.Voice> voices = Services.voices(store.key("eleven"));
                    ui.post(() -> { voiceStatus = "Connected"; setResult(vstatus, voices.size() + " available voices", true); load.setEnabled(true);
                        if (voices.isEmpty()) { toast("Account has no available voices."); return; }
                        String[] names = new String[voices.size()]; for(int i = 0; i < names.length; i++) names[i] = voices.get(i).name;
                        new AlertDialog.Builder(this).setTitle("Select an ElevenLabs voice").setItems(names, (a, which) -> {
                            Services.Voice selected = voices.get(which); store.put("voice_id", selected.id); store.put("voice_name", selected.name);
                            voiceName.setText("Selected voice: " + selected.name);
                        }).setNegativeButton("Later", null).show(); });
                } catch (Exception e) { String message = Services.explain(e); lastError = message;
                    ui.post(() -> { voiceStatus = message; setResult(vstatus, message, false); load.setEnabled(true); }); }
            });
        });
        gap(panel, 10); Button test = button("TEST SELECTED VOICE · GENERATES AUDIO", false); panel.addView(test, lp(-1, 50));
        test.setOnClickListener(v -> { if (store.get("voice_id", "").isEmpty()) { toast("Load and select a voice first."); return; } speakText("Hello. I'm Victor, your personal AI assistant."); });
        gap(panel, 10); Button remove = button("REMOVE ELEVENLABS KEY", false); panel.addView(remove, lp(-1, 48));
        remove.setOnClickListener(v -> { store.clearKey("eleven"); store.put("voice_id", ""); store.put("voice_name", "None"); voiceStatus = "Not connected"; vstatus.setText(voiceStatus); voiceName.setText("Selected voice: None"); stopAudio(); });
        LinearLayout options = vertical(); card(page, options); title(options, "PLAYBACK", "Adjust your listening experience");
        CheckBox enabled = checkbox("Enable voice playback", store.bool("voice_enabled", true)); options.addView(enabled);
        enabled.setOnCheckedChangeListener((v, checked) -> { store.setBool("voice_enabled", checked); if (!checked) stopAudio(); });
        CheckBox auto = checkbox("Auto-speak responses", store.bool("auto_speak", false)); options.addView(auto); auto.setOnCheckedChangeListener((v, checked) -> store.setBool("auto_speak", checked));
        gap(options, 17); TextView speedLabel = text("Speech speed: " + store.get("speed", "1.0") + "×", 14, TEXT); options.addView(speedLabel);
        SeekBar speed = new SeekBar(this); speed.setMax(50); speed.setProgress((int)((Float.parseFloat(store.get("speed", "1.0")) - .7f) * 100)); options.addView(speed, lp(-1, 50));
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int progress, boolean user) { if (user) speedLabel.setText(String.format(Locale.US, "Speech speed: %.2f×", .7f + progress/100f)); }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { store.put("speed", String.format(Locale.US, "%.2f", .7f + b.getProgress()/100f)); }
        });
        gap(options, 8); options.addView(text("Pitch: not supported by the ElevenLabs text-to-speech API. No synthetic pitch adjustment is applied.", 12, MUTED));
    }
    private void navigation() {
        new AlertDialog.Builder(this).setTitle("VICTOR AI").setItems(new String[]{"Home / Chat", "Conversation History", "Settings", "About VICTOR", "Clear current conversation"}, (d, which) -> {
            if (which == 0) showChat(); else if (which == 1) showHistory(); else if (which == 2) showSettings(); else if (which == 3) showAbout(); else confirmCurrentClear();
        }).show();
    }
    private void showHistory() {
        LinearLayout page = screen("HISTORY", "Your conversations stay on this device.");
        Button fresh = button("＋  NEW CONVERSATION", true); page.addView(fresh, lp(-1, 52)); fresh.setOnClickListener(v -> newChat()); gap(page, 18);
        List<Store.Chat> ordered = new ArrayList<>(chats); ordered.sort((a,b) -> Long.compare(b.updated, a.updated));
        for(Store.Chat chat : ordered) {
            LinearLayout row = vertical(); card(page, row);
            TextView label = text(chat.title, 16, TEXT); label.setMaxLines(2); label.setEllipsize(TextUtils.TruncateAt.END); row.addView(label);
            gap(row, 6); row.addView(text(chat.messages.size() + " messages  ·  " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(chat.updated)), 12, MUTED));
            gap(row, 8); Button open = button("CONTINUE", false); row.addView(open, lp(-1, 45)); open.setOnClickListener(v -> {
                if (busy && chat != current) { toast("Wait for the response to finish."); return; }
                current = chat; showChat();
            });
            gap(row, 6); Button delete = button("DELETE CONVERSATION", false); row.addView(delete, lp(-1, 45));
            delete.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Delete this conversation?")
                    .setPositiveButton("Delete", (a,b) -> { if (busy && chat == current) { toast("Wait for the response to finish."); return; }
                        chats.remove(chat); if (chats.isEmpty()) chats.add(new Store.Chat()); if (current == chat) current = chats.get(0); saveChats(); showHistory(); })
                    .setNegativeButton("Cancel", null).show());
        }
    }
    private void confirmCurrentClear() {
        new AlertDialog.Builder(this).setTitle("Clear this conversation?")
                .setPositiveButton("Clear", (a,b) -> { if (busy) { toast("Wait for the response to finish."); return; }
                    current.messages.clear(); current.title = "New conversation"; current.updated = System.currentTimeMillis(); saveChats(); showChat(); })
                .setNegativeButton("Cancel", null).show();
    }
    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("Clear all local conversations?").setMessage("This cannot be undone. API settings remain saved.")
                .setPositiveButton("Clear history", (a,b) -> { if (busy) { toast("Wait for the current response to finish."); return; }
                    chats.clear(); current = new Store.Chat(); chats.add(current); saveChats(); showChat(); })
                .setNegativeButton("Cancel", null).show();
    }
    private void showAbout() {
        LinearLayout page = screen("ABOUT VICTOR", "Your personal AI assistant.");
        LinearLayout panel = vertical(); card(page, panel); title(panel, "VICTOR AI", "Native Android · version 1.0");
        panel.addView(text("VICTOR connects to GonkaRouter for streaming AI chat, optionally falls back to Gemini, and uses ElevenLabs for speech. Android speech recognition converts your voice to text. Connections and generated speech require internet and your own service keys.", 14, TEXT));
        gap(panel, 18); panel.addView(text("Device control and server-generated file downloads are not built into the provider's basic chat API. For grounded search, enable Gemini and begin your message with “search web for”. VICTOR will not claim to have performed unsupported actions. You can save fenced code returned in chat to a file using Android's document picker.", 13, MUTED));
        gap(panel, 18); panel.addView(text("Keys are encrypted using Android Keystore. Conversations are stored in private local storage; app backup is disabled. Text and selected attachments are sent only to the selected AI provider. Images and up to three PDF pages require Gemini; speech is sent to ElevenLabs only when playback is requested or auto-speak is enabled.", 13, MUTED));
    }
    @Override public void onBackPressed() { if (input == null || input.getParent() == null || shell == null || !isChatVisible()) showChat(); else super.onBackPressed(); }
    private boolean isChatVisible() { return chatList != null && chatList.isAttachedToWindow(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
