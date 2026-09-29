package ai.victor.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Live HTTPS clients. No synthetic success, no credentials in URLs or logs. */
final class Services {
    interface TextSink { void token(String text); }
    static final class ApiError extends Exception {
        final int status;
        ApiError(String message, int status) { super(message); this.status = status; }
    }
    static final class Voice { final String id, name; Voice(String i, String n) { id = i; name = n; } @Override public String toString() { return name; } }
    static final class Attachment {
        String name, mime, text; byte[] image;
        boolean visual() { return image != null; }
    }
    static boolean online(Context c) {
        ConnectivityManager cm = (ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork(); NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
    static String endpoint(String base) throws ApiError {
        String url = base.trim().replaceAll("/+$", "");
        if (!url.startsWith("https://") || url.contains("?") || url.contains("#")) throw new ApiError("Use an HTTPS API base URL without query parameters.", 0);
        try { URL u = new URL(url); if (u.getHost().isEmpty() || u.getUserInfo() != null) throw new Exception(); }
        catch (Exception e) { throw new ApiError("Invalid HTTPS API base URL.", 0); }
        return url.endsWith("/v1") ? url : url + "/v1";
    }
    private static HttpURLConnection connect(String url, String method, String header, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(7000); c.setReadTimeout(45000);
        c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("Accept-Encoding", "identity");
        if (key != null && !key.isEmpty()) c.setRequestProperty(header, header.equals("Authorization") ? "Bearer " + key : key);
        if (method.equals("POST")) { c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.setDoOutput(true); }
        return c;
    }
    private static void post(HttpURLConnection c, JSONObject data) throws Exception {
        try (OutputStream out = c.getOutputStream()) { out.write(data.toString().getBytes(StandardCharsets.UTF_8)); }
    }
    private static String read(InputStream stream, int max) throws Exception {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) { if (out.size() + n > max) throw new ApiError("Response exceeds size limit.", 0); out.write(buf, 0, n); }
            return out.toString("UTF-8");
        }
    }
    private static void check(HttpURLConnection c) throws Exception {
        int status = c.getResponseCode(); if (status < 200 || status >= 300) {
            String detail = "";
            try { String body = read(c.getErrorStream(), 8192); JSONObject j = new JSONObject(body); JSONObject e = j.optJSONObject("error"); if (e != null) detail = e.optString("status", ""); }
            catch (Exception ignored) { }
            String msg;
            if (status == 401 || status == 403) msg = "Invalid or unauthorized API key.";
            else if (status == 402) msg = "Account credit or billing required.";
            else if (status == 429) msg = "Rate limit reached; try again later.";
            else if (status == 404) msg = "Endpoint or model not found. Check Settings.";
            else if (status >= 500) msg = "Service temporarily unavailable (HTTP " + status + ").";
            else msg = "Request rejected (HTTP " + status + ").";
            if (!detail.isEmpty() && detail.matches("[A-Z_]{2,45}")) msg += " " + detail;
            throw new ApiError(msg, status);
        }
    }
    static String explain(Exception e) {
        if (e instanceof ApiError) return e.getMessage();
        if (e instanceof SocketTimeoutException) return "Request timed out. Check your connection or retry.";
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException || e instanceof javax.net.ssl.SSLException) return "Unable to reach the service. Check internet and endpoint.";
        if (e instanceof java.io.InterruptedIOException) return "Request interrupted or timed out.";
        return "Service request failed (" + e.getClass().getSimpleName() + ").";
    }
    private static JSONArray history(Store.Chat chat) throws Exception {
        JSONArray messages = new JSONArray();
        JSONObject sys = new JSONObject(); sys.put("role", "system"); sys.put("content", "You are VICTOR AI, a concise and capable personal assistant. Be truthful about your capabilities. For code, use fenced code blocks with a language. Do not claim to have searched the web or executed code unless you actually have."); messages.put(sys);
        int start = Math.max(0, chat.messages.size() - 16);
        for (int i = start; i < chat.messages.size(); i++) {
            Store.Message m = chat.messages.get(i); if (!m.role.equals("user") && !m.role.equals("assistant")) continue;
            if (m.text.isEmpty() || m.text.equals("Thinking…") || m.text.equals("Switching to Gemini backup…")) continue;
            messages.put(new JSONObject().put("role", m.role).put("content", m.text + (m.attachment.isEmpty() ? "" : "\n\n" + m.attachment)));
        }
        return messages;
    }
    static String primary(Store.Chat chat, String base, String key, String model, boolean stream, TextSink sink) throws Exception {
        if (key.isEmpty()) throw new ApiError("Add a primary API key in Settings.", 0);
        if (model.trim().isEmpty()) throw new ApiError("Select a primary model.", 0);
        HttpURLConnection c = connect(endpoint(base) + "/chat/completions", "POST", "Authorization", key);
        try {
            JSONObject body = new JSONObject().put("model", model.trim()).put("messages", history(chat)).put("stream", stream);
            post(c, body); check(c);
            if (!stream) {
                JSONObject result = new JSONObject(read(c.getInputStream(), 4_000_000));
                String text = result.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
                if (text.isEmpty()) throw new ApiError("The model returned no text. Try another model.", 0);
                sink.token(text); return text;
            }
            StringBuilder answer = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim(); if (data.equals("[DONE]")) break;
                    if (data.isEmpty()) continue;
                    JSONObject event = new JSONObject(data);
                    if (event.has("error")) throw new ApiError("Provider reported a streaming error.", 0);
                    JSONArray choices = event.optJSONArray("choices"); if (choices == null || choices.length() == 0) continue;
                    JSONObject delta = choices.getJSONObject(0).optJSONObject("delta"); if (delta == null) continue;
                    String part = delta.optString("content", "");
                    if (!part.isEmpty()) { answer.append(part); sink.token(part); }
                }
            }
            if (answer.length() == 0) throw new ApiError("The model returned no visible text. Try a different model.", 0);
            return answer.toString();
        } finally { c.disconnect(); }
    }
    static String testPrimary(String base, String key, String model) throws Exception {
        Store.Chat chat = new Store.Chat(); chat.messages.add(new Store.Message("user", "Reply with one word: ready"));
        return primary(chat, base, key, model, false, t -> {});
    }
    static java.util.List<String> primaryModels(String base, String key) throws Exception {
        HttpURLConnection c = connect(endpoint(base) + "/models", "GET", "Authorization", key);
        try { check(c); JSONArray a = new JSONObject(read(c.getInputStream(), 2_000_000)).getJSONArray("data");
            java.util.List<String> models = new java.util.ArrayList<>(); for (int i = 0; i < a.length(); i++) models.add(a.getJSONObject(i).getString("id")); return models;
        } finally { c.disconnect(); }
    }
    static String gemini(Store.Chat chat, String key, String model, Attachment attachment, boolean stream, boolean search, TextSink sink) throws Exception {
        if (key.isEmpty()) throw new ApiError("Add a Gemini API key in Settings.", 0);
        if (!model.matches("[a-zA-Z0-9._-]{3,80}")) throw new ApiError("Invalid Gemini model ID.", 0);
        String path = stream ? ":streamGenerateContent?alt=sse" : ":generateContent";
        HttpURLConnection c = connect("https://generativelanguage.googleapis.com/v1beta/models/" + model + path, "POST", "x-goog-api-key", key);
        try {
            JSONArray contents = new JSONArray(); int start = Math.max(0, chat.messages.size() - 16);
            int lastUser = -1; for (int i = chat.messages.size() - 1; i >= start; i--) if (chat.messages.get(i).role.equals("user")) { lastUser = i; break; }
            for(int i = start; i < chat.messages.size(); i++) {
                Store.Message m = chat.messages.get(i); if (!m.role.equals("user") && !m.role.equals("assistant")) continue;
                if (m.text.equals("Thinking…") || m.text.equals("Switching to Gemini backup…")) continue;
                JSONArray parts = new JSONArray().put(new JSONObject().put("text", m.text + (m.attachment.isEmpty() ? "" : "\n\n" + m.attachment)));
                if (i == lastUser && attachment != null && attachment.visual())
                    parts.put(new JSONObject().put("inlineData", new JSONObject().put("mimeType", attachment.mime)
                            .put("data", android.util.Base64.encodeToString(attachment.image, android.util.Base64.NO_WRAP))));
                contents.put(new JSONObject().put("role", m.role.equals("assistant") ? "model" : "user").put("parts", parts));
            }
            JSONObject body = new JSONObject().put("contents", contents)
                    .put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", "You are VICTOR AI. Be helpful, direct, honest about what you have and have not done. Use fenced code blocks for code."))));
            if (search) body.put("tools", new JSONArray().put(new JSONObject().put("googleSearch", new JSONObject())));
            post(c, body); check(c);
            StringBuilder answer = new StringBuilder(); java.util.LinkedHashSet<String> sources = new java.util.LinkedHashSet<>();
            if (!stream) {
                JSONObject event = new JSONObject(read(c.getInputStream(), 4_000_000));
                if (search) collectSources(event, sources);
                String text = geminiText(event);
                if (!text.isEmpty()) { sink.token(text); answer.append(text); }
            } else {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) continue;
                        JSONObject event = new JSONObject(line.substring(5).trim());
                        if (search) collectSources(event, sources);
                        String part = geminiText(event);
                        if (!part.isEmpty()) { answer.append(part); sink.token(part); }
                    }
                }
            }
            if (answer.length() == 0) throw new ApiError("Gemini returned no text; the content may be blocked or unsupported.", 0);
            if (search && sources.isEmpty()) throw new ApiError("Gemini did not return grounded web sources; live search could not be verified.", 0);
            if (search) { String tail = "\n\nSources:\n" + android.text.TextUtils.join("\n", sources); answer.append(tail); sink.token(tail); }
            return answer.toString();
        } finally { c.disconnect(); }
    }
    private static void collectSources(JSONObject j, java.util.Set<String> sources) {
        JSONArray candidates = j.optJSONArray("candidates"); if (candidates == null || candidates.length() == 0) return;
        JSONObject candidate = candidates.optJSONObject(0); if (candidate == null) return;
        JSONObject metadata = candidate.optJSONObject("groundingMetadata"); if (metadata == null) return;
        JSONArray chunks = metadata.optJSONArray("groundingChunks"); if (chunks == null) return;
        for (int i = 0; i < chunks.length() && sources.size() < 8; i++) {
            JSONObject chunk = chunks.optJSONObject(i); if (chunk == null) continue;
            JSONObject web = chunk.optJSONObject("web");
            if (web != null) { String url = web.optString("uri", "");
                if (url.startsWith("https://")) sources.add(web.optString("title", "Source") + " — " + url);
            }
        }
    }
    private static String geminiText(JSONObject j) {
        JSONArray candidates = j.optJSONArray("candidates"); if (candidates == null || candidates.length() == 0) return "";
        JSONObject candidate = candidates.optJSONObject(0); if (candidate == null) return "";
        JSONObject content = candidate.optJSONObject("content"); if (content == null) return "";
        JSONArray parts = content.optJSONArray("parts"); if (parts == null) return "";
        StringBuilder text = new StringBuilder(); for (int i = 0; i < parts.length(); i++) text.append(parts.optJSONObject(i).optString("text", "")); return text.toString();
    }
    static String testGemini(String key, String model) throws Exception {
        Store.Chat chat = new Store.Chat(); chat.messages.add(new Store.Message("user", "Reply with one word: ready"));
        return gemini(chat, key, model, null, false, false, t -> {});
    }
    static java.util.List<Voice> voices(String key) throws Exception {
        if (key.isEmpty()) throw new ApiError("Add an ElevenLabs API key in Settings.", 0);
        HttpURLConnection c = connect("https://api.elevenlabs.io/v1/voices", "GET", "xi-api-key", key);
        try { check(c); JSONArray a = new JSONObject(read(c.getInputStream(), 2_000_000)).getJSONArray("voices");
            java.util.List<Voice> voices = new java.util.ArrayList<>(); for (int i = 0; i < a.length(); i++) {
                JSONObject v = a.getJSONObject(i); voices.add(new Voice(v.getString("voice_id"), v.optString("name", "Voice")));
            } return voices;
        } finally { c.disconnect(); }
    }
    static byte[] speech(String key, String voiceId, String text, float speed) throws Exception {
        if (key.isEmpty() || voiceId.isEmpty()) throw new ApiError("Configure ElevenLabs and select a voice first.", 0);
        HttpURLConnection c = connect("https://api.elevenlabs.io/v1/text-to-speech/" + java.net.URLEncoder.encode(voiceId, "UTF-8") + "?output_format=mp3_44100_128", "POST", "xi-api-key", key);
        try {
            c.setRequestProperty("Accept", "audio/mpeg");
            JSONObject body = new JSONObject().put("text", text).put("model_id", "eleven_multilingual_v2")
                    .put("voice_settings", new JSONObject().put("speed", Math.max(0.7, Math.min(1.2, speed))));
            post(c, body); check(c);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) {
                    if (out.size() + n > 12_000_000) throw new ApiError("Audio exceeded size limit. Try a shorter response.", 0);
                    out.write(buf, 0, n);
                } if (out.size() == 0) throw new ApiError("Voice service returned empty audio.", 0); return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }
}
