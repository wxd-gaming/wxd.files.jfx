package org.example;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

final class AppStateStore {

    static final int MAX_SPLIT_COUNT = 10;

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(AppStateStore.class);
    private static final String KEY_SPLIT_COUNT = "wxd.files.jfx.ui.split.count";
    private static final String KEY_LAYOUT_MODE = "wxd.files.jfx.ui.layout.mode";
    private static final String KEY_PATH_PREFIX = "wxd.files.jfx.ui.path.";

    private AppStateStore() {
    }

    static int loadSplitCount() {
        int count = PREFERENCES.getInt(KEY_SPLIT_COUNT, 4);
        return normalizeSplitCount(count);
    }

    static void saveSplitCount(int splitCount) {
        PREFERENCES.putInt(KEY_SPLIT_COUNT, normalizeSplitCount(splitCount));
    }

    static String loadLayoutMode() {
        return PREFERENCES.get(KEY_LAYOUT_MODE, "MIXED");
    }

    static void saveLayoutMode(String layoutMode) {
        PREFERENCES.put(KEY_LAYOUT_MODE, layoutMode);
    }

    static List<Path> loadPanePaths(int splitCount) {
        int normalizedCount = normalizeSplitCount(splitCount);
        List<Path> paths = new ArrayList<>(normalizedCount);
        for (int index = 0; index < normalizedCount; index++) {
            String value = PREFERENCES.get(KEY_PATH_PREFIX + index, "");
            if (value.isBlank()) {
                paths.add(null);
                continue;
            }
            try {
                paths.add(Paths.get(value));
            } catch (RuntimeException exception) {
                paths.add(null);
            }
        }
        return paths;
    }

    static void savePanePath(int index, Path path) {
        if (index < 0) {
            return;
        }
        if (path == null) {
            PREFERENCES.remove(KEY_PATH_PREFIX + index);
            return;
        }
        PREFERENCES.put(KEY_PATH_PREFIX + index, path.toAbsolutePath().normalize().toString());
    }

    static void clearPanePathFrom(int startIndex) {
        for (int index = Math.max(startIndex, 0); index < MAX_SPLIT_COUNT; index++) {
            PREFERENCES.remove(KEY_PATH_PREFIX + index);
        }
    }

    private static int normalizeSplitCount(int splitCount) {
        if (splitCount >= 1 && splitCount <= MAX_SPLIT_COUNT) {
            return splitCount;
        }
        return 4;
    }
}
