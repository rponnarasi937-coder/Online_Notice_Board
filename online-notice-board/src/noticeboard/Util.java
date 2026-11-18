package noticeboard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Small helpers: HTML escaping, form parsing, password hashing, random ids. */
final class Util {
    private Util() {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    /** Escape text so it is safe to place inside HTML (prevents XSS). */
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    static String dec(String s) {
        return URLDecoder.decode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    /** Parse "a=1&b=2" (query string or form body) into a map. */
    static Map<String, String> parseForm(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            map.put(dec(key), dec(value));
        }
        return map;
    }

    /** Read a request body (limited to 64 KB). */
    static String readBody(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BODY_BYTES) throw new IllegalArgumentException("Request too large");
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /** Random hex token (used for session ids and CSRF tokens). */
    static String token() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return HEX.formatHex(b);
    }

    static String salt() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HEX.formatHex(b);
    }

    /** PBKDF2 password hash (hex). */
    static String hash(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), HEX.parseHex(salt), 120_000, 256);
            byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return HEX.formatHex(h);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing not available", e);
        }
    }

    /** Constant-time string comparison. */
    static boolean sameText(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static String newId() {
        return Long.toString(System.currentTimeMillis(), 36) + token().substring(0, 6);
    }

    static String date(long millis) {
        return DATE_FORMAT.format(Instant.ofEpochMilli(millis));
    }
}
