package org.example;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class FileBrowserPane extends VBox {

    private static final String STYLESHEET = Objects.requireNonNull(
            FileBrowserPane.class.getResource("/org/example/file-browser-pane.css"),
            "Missing stylesheet: /org/example/file-browser-pane.css"
    ).toExternalForm();

    private final TextField pathField = new TextField();
    private final ComboBox<Path> driveSelector = new ComboBox<>();
    private final TableView<FileItem> tableView = new TableView<>();
    private final Consumer<Path> pathChangedListener;
    private final Timeline autoRefreshTimeline;
    private final TextField filterField = new TextField();
    private final List<FileItem> allItems = new ArrayList<>();
    private Path currentPath;
    private boolean updatingDriveSelection;
    private long lastModifiedTime;

    FileBrowserPane(Path initialPath, Consumer<Path> pathChangedListener) {
        this.pathChangedListener = Objects.requireNonNullElse(pathChangedListener, path -> {});
        this.currentPath = resolveInitialPath(initialPath);
        setSpacing(8);
        setPadding(new Insets(4));
        getStyleClass().add("file-browser-pane");
        if (!getStylesheets().contains(STYLESHEET)) {
            getStylesheets().add(STYLESHEET);
        }

        this.autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> autoRefresh()));
        this.autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);

        getChildren().addAll(createHeader(), createTable(), createFilter());
        VBox.setVgrow(tableView, Priority.ALWAYS);
        refresh();
        this.autoRefreshTimeline.play();
        this.pathChangedListener.accept(currentPath);
    }

    private Node createHeader() {
        Button upButton = new Button("上一级");
        styleActionButton(upButton);
        upButton.setOnAction(event -> {
            if (currentPath.getParent() != null) {
                openPath(currentPath.getParent());
            }
        });

        driveSelector.setPrefWidth(80);
        driveSelector.setVisibleRowCount(12);
        driveSelector.getStyleClass().add("drive-selector");
        driveSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.toString());
                if (!getStyleClass().contains("drive-selector-button-cell")) {
                    getStyleClass().add("drive-selector-button-cell");
                }
            }
        });
        driveSelector.setCellFactory(column -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.toString());
                if (!getStyleClass().contains("drive-selector-cell")) {
                    getStyleClass().add("drive-selector-cell");
                }
            }
        });
        driveSelector.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingDriveSelection || newValue == null) {
                return;
            }
            Path currentRoot = currentPath.getRoot();
            if (currentRoot == null || !currentRoot.equals(newValue)) {
                openPath(newValue);
            }
        });

        pathField.setPromptText("输入路径后回车");
        pathField.getStyleClass().add("path-field");
        pathField.setOnAction(event -> openFromInput());
        HBox.setHgrow(pathField, Priority.ALWAYS);

        HBox header = new HBox(6, upButton, driveSelector, pathField);
        header.getStyleClass().add("file-browser-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 0, 0));
        return header;
    }

    private Node createFilter() {
        filterField.setPromptText("过滤: 支持 ||(或) &&(且) 例如: pdf||doc, test&&2025");
        filterField.getStyleClass().add("filter-field");
        filterField.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        HBox.setHgrow(filterField, Priority.ALWAYS);

        Button openInExplorerButton = new Button("系统管理器");
        styleActionButton(openInExplorerButton);
        openInExplorerButton.setOnAction(event -> openInSystemExplorer());

        Button csvViewerButton = new Button("CSV 查看");
        styleActionButton(csvViewerButton);
        csvViewerButton.setOnAction(event -> openCsvViewer());

        return new HBox(6, filterField, openInExplorerButton, csvViewerButton);
    }

    private Node createTable() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tableView.setPlaceholder(createPlaceholder());
        tableView.setFixedCellSize(36);
        tableView.getStyleClass().add("file-table");

        TableColumn<FileItem, FileItem> iconColumn = new TableColumn<>(" ");
        iconColumn.setMinWidth(70);
        iconColumn.setMaxWidth(70);
        iconColumn.setResizable(false);
        iconColumn.setSortable(false);
        iconColumn.setReorderable(false);
        iconColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleObjectProperty<>(cell.getValue()));
        iconColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : item.iconNode());
                setAlignment(Pos.CENTER);
                if (!getStyleClass().contains("icon-cell")) {
                    getStyleClass().add("icon-cell");
                }
            }
        });

        TableColumn<FileItem, String> nameColumn = new TableColumn<>("名称");
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name()));
        nameColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!getStyleClass().contains("name-cell")) {
                    getStyleClass().add("name-cell");
                }
            }
        });

        TableColumn<FileItem, String> typeColumn = new TableColumn<>("类型");
        typeColumn.setMinWidth(120);
        typeColumn.setMaxWidth(120);
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type()));
        typeColumn.setCellFactory(column -> mutedCell());

        TableColumn<FileItem, String> sizeColumn = new TableColumn<>("大小");
        sizeColumn.setMinWidth(120);
        sizeColumn.setMaxWidth(120);
        sizeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().sizeText()));
        sizeColumn.setCellFactory(column -> mutedCell());

        TableColumn<FileItem, String> timeColumn = new TableColumn<>("修改时间");
        timeColumn.setMinWidth(170);
        timeColumn.setMaxWidth(170);
        timeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().modifiedText()));
        timeColumn.setCellFactory(column -> mutedCell());

        tableView.getColumns().addAll(iconColumn, nameColumn, typeColumn, sizeColumn, timeColumn);
        tableView.setRowFactory(view -> {
            TableRow<FileItem> row = new TableRow<>();
            row.getStyleClass().add("file-table-row");
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    FileItem item = row.getItem();
                    if (item.directory()) {
                        openPath(item.path());
                    } else {
                        openFileWithDefaultApp(item.path());
                    }
                }
            });
            row.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                if (row.isEmpty()) {
                    // 空白处显示当前目录的右键菜单
                    if (WindowsShellContextMenu.show(currentPath, event.getScreenX(), event.getScreenY())) {
                        event.consume();
                    }
                    return;
                }
                // 有内容的地方显示选中文件的右键菜单
                tableView.getSelectionModel().select(row.getIndex());
                if (WindowsShellContextMenu.show(row.getItem().path(), event.getScreenX(), event.getScreenY())) {
                    event.consume();
                }
            });
            return row;
        });
        return tableView;
    }

    private void styleActionButton(Button button) {
        button.getStyleClass().add("action-button");
        button.getStyleClass().add("secondary-button");
    }

    private Label createPlaceholder() {
        Label label = new Label("当前目录没有内容");
        label.getStyleClass().add("file-table-placeholder");
        return label;
    }

    private TableCell<FileItem, String> mutedCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!getStyleClass().contains("muted-cell")) {
                    getStyleClass().add("muted-cell");
                }
            }
        };
    }

    private void refresh() {
        pathField.setText(currentPath.toString());
        syncDriveSelection();
        try {
            lastModifiedTime = Files.getLastModifiedTime(currentPath).toMillis();
        } catch (IOException e) {
            lastModifiedTime = 0;
        }
        try (Stream<Path> pathStream = Files.list(currentPath)) {
            allItems.clear();
            allItems.addAll(pathStream
                    .map(FileItem::from)
                    .sorted(Comparator
                            .comparing(FileItem::directory).reversed()
                            .thenComparing(FileItem::name, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList()));
            applyFilter();
        } catch (IOException exception) {
            MainApplication.showError("无法读取目录", currentPath + System.lineSeparator() + exception.getMessage());
            allItems.clear();
            applyFilter();
        }
    }

    private void openPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            MainApplication.showError("路径不存在", normalized.toString());
            return;
        }
        if (!Files.isDirectory(normalized)) {
            MainApplication.showError("不是文件夹", normalized.toString());
            return;
        }
        currentPath = normalized;
        refresh();
        pathChangedListener.accept(currentPath);
    }

    private void openFromInput() {
        try {
            openPath(Paths.get(pathField.getText().trim()));
        } catch (InvalidPathException exception) {
            MainApplication.showError("路径格式错误", exception.getInput());
        }
    }

    private void openInSystemExplorer() {
        String os = System.getProperty("os.name").toLowerCase();
        String path = currentPath.toAbsolutePath().toString();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer.exe", path);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", path);
            } else if (os.contains("nix") || os.contains("nux")) {
                pb = new ProcessBuilder("xdg-open", path);
            } else {
                MainApplication.showError("不支持的操作系统", "无法在系统文件管理器中打开");
                return;
            }
            pb.start();
        } catch (IOException exception) {
            MainApplication.showError("打开失败", "无法在系统文件管理器中打开: " + path);
        }
    }

    private void openFileWithDefaultApp(Path filePath) {
        String os = System.getProperty("os.name").toLowerCase();
        String path = filePath.toAbsolutePath().toString();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", path);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", path);
            } else if (os.contains("nix") || os.contains("nux")) {
                pb = new ProcessBuilder("xdg-open", path);
            } else {
                MainApplication.showError("不支持的操作系统", "无法打开文件");
                return;
            }
            pb.start();
        } catch (IOException exception) {
            MainApplication.showError("打开失败", "无法打开文件: " + path);
        }
    }

    private void openCsvViewer() {
        CsvViewerPane csvViewer = new CsvViewerPane(currentPath);
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("CSV 文件查看器 - " + currentPath.getFileName());
        stage.setScene(new javafx.scene.Scene(csvViewer, 1000, 700));
        stage.show();
    }

    private static Path initialPath() {
        return Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
    }

    private static Path resolveInitialPath(Path preferredPath) {
        Path fallback = initialPath();
        if (preferredPath == null) {
            return fallback;
        }
        Path normalized = preferredPath.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            return normalized;
        }
        return fallback;
    }

    private void applyFilter() {
        String filterText = filterField.getText().trim();
        List<FileItem> filteredItems;
        if (filterText.isEmpty()) {
            filteredItems = new ArrayList<>(allItems);
        } else {
            filteredItems = allItems.stream()
                    .filter(item -> matchesFilter(item.name().toLowerCase(), filterText.toLowerCase()))
                    .collect(Collectors.toList());
        }
        tableView.setItems(FXCollections.observableArrayList(filteredItems));
    }

    private boolean matchesFilter(String fileName, String filter) {
        // 处理括号表达式
        filter = filter.trim();
        if (filter.startsWith("(") && findMatchingParen(filter) == filter.length() - 1) {
            return matchesFilter(fileName, filter.substring(1, filter.length() - 1));
        }

        // 查找最外层的 || 运算符（不在括号内的）
        int orPos = findOperator(filter, "||");
        if (orPos != -1) {
            String left = filter.substring(0, orPos).trim();
            String right = filter.substring(orPos + 2).trim();
            return matchesFilter(fileName, left) || matchesFilter(fileName, right);
        }

        // 查找最外层的 && 运算符（不在括号内的）
        int andPos = findOperator(filter, "&&");
        if (andPos != -1) {
            String left = filter.substring(0, andPos).trim();
            String right = filter.substring(andPos + 2).trim();
            return matchesFilter(fileName, left) && matchesFilter(fileName, right);
        }

        // 基础条件：包含关键词
        if (!filter.isEmpty()) {
            return fileName.contains(filter);
        }

        return true;
    }

    private int findOperator(String str, String operator) {
        int depth = 0;
        for (int i = 0; i <= str.length() - operator.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && str.substring(i, i + operator.length()).equals(operator)) {
                return i;
            }
        }
        return -1;
    }

    private int findMatchingParen(String str) {
        int depth = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private void autoRefresh() {
        try {
            long currentModifiedTime = Files.getLastModifiedTime(currentPath).toMillis();
            if (currentModifiedTime > lastModifiedTime) {
                refresh();
            }
        } catch (IOException e) {
            // Ignore errors during auto-refresh
        }
    }

    private void syncDriveSelection() {
        List<Path> roots = StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false)
                .map(Path::toAbsolutePath)
                .collect(Collectors.toList());

        updatingDriveSelection = true;
        driveSelector.setItems(FXCollections.observableArrayList(roots));
        Path currentRoot = currentPath.getRoot();
        if (currentRoot != null && roots.contains(currentRoot)) {
            driveSelector.getSelectionModel().select(currentRoot);
        } else {
            driveSelector.getSelectionModel().clearSelection();
        }
        updatingDriveSelection = false;
    }
}
