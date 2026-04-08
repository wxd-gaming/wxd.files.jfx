package org.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class AppStateStore {

    static final Path emptyPath = Path.of("");
    static final int MAX_SPLIT_COUNT = 10;

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(AppStateStore.class);

    private AppStateStore() {
    }

    static int userDirHashcode() {
        String property = System.getProperty("user.dir");
        int hashCode = property.hashCode();
        return Math.abs(hashCode);
    }

    static String keySplitCount() {
        return "wxd.files.jfx.ui.split.count." + userDirHashcode();
    }

    static String keyLayoutCount() {
        return "wxd.files.jfx.ui.layout.mode." + userDirHashcode();
    }

    static int loadSplitCount() {
        int count = PREFERENCES.getInt(keySplitCount(), 4);
        return normalizeSplitCount(count);
    }

    static void saveSplitCount(int splitCount) {
        PREFERENCES.putInt(keySplitCount(), normalizeSplitCount(splitCount));
    }

    static String loadLayoutMode() {
        return PREFERENCES.get(keyLayoutCount(), "MIXED");
    }

    static void saveLayoutMode(String layoutMode) {
        PREFERENCES.put(keyLayoutCount(), layoutMode);
    }

    static String keyPanel(int index) {
        final String KEY_PANEL_PREFIX = "wxd.files.jfx.ui.panel.";
        return KEY_PANEL_PREFIX + "." + userDirHashcode() + "." + index;
    }

    static List<AppStateStore.PanelBean> loadPanePaths() {
        List<AppStateStore.PanelBean> panelBeanList = new ArrayList<>();
        for (int index = 0; index < MAX_SPLIT_COUNT; index++) {
            String value = PREFERENCES.get(keyPanel(index), ""); keyPanel(index);
            PanelBean panelBean = new PanelBean();
            if (value != null && !value.isBlank()) {
                try {
                    panelBean = new PanelBean(value);
                } catch (Exception e) {
                    log.error("加载面板配置失败: {}", value, e);
                }
            }
            panelBean.index = index;
            if (panelBean.path == null || panelBean.path.isBlank()) {
                panelBean.path = System.getProperty("user.home");
            }
            panelBeanList.add(panelBean);
        }
        return panelBeanList;
    }

    static void savePanelBean(PanelBean panelBean) {
        PREFERENCES.put(keyPanel(panelBean.index), panelBean.toString());
    }

    static int normalizeSplitCount(int splitCount) {
        if (splitCount >= 1 && splitCount <= MAX_SPLIT_COUNT) {
            return splitCount;
        }
        return 4;
    }

    public static class PanelBean {

        public int index;
        public String path = "";
        public String filter = "";

        public PanelBean() {
        }

        public PanelBean(String args) {
            String[] split = args.split("<~~>");
            if (split.length > 1)
                this.path = split[1];
            if (split.length > 2)
                this.filter = split[2];
        }

        public Path ofPath() {

            if (path == null) {
                return emptyPath;
            }
            Path of = Path.of(path);
            if (!Files.exists(of))
                return emptyPath;
            if (!Files.isDirectory(of)) {
                return emptyPath;
            }
            return of;
        }

        public String toString() {
            return index + "<~~>" + path + "<~~>" + filter;
        }

    }

}
