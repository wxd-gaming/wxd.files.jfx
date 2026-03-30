package org.example;

import javafx.scene.layout.BorderPane;
import java.nio.file.Path;

/**
 * CSV Viewer Pane - Entry point for CSV file viewing functionality
 */
final class CsvViewerPanel extends BorderPane {

    CsvViewerPanel(Path directory) {
        CsvMainPanel mainPanel = new CsvMainPanel(directory);
        setCenter(mainPanel);
    }
}
