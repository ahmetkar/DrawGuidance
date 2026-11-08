package com.karapp.drawguidance.server.distribution.cli;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v43 CLI argümanları: konumsal değerler ve {@code --anahtar değer} seçenekleri ({@code --anahtar=değer} de olur;
 * değersiz seçenek bayraktır). Aynı seçenek birden çok kez verilebilir ({@code --oge ... --oge ...}).
 */
public final class Args {
    private final List<String> positional = new ArrayList<>();
    private final Map<String, List<String>> options = new LinkedHashMap<>();
    private final Set<String> used = new HashSet<>();

    public Args(String[] argv, Set<String> flags) {
        for (int i = 0; i < argv.length; i++) {
            String a = argv[i];
            if (a.startsWith("--") && a.length() > 2) {
                String key = a.substring(2);
                String value;
                int eq = key.indexOf('=');
                if (eq >= 0) {
                    value = key.substring(eq + 1);
                    key = key.substring(0, eq);
                } else if (flags.contains(key) || i + 1 >= argv.length || argv[i + 1].startsWith("--")) {
                    value = "true";
                } else {
                    value = argv[++i];
                }
                options.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            } else {
                positional.add(a);
            }
        }
    }

    public String pos(int i) { return i < positional.size() ? positional.get(i) : null; }

    public String need(int i, String what) {
        String v = pos(i);
        if (v == null) throw new IllegalArgumentException(what + " gerekli");
        return v;
    }

    /** İlk değer (yoksa null); seçeneğin birden çok adı verilebilir. */
    public String opt(String... names) {
        for (String n : names) {
            List<String> v = options.get(n);
            if (v != null && !v.isEmpty()) {
                used.add(n);
                return v.get(0);
            }
        }
        return null;
    }

    public String need(String... names) {
        String v = opt(names);
        if (v == null) throw new IllegalArgumentException("--" + names[0] + " gerekli");
        return v;
    }

    public List<String> all(String... names) {
        List<String> out = new ArrayList<>();
        for (String n : names) {
            List<String> v = options.get(n);
            if (v != null) {
                used.add(n);
                out.addAll(v);
            }
        }
        return out;
    }

    public boolean flag(String... names) {
        String v = opt(names);
        return v != null && !"false".equalsIgnoreCase(v);
    }

    /** Tanınmayan seçenekler (yazım hatasını sessizce yutmamak için). */
    public List<String> unknown() {
        List<String> out = new ArrayList<>();
        for (String k : options.keySet()) if (!used.contains(k)) out.add("--" + k);
        return out;
    }
}
