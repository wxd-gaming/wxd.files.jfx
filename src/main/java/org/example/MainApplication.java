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
        MainPanel mainPanel = new MainPanel(resolveSplitCount(getParameters().getRaw()));
        Scene scene = new Scene(mainPanel, 1280, 820);
        stage.setTitle("JFX Folder Manager");
        stage.setScene(scene);
        stage.show();
    }

    static int resolveSplitCount(List<String> args) {
        if (args.isEmpty()) {
            return 2;
        }
        String value = args.getFirst();
        return "4".equals(value) ? 4 : 2;
    }

    static void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Folder Manager");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
