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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
final class ExcelMainPanel extends BorderPane {

    private final Path currentDirectory;
    private final ListView<String> fileList = new ListView<>();
    private final TabPane excelTabPane = new TabPane();
    private final Map<String, ExcelTablePanel> openTabs = new ConcurrentHashMap<>();
    private final TextField filterField = new TextField();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final AtomicReference<List<String>> excelFilesReference = new AtomicReference<>(new ArrayList<>());
    private SplitPane splitPane;
    private long lastModifiedTime = 0;
    private final Timeline autoRefreshTimeline;

    private static final String STYLESHEET = Objects.requireNonNull(
            CsvMainPanel.class.getResource("/org/example/csv-viewer.css"),
            "Missing stylesheet: /org/example/csv-viewer.css"
    ).toExternalForm();

    ExcelMainPanel(Path directory) {
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
            List<String> excelFiles = new ArrayList<>();
            String filterText = filterField.getText();
            Files.walkFileTree(currentDirectory, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            String fileNameLower = file.toString().toLowerCase();
                            if (fileNameLower.endsWith(".xls") || fileNameLower.endsWith(".xlsx")) {
                                String fileName = currentDirectory.relativize(file).toString();
                                if (matchesFilter(fileName, filterText)) {
                                    excelFiles.add(fileName);
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
            excelFiles.sort(String::compareTo);
            return excelFiles;
        } catch (IOException e) {
            log.error("在后台加载文件失败: {}", currentDirectory, e);
            throw new RuntimeException(e);
        }
    }

    private void setupUI() {
        setPadding(new Insets(4));
        getStyleClass().add("csv-viewer-pane"); // Reuse CSS

        BorderPane leftPanel = new BorderPane();
        leftPanel.setCenter(fileList);
        leftPanel.setBottom(createFileListFooter());
        leftPanel.setMinWidth(320);
        leftPanel.setPrefWidth(320);
        leftPanel.setMaxWidth(450);
        leftPanel.getStyleClass().add("csv-left-panel");

        fileList.getStyleClass().add("csv-file-list");
        fileList.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().clear();
                } else {
                    getStyleClass().clear();
                    getStyleClass().add("csv-file-list-cell");
                    setText(item);
                }
            }
        });

        BorderPane rightPanel = new BorderPane();
        excelTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        excelTabPane.getStyleClass().add("csv-tab-pane");
        excelTabPane.setTabMinHeight(35);
        excelTabPane.setTabMaxHeight(35);

        rightPanel.setCenter(excelTabPane);
        rightPanel.getStyleClass().add("csv-right-panel");

        splitPane = new SplitPane(leftPanel, rightPanel);
        splitPane.setDividerPositions(0.2);
        splitPane.getStyleClass().add("csv-split-pane");
        splitPane.setPadding(new Insets(0));

        setCenter(splitPane);

        fileList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                openExcelInTab(newVal);
            }
        });
    }

    private void openExcelInTab(String fileName) {
        String tabId = fileName;
        if (openTabs.containsKey(tabId)) {
            excelTabPane.getSelectionModel().select(openTabs.get(tabId));
            return;
        }

        Path filePath = currentDirectory.resolve(fileName.replace("\\", "/"));
        ExcelTablePanel newTab = new ExcelTablePanel(fileName, filePath);

        openTabs.put(tabId, newTab);
        newTab.setOnClosed(event -> openTabs.remove(tabId));
        excelTabPane.getTabs().add(newTab);
        excelTabPane.getSelectionModel().select(newTab);
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

        filterField.textProperty().addListener((obs, oldVal, newVal) -> {
            List<String> list = excelFilesReference.get().stream().filter(file -> matchesFilter(file, newVal)).toList();
            fileList.setItems(FXCollections.observableArrayList(list));
        });

        HBox filterBox = new HBox(0, filterField);
        filterBox.setPadding(new Insets(6, 0, 6, 0));

        VBox footer = new VBox(6, label, filterLabel, filterBox);
        footer.setPadding(new Insets(12, 12, 12, 12));
        footer.getStyleClass().add("csv-footer");
        return footer;
    }

    private void loadFiles() {
        setCenter(progressIndicator);
        Task<List<String>> loadFilesTask = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return loadFilesInBackground();
            }
        };
        loadFilesTask.setOnSucceeded(event -> {
            excelFilesReference.set(loadFilesTask.getValue());
            Platform.runLater(() -> {
                fileList.setItems(FXCollections.observableArrayList(excelFilesReference.get()));
                setCenter(splitPane);
            });
        });
        loadFilesTask.setOnFailed(event -> {
            log.error("加载文件失败", loadFilesTask.getException());
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
            log.error("自动刷新失败: {}", currentDirectory, e);
        }
    }

    private boolean matchesFilter(String text, String filterText) {
        if (filterText == null || filterText.isEmpty()) {
            return true;
        }
        String lowerText = text.toLowerCase();
        String lowerFilter = filterText.toLowerCase();

        if (lowerFilter.contains("||")) {
            String[] parts = lowerFilter.split("\\|\\|");
            for (String part : parts) {
                if (matchesFilter(text, part.trim())) return true;
            }
            return false;
        }

        if (lowerFilter.contains("&&")) {
            String[] parts = lowerFilter.split("&&");
            for (String part : parts) {
                if (!matchesFilter(text, part.trim())) return false;
            }
            return true;
        }

        return lowerText.contains(lowerFilter);
    }
}
