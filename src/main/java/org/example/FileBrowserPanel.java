package org.example;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class FileBrowserPanel extends VBox {

    private static final String STYLESHEET = Objects.requireNonNull(
            FileBrowserPanel.class.getResource("/org/example/file-browser-pane.css"),
            "Missing stylesheet: /org/example/file-browser-pane.css"
    ).toExternalForm();

    static FileBrowserPanel selectedPanel = null;

    AppStateStore.PanelBean panelBean;
    private final TextField pathField = new TextField();
    private final ComboBox<Path> driveSelector = new ComboBox<>();
    private final TableView<FileItem> tableView = new TableView<>();
    private final Timeline autoRefreshTimeline;
    private final TextField filterField = new TextField();
    private final List<FileItem> allItems = new ArrayList<>();
    private boolean updatingDriveSelection;
    private long lastModifiedTime;

    FileBrowserPanel(AppStateStore.PanelBean panelBean) {
        this.panelBean = panelBean;
        setSpacing(8);
        setPadding(new Insets(4));
        getStyleClass().add("file-browser-pane");
        if (!getStylesheets().contains(STYLESHEET)) {
            getStylesheets().add(STYLESHEET);
        }

        this.autoRefreshTimeline = new Timeline(new KeyFrame(Duration.millis(200), event -> autoRefresh()));
        this.autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);

        getChildren().addAll(createHeader(), createTable(), createFilter());
        VBox.setVgrow(tableView, Priority.ALWAYS);
        refresh();
        this.autoRefreshTimeline.play();

        // 添加鼠标点击事件处理选中效果
        setPickOnBounds(false);
        addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
            if (selectedPanel != this) {
                if (selectedPanel != null) {
                    selectedPanel.setSelected(false);
                }
                setSelected(true);
                selectedPanel = this;
            }
        });
        // 新增: 设置键盘事件过滤器
        addEventFilter(KeyEvent.KEY_PRESSED, keyEvent -> {
            if (keyEvent.isControlDown()) {
                switch (keyEvent.getCode()) {
                    case C: // Ctrl+C
                        copySelectedPathToClipboard();
                        break;
                    case V: // Ctrl+V
                        pasteFromClipboard();
                        break;
                }
            }
        });
    }

    private void copySelectedPathToClipboard() {
        int selectedIndex = tableView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            FileItem selectedItem = tableView.getItems().get(selectedIndex);
            String path = selectedItem.path().toString();
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(path), null);
        }
    }

    private void pasteFromClipboard() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable contents = clipboard.getContents(null);
        if (contents == null) {
            return;
        }
        try {
            // 尝试处理文件列表
            if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                List<File> files = (List<File>) contents.getTransferData(DataFlavor.javaFileListFlavor);
                for (File file : files) {
                    Path source = file.toPath();
                    // 复制逻辑与之前相同...
                    // 检查源路径是否为文件夹
                    if (Files.isDirectory(source)) {
                        // 获取源文件夹的名称
                        String sourceFolderName = source.getFileName().toString();
                        Path targetSubDir = panelBean.ofPath().resolve(sourceFolderName);
                        Path targetDir;
                        // 如果目标位置已经存在同名文件夹，则将内容复制到该文件夹中
                        if (Files.exists(targetSubDir) && Files.isDirectory(targetSubDir)) {
                            targetDir = targetSubDir;
                        } else {
                            // 否则创建新的文件夹
                            Files.createDirectories(targetSubDir);
                            targetDir = targetSubDir;
                        }

                        // 如果是文件夹，递归复制子文件夹和文件，并保持目录结构
                        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                Path relativePath = source.relativize(dir);
                                Path targetPath = targetDir.resolve(relativePath);
                                Files.createDirectories(targetPath);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Path relativePath = source.relativize(file);
                                Path targetPath = targetDir.resolve(relativePath);
                                Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } else {
                        // 如果是文件，直接复制
                        Path targetDir = panelBean.ofPath();
                        Files.copy(source, targetDir.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                refresh(); // 刷新视图以反映更改
            }
            // 仍然保留对字符串路径的支持
            else if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String sourcePath = (String) contents.getTransferData(DataFlavor.stringFlavor);
                Path source = Paths.get(sourcePath);

                // 检查源路径是否为文件夹
                if (Files.isDirectory(source)) {
                    // 获取源文件夹的名称
                    String sourceFolderName = source.getFileName().toString();
                    Path targetSubDir = panelBean.ofPath().resolve(sourceFolderName);
                    Path targetDir;
                    // 如果目标位置已经存在同名文件夹，则将内容复制到该文件夹中
                    if (Files.exists(targetSubDir) && Files.isDirectory(targetSubDir)) {
                        targetDir = targetSubDir;
                    } else {
                        // 否则创建新的文件夹
                        Files.createDirectories(targetSubDir);
                        targetDir = targetSubDir;
                    }

                    // 如果是文件夹，递归复制子文件夹和文件，并保持目录结构
                    Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            Path relativePath = source.relativize(dir);
                            Path targetPath = targetDir.resolve(relativePath);
                            Files.createDirectories(targetPath);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Path relativePath = source.relativize(file);
                            Path targetPath = targetDir.resolve(relativePath);
                            Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    // 如果是文件，直接复制
                    Path targetDir = panelBean.ofPath();
                    Files.copy(source, targetDir.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
                refresh(); // 刷新视图以反映更改
            }
        } catch (UnsupportedFlavorException | IOException e) {
            MainApplication.showError("粘贴失败", "无法从剪切板粘贴: " + e.getMessage());
        }
    }

    private void setSelected(boolean selected) {
        if (selected) {
            if (!getStyleClass().contains("file-browser-pane-selected")) {
                getStyleClass().add("file-browser-pane-selected");
                requestLayout();
            }
        } else {
            getStyleClass().removeAll("file-browser-pane-selected");
            requestLayout();
        }
    }

    private Node createHeader() {
        Button upButton = new Button("上一级");
        styleActionButton(upButton);
        upButton.setOnAction(event -> {
            if (panelBean.path != null) {
                openPath(panelBean.ofPath().getParent());
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
            Path currentRoot = panelBean.ofPath().getRoot();
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
        tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(createPlaceholder());
        tableView.setFixedCellSize(36);
        tableView.getStyleClass().add("file-table");

        TableColumn<FileItem, FileItem> iconColumn = new TableColumn<>(" ");
        iconColumn.setMinWidth(30);
        iconColumn.setMaxWidth(30);
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
        // 设置nameColumn的最小宽度为140，并允许其自动扩展
        nameColumn.setMinWidth(140);
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name()));
        nameColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || item.isBlank() || empty) {
                    setText("");
                    setTooltip(new Tooltip(""));
                } else {
                    setText(item);
                    // 修改这里以展示文件的全路径作为tooltip
                    FileItem fileItem = getTableView().getItems().get(getIndex());
                    setTooltip(new Tooltip(fileItem.path().toString()));
                }
                if (!getStyleClass().contains("name-cell")) {
                    getStyleClass().add("name-cell");
                }
            }
        });

        TableColumn<FileItem, String> typeColumn = new TableColumn<>("类型");
        typeColumn.setMinWidth(60);
        typeColumn.setMaxWidth(80);
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type()));
        typeColumn.setCellFactory(column -> mutedCell());

        TableColumn<FileItem, String> sizeColumn = new TableColumn<>("大小");
        sizeColumn.setMinWidth(60);
        sizeColumn.setMaxWidth(120);
        sizeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().sizeText()));
        sizeColumn.setCellFactory(column -> mutedCell());

        TableColumn<FileItem, String> timeColumn = new TableColumn<>("修改时间");
        timeColumn.setMinWidth(140);
        timeColumn.setMaxWidth(170);
        timeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().modifiedText()));
        timeColumn.setCellFactory(column -> mutedCell());
        nameColumn.prefWidthProperty().bind(tableView.widthProperty().subtract(
                iconColumn.widthProperty().add(typeColumn.widthProperty()).add(sizeColumn.widthProperty()).add(timeColumn.widthProperty()).add(30)
        ));
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
                    if (WindowsShellContextMenu.show(panelBean.ofPath(), event.getScreenX(), event.getScreenY())) {
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
        pathField.setText(panelBean.path);
        filterField.setText(panelBean.filter);
        syncDriveSelection();
        try {
            lastModifiedTime = Files.getLastModifiedTime(panelBean.ofPath()).toMillis();
        } catch (IOException e) {
            lastModifiedTime = 0;
        }
        try (Stream<Path> pathStream = Files.list(panelBean.ofPath())) {
            allItems.clear();
            allItems.addAll(pathStream
                    .map(FileItem::from)
                    .sorted(Comparator
                            .comparing(FileItem::directory).reversed()
                            .thenComparing(FileItem::name, new NaturalSortComparator()))
                    .toList());
            applyFilter();
        } catch (IOException exception) {
            MainApplication.showError("无法读取目录", panelBean.path + System.lineSeparator() + exception.getMessage());
            allItems.clear();
            applyFilter();
        }
    }

    static class NaturalSortComparator implements Comparator<String> {
        @Override
        public int compare(String a, String b) {
            // 将字符串拆分为数字部分和非数字部分进行比较
            char[] aChars = a.toCharArray();
            char[] bChars = b.toCharArray();

            int aIndex = 0, bIndex = 0;

            while (aIndex < aChars.length && bIndex < bChars.length) {
                char aChar = aChars[aIndex];
                char bChar = bChars[bIndex];

                // 如果都是数字，则解析整个数字进行比较
                if (Character.isDigit(aChar) && Character.isDigit(bChar)) {
                    // 解析a中的数字
                    int aNum = 0;
                    while (aIndex < aChars.length && Character.isDigit(aChars[aIndex])) {
                        aNum = aNum * 10 + (aChars[aIndex] - '0');
                        aIndex++;
                    }

                    // 解析b中的数字
                    int bNum = 0;
                    while (bIndex < bChars.length && Character.isDigit(bChars[bIndex])) {
                        bNum = bNum * 10 + (bChars[bIndex] - '0');
                        bIndex++;
                    }

                    if (aNum != bNum) {
                        return Integer.compare(aNum, bNum);
                    }
                } else {
                    // 非数字部分按字符比较
                    if (aChar != bChar) {
                        return Character.compare(aChar, bChar);
                    }
                    aIndex++;
                    bIndex++;
                }
            }

            // 比较长度
            return Integer.compare(a.length(), b.length());
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
        panelBean.path = normalized.toString();
        refresh();
        AppStateStore.savePanelBean(panelBean);
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
        String path = panelBean.path;
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
            Path parent = filePath.getParent();
            if (parent != null) {
                pb.directory(parent.toFile());
            }
            pb.start();
        } catch (IOException exception) {
            MainApplication.showError("打开失败", "无法打开文件: " + path);
        }
    }

    private void openCsvViewer() {
        CsvViewerPanel csvViewer = new CsvViewerPanel(panelBean.ofPath());
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("CSV 文件查看器 - " + panelBean.path);
        stage.setScene(new javafx.scene.Scene(csvViewer, 1000, 700));
        stage.show();
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
        panelBean.filter = filterText;
        AppStateStore.savePanelBean(panelBean);
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
            long currentModifiedTime = Files.getLastModifiedTime(panelBean.ofPath()).toMillis();
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
        Path currentRoot = panelBean.ofPath().getRoot();
        if (currentRoot != null && roots.contains(currentRoot)) {
            driveSelector.getSelectionModel().select(currentRoot);
        } else {
            driveSelector.getSelectionModel().clearSelection();
        }
        updatingDriveSelection = false;
    }
}
