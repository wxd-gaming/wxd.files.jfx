package org.example;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class FileBrowserPane extends VBox {

    private final TextField pathField = new TextField();
    private final ComboBox<Path> driveSelector = new ComboBox<>();
    private final TableView<FileItem> tableView = new TableView<>();
    private final Consumer<Path> pathChangedListener;
    private Path currentPath;
    private boolean updatingDriveSelection;

    FileBrowserPane(Path initialPath, Consumer<Path> pathChangedListener) {
        this.pathChangedListener = Objects.requireNonNullElse(pathChangedListener, path -> {});
        this.currentPath = resolveInitialPath(initialPath);
        setSpacing(10);
        setPadding(new Insets(12));
        setStyle("""
                -fx-background-color: rgba(255,255,255,0.96);
                -fx-background-radius: 18;
                -fx-border-color: rgba(32,48,64,0.12);
                -fx-border-radius: 18;
                """);

        getChildren().addAll(createHeader(), createTable());
        VBox.setVgrow(tableView, Priority.ALWAYS);
        refresh();
        this.pathChangedListener.accept(currentPath);
    }

    private Node createHeader() {
        Button upButton = new Button("上一级");
        upButton.setOnAction(event -> {
            if (currentPath.getParent() != null) {
                openPath(currentPath.getParent());
            }
        });

        Button refreshButton = new Button("刷新");
        refreshButton.setOnAction(event -> refresh());

        driveSelector.setPrefWidth(120);
        driveSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.toString());
            }
        });
        driveSelector.setCellFactory(column -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.toString());
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

        pathField.setOnAction(event -> openFromInput());
        HBox.setHgrow(pathField, Priority.ALWAYS);

        HBox header = new HBox(8, upButton, refreshButton, driveSelector, pathField);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Node createTable() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tableView.setPlaceholder(new Label("当前目录没有内容"));

        TableColumn<FileItem, FileItem> iconColumn = new TableColumn<>("Icon");
        iconColumn.setMaxWidth(70);
        iconColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleObjectProperty<>(cell.getValue()));
        iconColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : item.iconNode());
                setAlignment(Pos.CENTER);
            }
        });

        TableColumn<FileItem, String> nameColumn = new TableColumn<>("名称");
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name()));

        TableColumn<FileItem, String> typeColumn = new TableColumn<>("类型");
        typeColumn.setMaxWidth(120);
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type()));

        TableColumn<FileItem, String> sizeColumn = new TableColumn<>("大小");
        sizeColumn.setMaxWidth(120);
        sizeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().sizeText()));

        TableColumn<FileItem, String> timeColumn = new TableColumn<>("修改时间");
        timeColumn.setMaxWidth(170);
        timeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().modifiedText()));

        tableView.getColumns().addAll(iconColumn, nameColumn, typeColumn, sizeColumn, timeColumn);
        tableView.setRowFactory(view -> {
            TableRow<FileItem> row = new TableRow<>();
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
