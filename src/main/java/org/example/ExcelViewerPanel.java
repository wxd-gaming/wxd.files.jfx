package org.example;

import javafx.scene.layout.BorderPane;
import java.nio.file.Path;

/**
 * Excel Viewer Pane - Entry point for Excel file viewing functionality
 */
final class ExcelViewerPanel extends BorderPane {

    ExcelViewerPanel(Path directory) {
        ExcelMainPanel mainPanel = new ExcelMainPanel(directory);
        setCenter(mainPanel);
    }
}
