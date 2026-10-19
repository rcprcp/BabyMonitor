package com.babymonitor.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads YAMNet's class map, the {@code index,mid,display_name} CSV that ships
 * beside the model.
 *
 * <p>The index is the model's output position, so the file is read into a list
 * whose position <em>is</em> the class: a gap or a shuffled row would silently
 * mislabel every score, so the order is checked rather than assumed. The
 * display name is what a notification shows, and it is the only field kept.</p>
 *
 * <p>Names may contain commas (they are quoted in the CSV, e.g.
 * {@code "Crying, sobbing"}), so each row is split on its first two commas
 * rather than with a general CSV parser.</p>
 */
public final class YamNetLabels {

    private YamNetLabels() {
    }

    /**
     * Reads a class map stream into a list position-indexed by class.
     *
     * @param in the CSV; closed by this method
     * @return every display name, in index order
     * @throws IOException when the stream is unreadable, unordered or empty
     */
    public static List<String> read(InputStream in) throws IOException {
        List<String> names = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                int firstComma = line.indexOf(',');
                if (firstComma < 0) {
                    continue;
                }
                String index = line.substring(0, firstComma).trim();
                if (index.isEmpty() || !index.chars().allMatch(Character::isDigit)) {
                    // The header row ("index,mid,display_name").
                    continue;
                }
                int secondComma = line.indexOf(',', firstComma + 1);
                if (secondComma < 0) {
                    continue;
                }
                int position = Integer.parseInt(index);
                if (position != names.size()) {
                    throw new IOException("The YAMNet class map is out of order: got index "
                            + position + " where " + names.size() + " was expected");
                }
                names.add(unquote(line.substring(secondComma + 1).trim()));
            }
        }
        if (names.isEmpty()) {
            throw new IOException("The YAMNet class map holds no classes");
        }
        return List.copyOf(names);
    }

    /** Removes the quoting the CSV uses around names containing commas. */
    private static String unquote(String name) {
        if (name.length() >= 2 && name.charAt(0) == '"' && name.charAt(name.length() - 1) == '"') {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }
}
