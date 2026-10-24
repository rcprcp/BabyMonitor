package com.babymonitor.audio;

import com.babymonitor.dto.AudioAlertClassDto;
import com.babymonitor.dto.AudioAlertConfigDto;
import com.babymonitor.dto.AudioAlertGroupDto;
import com.babymonitor.entity.AudioAlertClass;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Which YAMNet classes may raise an {@code Audio} notification.
 *
 * <p>The class map is fixed (it ships with the model), but the alert set is
 * data: the migration creates one {@code audio_alert_classes} row per class and
 * the Audio Notification Config page flips them. The enabled set is cached in
 * memory so a capture thread can ask on every window without a database read;
 * a write refreshes the cache, so a change takes effect on the next window
 * without restarting any capture.</p>
 */
@ApplicationScoped
public class AudioAlertSettings {

    /** The last index in the bundled 521-class map (indices 0-520). */
    public static final int MAX_CLASS_INDEX = 520;

    /** The AudioSet categories, in class-index order. */
    public static final List<AudioAlertGroupDto> GROUPS = List.of(
            new AudioAlertGroupDto(1, "Speech & Human Sounds", 0, 23),
            new AudioAlertGroupDto(2, "Singing & Music Genres", 24, 276),
            new AudioAlertGroupDto(3, "Nature & Weather", 277, 293),
            new AudioAlertGroupDto(4, "Vehicles & Engines", 294, 349),
            new AudioAlertGroupDto(5, "Household & Tools", 350, 421),
            new AudioAlertGroupDto(6, "Impacts, Explosions & Acoustics", 422, MAX_CLASS_INDEX));

    @Inject
    AudioConfig config;

    private volatile List<String> names;
    private volatile Set<Integer> enabled;

    /** The classes that may raise an alert, without a database read per window. */
    public Set<Integer> enabledIndices() {
        Set<Integer> current = enabled;
        if (current == null) {
            synchronized (this) {
                if (enabled == null) {
                    refresh();
                }
                current = enabled;
            }
        }
        return current;
    }

    /** The whole config: categories plus every class and its switch. */
    public AudioAlertConfigDto config() {
        List<String> labels = names();
        Map<Integer, Boolean> switches = readSwitches();
        Set<Integer> defaults = defaultIndices();
        List<AudioAlertClassDto> classes = new ArrayList<>(labels.size());
        for (int index = 0; index < labels.size(); index++) {
            AudioAlertGroupDto group = groupFor(index);
            boolean on = switches.containsKey(index) ? switches.get(index) : defaults.contains(index);
            classes.add(new AudioAlertClassDto(
                    index, labels.get(index), group == null ? 0 : group.id(), on));
        }
        return new AudioAlertConfigDto(GROUPS, classes);
    }

    /** Flips one class and refreshes the cache the detector reads. */
    public void setEnabled(int index, boolean value) {
        if (index < 0 || index > MAX_CLASS_INDEX) {
            throw new IllegalArgumentException("No such YAMNet class: " + index);
        }
        QuarkusTransaction.requiringNew().run(() -> {
            AudioAlertClass row = AudioAlertClass.findByIndex(index);
            if (row == null) {
                row = new AudioAlertClass();
                row.classIndex = index;
                row.persist();
            }
            row.enabled = value;
        });
        refresh();
    }

    /** Replaces the whole set, which is how a group macro button saves. */
    public void setEnabledIndices(Collection<Integer> indices) {
        Set<Integer> wanted = indices.stream()
                .filter(index -> index != null && index >= 0 && index <= MAX_CLASS_INDEX)
                .collect(Collectors.toUnmodifiableSet());
        QuarkusTransaction.requiringNew().run(() -> {
            List<AudioAlertClass> rows = AudioAlertClass.listOrdered();
            if (rows.isEmpty()) {
                for (int index = 0; index <= MAX_CLASS_INDEX; index++) {
                    AudioAlertClass row = new AudioAlertClass();
                    row.classIndex = index;
                    row.enabled = wanted.contains(index);
                    row.persist();
                }
            } else {
                for (AudioAlertClass row : rows) {
                    row.enabled = wanted.contains(row.classIndex);
                }
            }
        });
        refresh();
    }

    /** Restores the shipped default: the Speech & Human Sounds group on. */
    public void resetToDefault() {
        setEnabledIndices(defaultIndices());
    }

    /** The shipped default: every class in the first group (0-23). */
    public Set<Integer> defaultIndices() {
        return IntStream.rangeClosed(GROUPS.get(0).firstIndex(), GROUPS.get(0).lastIndex())
                .boxed()
                .collect(Collectors.toUnmodifiableSet());
    }

    private void refresh() {
        List<AudioAlertClass> rows =
                QuarkusTransaction.requiringNew().call(AudioAlertClass::listOrdered);
        if (rows.isEmpty()) {
            // A database that predates V6 (or was baselined oddly) still gets the
            // documented default rather than silently alerting on nothing.
            enabled = defaultIndices();
            return;
        }
        enabled = rows.stream()
                .filter(row -> row.enabled)
                .map(row -> row.classIndex)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Map<Integer, Boolean> readSwitches() {
        List<AudioAlertClass> rows =
                QuarkusTransaction.requiringNew().call(AudioAlertClass::listOrdered);
        Map<Integer, Boolean> switches = new LinkedHashMap<>();
        for (AudioAlertClass row : rows) {
            switches.put(row.classIndex, row.enabled);
        }
        return switches;
    }

    private static AudioAlertGroupDto groupFor(int index) {
        for (AudioAlertGroupDto group : GROUPS) {
            if (index >= group.firstIndex() && index <= group.lastIndex()) {
                return group;
            }
        }
        return null;
    }

    private List<String> names() {
        List<String> current = names;
        if (current == null) {
            synchronized (this) {
                if (names == null) {
                    try (InputStream in =
                            AudioAlertSettings.class.getResourceAsStream(config.labelsResource())) {
                        if (in == null) {
                            throw new IllegalStateException(
                                    "Missing class map " + config.labelsResource());
                        }
                        names = List.copyOf(YamNetLabels.read(in));
                    } catch (IOException e) {
                        throw new UncheckedIOException(
                                "Could not read " + config.labelsResource(), e);
                    }
                }
                current = names;
            }
        }
        return current;
    }
}
