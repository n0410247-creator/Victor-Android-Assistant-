package ai.victor.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

/** Keys live encrypted in private preferences with a non-exportable Android Keystore AES key. */
final class Store {
    private static final String ALIAS = "victor.credentials.v1";
    private final Context ctx;
    private final SharedPreferences prefs;
    Store(Context c) { ctx = c.getApplicationContext(); prefs = ctx.getSharedPreferences("victor", Context.MODE_PRIVATE); }
    String get(String key, String def) { return prefs.getString(key, def); }
    void put(String key, String value) { prefs.edit().putString(key, value).apply(); }
    boolean bool(String key, boolean def) { return prefs.getBoolean(key, def); }
    void bool(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); }
    void clearKey(String name) { prefs.edit().remove("secret_" + name).apply(); }
    private SecretKey secret() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            gen.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }
    synchronized void key(String name, String value) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, secret());
        byte[] data = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
        prefs.edit().putString("secret_" + name, Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)).commit();
    }
    synchronized String key(String name) throws Exception {
        String encoded = prefs.getString("secret_" + name, ""); if (encoded.isEmpty()) return "";
        String[] parts = encoded.split(":", 2);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    boolean hasKey(String name) { return prefs.contains("secret_" + name); }

    static final class Message {
        String id, role, text, attachment; long time;
        Message(String role, String text) { this.id = java.util.UUID.randomUUID().toString(); this.role = role; this.text = text; this.time = System.currentTimeMillis(); this.attachment = ""; }
        JSONObject json() throws Exception { JSONObject j = new JSONObject(); j.put("id", id); j.put("role", role); j.put("text", text); j.put("time", time); j.put("attachment", attachment); return j; }
        static Message from(JSONObject j) { Message m = new Message(j.optString("role"), j.optString("text")); m.id = j.optString("id", m.id); m.time = j.optLong("time", m.time); m.attachment = j.optString("attachment"); return m; }
    }
    static final class Chat {
        String id, title; long updated; final List<Message> messages = new ArrayList<>();
        Chat() { id = java.util.UUID.randomUUID().toString(); title = "New conversation"; updated = System.currentTimeMillis(); }
        JSONObject json() throws Exception { JSONObject j = new JSONObject(); j.put("id", id); j.put("title", title); j.put("updated", updated); JSONArray a = new JSONArray(); for (Message m : messages) a.put(m.json()); j.put("messages", a); return j; }
        static Chat from(JSONObject j) { Chat c = new Chat(); c.id = j.optString("id", c.id); c.title = j.optString("title", c.title); c.updated = j.optLong("updated", c.updated); JSONArray a = j.optJSONArray("messages"); if (a != null) for(int i = 0; i < a.length(); i++) c.messages.add(Message.from(a.optJSONObject(i))); return c; }
    }
    synchronized List<Chat> load() {
        List<Chat> result = new ArrayList<>(); File f = new File(ctx.getFilesDir(), "chats.json"); if (!f.exists()) return result;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] bytes = new byte[(int) Math.min(f.length(), 12_000_000)]; int pos = 0, n;
            while (pos < bytes.length && (n = in.read(bytes, pos, bytes.length - pos)) > 0) pos += n;
            JSONArray a = new JSONArray(new String(bytes, 0, pos, StandardCharsets.UTF_8));
            for(int i = 0; i < a.length(); i++) result.add(Chat.from(a.getJSONObject(i)));
        } catch (Exception ignored) { /* No backup/network copy of local conversation data. */ }
        return result;
    }
    synchronized void save(List<Chat> chats) throws Exception {
        JSONArray a = new JSONArray(); for(Chat c : chats) a.put(c.json());
        File dst = new File(ctx.getFilesDir(), "chats.json"), tmp = new File(ctx.getFilesDir(), "chats.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(a.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        if (!tmp.renameTo(dst)) throw new java.io.IOException("Could not save chat history");
    }
}
