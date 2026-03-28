package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.List;

/**
 * s
 *
 * @author wxd-gaming(無心道, 15388152619)
 * @version 2026-03-28 14:16
 **/
public class MainApplication extends Application {

    @Override
    public void start(Stage stage) {
        MainPanel mainPanel = new MainPanel(resolveRequestedSplitCount(getParameters().getRaw()));
        Scene scene = new Scene(mainPanel, 1280, 820);
        stage.setTitle("無心道 分屏 文件管理器");
        stage.setScene(scene);
        stage.show();
    }

    static Integer resolveRequestedSplitCount(List<String> args) {
        if (args.isEmpty()) {
            return null;
        }
        String value = args.getFirst();
        return switch (value) {
            case "2" -> 2;
            case "4" -> 4;
            case "6" -> 6;
            default -> null;
        };
    }

    static void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Folder Manager");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    static void showInfo(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Folder Manager");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
