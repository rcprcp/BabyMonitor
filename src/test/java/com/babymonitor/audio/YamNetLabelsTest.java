package com.babymonitor.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The class map is the only thing that turns a YAMNet output position into
 * something a notification can say, so an out-of-order or truncated file has to
 * fail loudly rather than mislabel every score by one.
 */
class YamNetLabelsTest {

    private static final String HEADER = "index,mid,display_name\n";

    @Test
    void readsNamesInIndexOrderAndUnquotesThem() throws IOException {
        List<String> names = YamNetLabels.read(csv(HEADER
                + "0,/m/09x0r,Speech\n"
                + "1,/m/0ytgt,\"Child speech, kid speaking\"\n"
                + "2,/m/01h8n0,Conversation\n"));

        assertEquals(List.of("Speech", "Child speech, kid speaking", "Conversation"), names);
    }

    @Test
    void keepsTheCommasInsideAQuotedName() throws IOException {
        List<String> names = YamNetLabels.read(csv(HEADER + "0,/m/0463cq4,\"Crying, sobbing\"\n"));

        assertEquals(List.of("Crying, sobbing"), names);
    }

    @Test
    void rejectsAClassMapThatSkipsAnIndex() {
        IOException failure = assertThrows(IOException.class,
                () -> YamNetLabels.read(csv(HEADER
                        + "0,/m/09x0r,Speech\n"
                        + "2,/m/01h8n0,Conversation\n")));

        assertTrue(failure.getMessage().contains("out of order"), failure.getMessage());
    }

    @Test
    void rejectsAnEmptyClassMap() {
        assertThrows(IOException.class, () -> YamNetLabels.read(csv(HEADER)));
    }

    @Test
    void ignoresBlankLinesAndTheHeaderRow() throws IOException {
        List<String> names = YamNetLabels.read(csv(HEADER + "\n0,/m/09x0r,Speech\n\n"));

        assertEquals(List.of("Speech"), names);
    }

    private static ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
