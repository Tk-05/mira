package com.mira.build.dependency;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal semver value + constraint matcher, used to pick a git tag for a
 * {@code version = "..."} dependency constraint. Pre-release/build metadata
 * suffixes are not supported: tags must look like (v)MAJOR(.MINOR(.PATCH)).
 */
public record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {

    private static final Pattern VERSION = Pattern.compile("^v?(\\d+)(?:\\.(\\d+)(?:\\.(\\d+))?)?$");

    public static Optional<SemVer> parse(String raw) {
        Matcher m = VERSION.matcher(raw.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        int major = Integer.parseInt(m.group(1));
        int minor = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        int patch = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        return Optional.of(new SemVer(major, minor, patch));
    }

    @Override
    public int compareTo(SemVer other) {
        return Comparator.comparingInt(SemVer::major)
                .thenComparingInt(SemVer::minor)
                .thenComparingInt(SemVer::patch)
                .compare(this, other);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    /**
     * Supports "^1.2.3" (caret), "~1.2.3" (tilde), and a bare "1.2.3" (exact).
     */
    public static boolean satisfies(SemVer candidate, String constraint) {
        String c = constraint.trim();
        if (c.startsWith("^")) {
            SemVer base = parse(c.substring(1)).orElseThrow(
                    () -> new IllegalArgumentException("Invalid version constraint: " + constraint));
            return satisfiesCaret(candidate, base);
        }
        if (c.startsWith("~")) {
            SemVer base = parse(c.substring(1)).orElseThrow(
                    () -> new IllegalArgumentException("Invalid version constraint: " + constraint));
            return candidate.major == base.major && candidate.minor == base.minor
                    && candidate.compareTo(base) >= 0;
        }
        SemVer exact = parse(c).orElseThrow(
                () -> new IllegalArgumentException("Invalid version constraint: " + constraint));
        return candidate.equals(exact);
    }

    private static boolean satisfiesCaret(SemVer candidate, SemVer base) {
        if (candidate.compareTo(base) < 0) {
            return false;
        }
        if (base.major > 0) {
            return candidate.major == base.major;
        }
        if (base.minor > 0) {
            return candidate.major == 0 && candidate.minor == base.minor;
        }
        return candidate.major == 0 && candidate.minor == 0 && candidate.patch == base.patch;
    }
}
