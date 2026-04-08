package org.example;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

record FileItem(Path path, String name, boolean directory, String type, String sizeText, String modifiedText) {

    private static final Logger log = LoggerFactory.getLogger(FileItem.class);

    public static final Set<String> ARCHIVE_EXTENSIONS = Set.of("zip", "rar", "7z", "tar", "gz", "bz2", "xz");
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    static FileItem from(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            boolean directory = attributes.isDirectory();
            String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            String type = directory ? "文件夹" : fileType(path);
            String sizeText = directory ? "-" : humanSize(attributes.size());
            String modifiedText = TIME_FORMATTER.format(Instant.ofEpochMilli(attributes.lastModifiedTime().toMillis()));
            return new FileItem(path, name, directory, type, sizeText, modifiedText);
        } catch (IOException exception) {
            log.error("获取文件属性失败: {}", path, exception);
            String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            return new FileItem(path, name, Files.isDirectory(path), "未知", "-", "-");
        }
    }

    static String fileType(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == fileName.length() - 1) {
            return "文件";
        }
        String extension = fileName.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
        if (ARCHIVE_EXTENSIONS.contains(extension)) {
            return "压缩包";
        }
        return extension.toUpperCase(Locale.ROOT);
    }

    static String humanSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        double value = size;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unitIndex = -1;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex]);
    }

    Node iconNode() {
        if (directory) {
            Rectangle body = new Rectangle(22, 14, Color.web("#f4c84b"));
            body.setArcWidth(5);
            body.setArcHeight(5);
            Rectangle tab = new Rectangle(10, 5, Color.web("#dfaf28"));
            tab.setTranslateX(-6);
            tab.setTranslateY(-8);
            StackPane icon = new StackPane(body, tab);
            icon.setMinSize(28, 22);
            return icon;
        }

        if ("压缩包".equals(type)) {
            Rectangle file = new Rectangle(18, 22, Color.web("#d9e3f7"));
            file.setArcHeight(4);
            file.setArcWidth(4);
            Polygon zip = new Polygon(
                    0.0, 0.0,
                    4.0, 0.0,
                    4.0, 16.0,
                    0.0, 16.0
            );
            zip.setFill(Color.web("#556b8a"));
            StackPane icon = new StackPane(file, zip);
            icon.setMinSize(28, 22);
            return icon;
        }

        Rectangle file = new Rectangle(18, 22, Color.web("#e9eef5"));
        file.setArcHeight(4);
        file.setArcWidth(4);
        Label ext = new Label(type.length() > 3 ? type.substring(0, 3) : type);
        ext.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #3c4f65;");
        StackPane icon = new StackPane(file, ext);
        icon.setMinSize(28, 22);
        return icon;
    }
}
