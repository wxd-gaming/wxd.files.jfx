package org.example;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * CSV Main Panel - Main interface for CSV viewer with file list and tabbed CSV display
 */
@Slf4j
final class CsvMainPanel extends BorderPane {

    private final ListView<String> fileList = new ListView<>();
    private final TabPane csvTabPane = new TabPane();
    private Path currentDirectory;

    // Auto refresh
    private final Timeline autoRefreshTimeline;
    private long lastModifiedTime;

    // Store tabs to prevent duplicates
    public final TextField skipField = new TextField();
    private final TextField filterField = new TextField();
    private SplitPane splitPane;
    private final javafx.collections.ObservableMap<String, CsvTablePanel> openTabs = javafx.collections.FXCollections.observableHashMap();

    private final ProgressIndicator progressIndicator = new ProgressIndicator();

    private static final String STYLESHEET = Objects.requireNonNull(
            CsvMainPanel.class.getResource("/org/example/csv-viewer.css"),
            "Missing stylesheet: /org/example/csv-viewer.css"
    ).toExternalForm();

    CsvMainPanel(Path directory) {
        this.currentDirectory = directory;

        // Load stylesheet
        if (!getStylesheets().contains(STYLESHEET)) {
            getStylesheets().add(STYLESHEET);
        }

        this.autoRefreshTimeline = new Timeline(new KeyFrame(Duration.millis(100), event -> autoRefresh()));
        this.autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);

        setupUI();

        loadFiles();
        this.autoRefreshTimeline.play();
    }

    private List<String> loadFilesInBackground() {
        try {
            lastModifiedTime = Files.getLastModifiedTime(currentDirectory).toMillis();
            List<String> csvFiles = new ArrayList<>();
            String filterText = filterField.getText();
            Files.walkFileTree(currentDirectory, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (file.toString().toLowerCase().endsWith(".csv")) {
                                String fileName = currentDirectory.relativize(file).toString();
                                if (matchesFilter(fileName, filterText)) {
                                    csvFiles.add(fileName);
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            // Ignore access denied errors and continue
                            return FileVisitResult.CONTINUE;
                        }
                    });
            csvFiles.sort(String::compareTo);
            return csvFiles;
        } catch (IOException e) {
            log.error("在后台加载文件失败: {}", currentDirectory, e);
            throw new RuntimeException(e);
        }
    }

    private void setupUI() {
        setPadding(new Insets(4));
        getStyleClass().add("csv-viewer-pane");

        // Left panel with file list
        BorderPane leftPanel = new BorderPane();
        leftPanel.setCenter(fileList);
        leftPanel.setBottom(createFileListFooter());
        leftPanel.setMinWidth(320);
        leftPanel.setPrefWidth(320);
        leftPanel.setMaxWidth(450);
        leftPanel.getStyleClass().add("csv-left-panel");

        // Style file list
        fileList.getStyleClass().add("csv-file-list");
        fileList.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    getStyleClass().clear();
                } else {
                    // Clean and apply style classes
                    getStyleClass().clear();
                    getStyleClass().add("csv-file-list-cell");
                    setText(item);
                }
            }
        });

        // Right panel with TabPane
        BorderPane rightPanel = new BorderPane();
        csvTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        csvTabPane.getStyleClass().add("csv-tab-pane");
        csvTabPane.setTabMinHeight(35);
        csvTabPane.setTabMaxHeight(35);

        rightPanel.setCenter(csvTabPane);
        rightPanel.getStyleClass().add("csv-right-panel");

        // Split pane
        splitPane = new SplitPane(leftPanel, rightPanel);
        splitPane.setDividerPositions(0.2);
        splitPane.getStyleClass().add("csv-split-pane");
        splitPane.setPadding(new Insets(0));

        setCenter(splitPane);

        // Event handlers
        fileList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                openCsvInTab(newVal);
            }
        });
    }

    private void openCsvInTab(String fileName) {
        String tabId = fileName;

        // Check if tab already exists
        if (openTabs.containsKey(tabId)) {
            csvTabPane.getSelectionModel().select(openTabs.get(tabId));
            return;
        }

        // Create new tab
        Path filePath = currentDirectory.resolve(fileName.replace("\\", "/"));
        CsvTablePanel newTab = new CsvTablePanel(fileName, filePath, Integer.parseInt(skipField.getText()));

        // Store tab reference
        openTabs.put(tabId, newTab);

        // Add close handler
        newTab.setOnClosed(event -> openTabs.remove(tabId));

        // Add tab to tab pane
        csvTabPane.getTabs().add(newTab);

        // Switch to new tab
        csvTabPane.getSelectionModel().select(newTab);
    }

    private VBox createFileListFooter() {
        Label label = new Label("📁 文件列表");
        label.getStyleClass().add("csv-footer-label");
        label.setPadding(new Insets(4, 0, 4, 0));

        Label filterLabel = new Label("🔍 过滤文件 (|| 或, && 与)");
        filterLabel.getStyleClass().add("csv-footer-sublabel");
        filterLabel.setPadding(new Insets(2, 0, 2, 0));


        filterField.setPromptText("例如: data||test 或 report&&2024");
        filterField.getStyleClass().add("csv-filter-field");
        HBox.setHgrow(filterField, Priority.ALWAYS);

        skipField.setPrefWidth(35);
        skipField.setMaxWidth(35);
        skipField.setPromptText("跳过的行数");
        skipField.getStyleClass().add("csv-filter-field");
        skipField.setText("1");
        HBox.setHgrow(skipField, Priority.NEVER);

        filterField.textProperty().addListener((obs, oldVal, newVal) -> {
            List<String> list = fileList.getItems().stream().filter(file -> matchesFilter(file, newVal)).toList();
            fileList.setItems(FXCollections.observableArrayList(list));
        });

        HBox filterBox = new HBox(0, skipField, filterField);
        filterBox.setPadding(new Insets(6, 0, 6, 0));

        VBox footer = new VBox(6, label, filterLabel, filterBox);
        footer.setPadding(new Insets(12, 12, 12, 12));
        footer.getStyleClass().add("csv-footer");
        return footer;
    }

    private void loadFiles() {
        setCenter(progressIndicator);
        // Start loading files in a background thread
        Task<List<String>> loadFilesTask = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return loadFilesInBackground();
            }
        };
        loadFilesTask.setOnSucceeded(event -> {
            List<String> csvFiles = loadFilesTask.getValue();
            Platform.runLater(() -> {
                fileList.setItems(FXCollections.observableArrayList(csvFiles));
                setCenter(splitPane);
            });
        });
        loadFilesTask.setOnFailed(event -> {
            log.error("加载文件失败", loadFilesTask.getException());
            MainApplication.showError("加载文件失败", loadFilesTask.getException().getMessage());
            setCenter(splitPane);
        });

        new Thread(loadFilesTask).start();
    }

    private void autoRefresh() {
        try {
            long currentModifiedTime = Files.getLastModifiedTime(currentDirectory).toMillis();
            if (currentModifiedTime > lastModifiedTime) {
                loadFiles();
            }
        } catch (IOException e) {
            // Ignore errors during auto-refresh
            log.error("自动刷新失败: {}", currentDirectory, e);
        }
    }

    private boolean matchesFilter(String text, String filterText) {
        if (filterText == null || filterText.isEmpty()) {
            return true;
        }

        String lowerText = text.toLowerCase();
        String lowerFilter = filterText.toLowerCase();

        // Check for && (AND) operator
        if (lowerFilter.contains("&&")) {
            String[] parts = lowerFilter.split("\\|\\|");
            for (String part : parts) {
                String[] andParts = part.split("&&");
                boolean allMatch = true;
                for (String andPart : andParts) {
                    if (!lowerText.contains(andPart.trim())) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return true;
                }
            }
            return false;
        }

        // Check for || (OR) operator
        if (lowerFilter.contains("||")) {
            String[] parts = lowerFilter.split("\\|\\|");
            for (String part : parts) {
                if (lowerText.contains(part.trim())) {
                    return true;
                }
            }
            return false;
        }

        return lowerText.contains(lowerFilter);
    }
}
