package org.example;

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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
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
    private Path currentPath;
    private boolean updatingDriveSelection;

    FileBrowserPane(Path initialPath, Consumer<Path> pathChangedListener) {
        this.pathChangedListener = Objects.requireNonNullElse(pathChangedListener, path -> {});
        this.currentPath = resolveInitialPath(initialPath);
        setSpacing(14);
        setPadding(new Insets(16));
        getStyleClass().add("file-browser-pane");
        if (!getStylesheets().contains(STYLESHEET)) {
            getStylesheets().add(STYLESHEET);
        }

        getChildren().addAll(createHeader(), createTable());
        VBox.setVgrow(tableView, Priority.ALWAYS);
        refresh();
        this.pathChangedListener.accept(currentPath);
    }

    private Node createHeader() {
        Button upButton = new Button("上一级");
        styleActionButton(upButton, false);
        upButton.setOnAction(event -> {
            if (currentPath.getParent() != null) {
                openPath(currentPath.getParent());
            }
        });

        Button refreshButton = new Button("刷新");
        styleActionButton(refreshButton, true);
        refreshButton.setOnAction(event -> refresh());

        driveSelector.setPrefWidth(120);
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

        HBox header = new HBox(8, upButton, refreshButton, driveSelector, pathField);
        header.getStyleClass().add("file-browser-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 2, 0));
        return header;
    }

    private Node createTable() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tableView.setPlaceholder(createPlaceholder());
        tableView.setFixedCellSize(42);
        tableView.getStyleClass().add("file-table");

        TableColumn<FileItem, FileItem> iconColumn = new TableColumn<>(" ");
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
        typeColumn.setMaxWidth(120);
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type()));
        typeColumn.setCellFactory(column -> mutedCell());

        TableColumn<FileItem, String> sizeColumn = new TableColumn<>("大小");
        sizeColumn.setMaxWidth(120);
        sizeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().sizeText()));
        sizeColumn.setCellFactory(column -> mutedCell());

        TableColumn<FileItem, String> timeColumn = new TableColumn<>("修改时间");
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
                    }
                }
            });
            row.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                if (row.isEmpty()) {
                    return;
                }
                tableView.getSelectionModel().select(row.getIndex());
                if (WindowsShellContextMenu.show(row.getItem().path(), event.getScreenX(), event.getScreenY())) {
                    event.consume();
                }
            });
            return row;
        });
        return tableView;
    }

    private void styleActionButton(Button button, boolean primary) {
        button.getStyleClass().add("action-button");
        button.getStyleClass().add(primary ? "primary-button" : "secondary-button");
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
        try (Stream<Path> pathStream = Files.list(currentPath)) {
            List<FileItem> items = pathStream
                    .map(FileItem::from)
                    .sorted(Comparator
                            .comparing(FileItem::directory).reversed()
                            .thenComparing(FileItem::name, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
            tableView.setItems(FXCollections.observableArrayList(items));
        } catch (IOException exception) {
            MainApplication.showError("无法读取目录", currentPath + System.lineSeparator() + exception.getMessage());
            tableView.setItems(FXCollections.emptyObservableList());
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
