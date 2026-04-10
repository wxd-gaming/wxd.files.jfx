package org.example;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
final class ExcelTablePanel extends Tab {

    private final String fileName;
    private final Path filePath;
    private final TableView<List<String>> scrollableTable = new TableView<>();
    private final TableView<List<String>> fixedTable = new TableView<>();
    private final ComboBox<Integer> fixedRowSelector = new ComboBox<>();
    private final ComboBox<String> sheetSelector = new ComboBox<>();
    private final CheckBox showHeaderCheckBox = new CheckBox("显示表头");
    private final VBox tableContainer = new VBox();
    private final TextField headerFilterField = new TextField();
    private final TextField contentFilterField = new TextField();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private Workbook workbook;
    private int fixedRowCount = 4;
    
    private String[] originalHeaders;
    private List<List<String>> originalData = new ArrayList<>();

    private static final String STYLESHEET = Objects.requireNonNull(
            ExcelTablePanel.class.getResource("/org/example/csv-viewer.css"),
            "Missing stylesheet: /org/example/csv-viewer.css"
    ).toExternalForm();

    ExcelTablePanel(String fileName, Path filePath) {
        this.fileName = fileName;
        this.filePath = filePath;
        setText(fileName);
        setContent(createUI());
        loadExcelFile();
    }

    private Node createUI() {
        BorderPane root = new BorderPane();
        
        // Load stylesheet on the root pane
        if (!root.getStylesheets().contains(STYLESHEET)) {
            root.getStylesheets().add(STYLESHEET);
        }

        root.setCenter(createMainContent());
        root.setBottom(createTableFooter());
        root.getStyleClass().add("csv-table-panel");
        return root;
    }

    private Node createMainContent() {
        tableContainer.setSpacing(0);
        VBox.setVgrow(scrollableTable, Priority.ALWAYS);

        // Configure tables
        fixedTable.setMinHeight(0);
        fixedTable.setPrefHeight(0);
        fixedTable.getStyleClass().addAll("csv-table", "fixed-table");
        fixedTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        fixedTable.setPlaceholder(new Label(""));

        scrollableTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        scrollableTable.setPlaceholder(new Label("没有数据"));
        scrollableTable.getStyleClass().add("csv-table");

        // Sync vertical scrolling (optional, but good for consistency)
        tableContainer.getChildren().addAll(fixedTable, scrollableTable);
        return tableContainer;
    }

    private VBox createTableFooter() {
        Label label = new Label("📊 " + fileName);
        label.getStyleClass().add("csv-footer-label");
        label.setPadding(new Insets(4, 0, 4, 0));

        // Sheet selector
        Label sheetLabel = new Label("📑 工作表");
        sheetLabel.getStyleClass().add("csv-footer-sublabel");
        sheetLabel.setPadding(new Insets(6, 4, 6, 0));

        sheetSelector.setPromptText("选择工作表");
        sheetSelector.getStyleClass().add("csv-filter-field");
        sheetSelector.setMaxWidth(150);
        sheetSelector.setOnAction(event -> {
            String selectedSheet = sheetSelector.getValue();
            if (selectedSheet != null) {
                displaySheet(selectedSheet);
            }
        });

        // Header filter
        Label headerLabel = new Label("📋 表头");
        headerLabel.getStyleClass().add("csv-footer-sublabel");
        headerLabel.setPadding(new Insets(6, 4, 6, 10));

        headerFilterField.setPromptText("过滤 (|| 或, && 与)");
        headerFilterField.getStyleClass().add("csv-filter-field");
        HBox.setHgrow(headerFilterField, Priority.ALWAYS);
        headerFilterField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Content filter
        Label contentLabel = new Label("📄 内容");
        contentLabel.getStyleClass().add("csv-footer-sublabel");
        contentLabel.setPadding(new Insets(6, 4, 6, 10));

        contentFilterField.setPromptText("过滤 (|| 或, && 与)");
        contentFilterField.getStyleClass().add("csv-filter-field");
        HBox.setHgrow(contentFilterField, Priority.ALWAYS);
        contentFilterField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Fixed row selector
        Label fixedLabel = new Label("📌 固定行");
        fixedLabel.getStyleClass().add("csv-footer-sublabel");
        fixedLabel.setPadding(new Insets(6, 4, 6, 10));

        fixedRowSelector.setItems(FXCollections.observableArrayList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        fixedRowSelector.setValue(4);
        fixedRowSelector.getStyleClass().add("csv-filter-field");
        fixedRowSelector.setOnAction(event -> {
            fixedRowCount = fixedRowSelector.getValue();
            applyFilters();
        });

        // Show header checkbox
        showHeaderCheckBox.setSelected(true);
        showHeaderCheckBox.getStyleClass().add("csv-footer-sublabel");
        showHeaderCheckBox.setPadding(new Insets(6, 10, 6, 10));
        showHeaderCheckBox.setOnAction(event -> {
            if (showHeaderCheckBox.isSelected()) {
                scrollableTable.getStyleClass().remove("hide-header");
            } else {
                if (!scrollableTable.getStyleClass().contains("hide-header")) {
                    scrollableTable.getStyleClass().add("hide-header");
                }
            }
        });

        // Create filter row with all elements
        HBox filterRow = new HBox(6, sheetLabel, sheetSelector, headerLabel, headerFilterField, contentLabel, contentFilterField, fixedLabel, fixedRowSelector, showHeaderCheckBox);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        VBox footer = new VBox(6, label, filterRow);
        footer.setPadding(new Insets(12, 12, 12, 12));
        footer.getStyleClass().add("csv-footer");
        return footer;
    }

    private void loadExcelFile() {
        scrollableTable.setPlaceholder(progressIndicator);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (InputStream is = Files.newInputStream(filePath)) {
                    if (fileName.toLowerCase().endsWith(".xlsx")) {
                        workbook = new XSSFWorkbook(is);
                    } else {
                        workbook = new HSSFWorkbook(is);
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            ObservableList<String> sheetNames = FXCollections.observableArrayList();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheetNames.add(workbook.getSheetName(i));
            }
            sheetSelector.setItems(sheetNames);
            if (!sheetNames.isEmpty()) {
                sheetSelector.getSelectionModel().select(0);
                displaySheet(sheetNames.get(0));
            }
        });

        task.setOnFailed(event -> {
            log.error("加载 Excel 失败: {}", filePath, task.getException());
            scrollableTable.setPlaceholder(new Label("加载失败: " + task.getException().getMessage()));
        });

        new Thread(task).start();
    }

    private void displaySheet(String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) return;

        int maxCols = 0;
        for (Row row : sheet) {
            maxCols = Math.max(maxCols, row.getLastCellNum());
        }

        originalHeaders = new String[maxCols];
        for (int i = 0; i < maxCols; i++) {
            originalHeaders[i] = "列 " + (i + 1);
        }

        originalData.clear();
        DataFormatter formatter = new DataFormatter();

        for (Row row : sheet) {
            List<String> rowData = new ArrayList<>();
            for (int i = 0; i < maxCols; i++) {
                org.apache.poi.ss.usermodel.Cell cell = row.getCell(i);
                rowData.add(formatter.formatCellValue(cell));
            }
            // Add original row index (1-based)
            rowData.add(String.valueOf(row.getRowNum() + 1));
            originalData.add(rowData);
        }

        applyFilters();
    }

    private void applyFilters() {
        if (originalHeaders == null) return;

        fixedTable.getColumns().clear();
        scrollableTable.getColumns().clear();

        String headerFilterText = headerFilterField.getText().trim();
        String contentFilterText = contentFilterField.getText().trim();

        // Separate original data into fixed part and filterable part
        List<List<String>> fixedRows = new ArrayList<>();
        List<List<String>> filterableRows = new ArrayList<>();
        List<Integer> visibleColumnIndices = new ArrayList<>();

        for (int i = 0; i < originalData.size(); i++) {
            if (i < fixedRowCount) {
                fixedRows.add(originalData.get(i));
            } else {
                filterableRows.add(originalData.get(i));
            }
        }

        // Update visible columns for both tables
        List<TableColumn<List<String>, ?>> newFixedCols = new ArrayList<>();
        List<TableColumn<List<String>, ?>> newScrollCols = new ArrayList<>();
        
        for (int i = 0; i < originalHeaders.length; i++) {
            boolean matches = i == 0 || headerFilterText.isEmpty() || matchesFilter(originalHeaders[i], headerFilterText);

            // If not matched by header, but we have fixed rows, check them as well
            if (!matches && fixedRowCount > 0 && !headerFilterText.isEmpty()) {
                for (List<String> row : fixedRows) {
                    if (i < row.size() - 1 && matchesFilter(row.get(i), headerFilterText)) {
                        matches = true;
                        break;
                    }
                }
            }

            if (matches) {
                visibleColumnIndices.add(i);
            }
        }

        // Update visible columns for both tables
        newFixedCols.clear();
        newScrollCols.clear();

        // Add Row Number column first
        TableColumn<List<String>, String> fixedRowNoCol = createRowNoColumn();
        TableColumn<List<String>, String> scrollRowNoCol = createRowNoColumn();
        newFixedCols.add(fixedRowNoCol);
        newScrollCols.add(scrollRowNoCol);

        for (int i : visibleColumnIndices) {
            final int colIndex = i;
            TableColumn<List<String>, String> fixedCol = new TableColumn<>(originalHeaders[i]);
            TableColumn<List<String>, String> scrollCol = new TableColumn<>(originalHeaders[i]);

            fixedCol.setCellValueFactory(data -> {
                List<String> row = data.getValue();
                return new SimpleStringProperty(colIndex < row.size() ? row.get(colIndex) : "");
            });
            scrollCol.setCellValueFactory(data -> {
                List<String> row = data.getValue();
                return new SimpleStringProperty(colIndex < row.size() ? row.get(colIndex) : "");
            });

            // Sync column widths
            fixedCol.widthProperty().addListener((obs, oldVal, newVal) -> scrollCol.setPrefWidth(newVal.doubleValue()));
            scrollCol.widthProperty().addListener((obs, oldVal, newVal) -> fixedCol.setPrefWidth(newVal.doubleValue()));

            fixedCol.getStyleClass().add("csv-table-column");
            scrollCol.getStyleClass().add("csv-table-column");

            fixedCol.setCellFactory(col -> createStyledCell());
            scrollCol.setCellFactory(col -> createStyledCell());

            newFixedCols.add(fixedCol);
            newScrollCols.add(scrollCol);
        }
        
        fixedTable.getColumns().setAll(newFixedCols);
        scrollableTable.getColumns().setAll(newScrollCols);

        // Filter rows based on content filter (only filter non-fixed rows)
        List<List<String>> filteredData = new ArrayList<>();
        if (contentFilterText.isEmpty()) {
            filteredData.addAll(filterableRows);
        } else {
            for (List<String> row : filterableRows) {
                if (matchesContentFilter(row, visibleColumnIndices, contentFilterText)) {
                    filteredData.add(row);
                }
            }
        }

        // Final data lists
        List<List<String>> fixedDataList = fixedRows;
        List<List<String>> scrollDataList = filteredData;

        fixedTable.setItems(FXCollections.observableArrayList(fixedDataList));
        scrollableTable.setItems(FXCollections.observableArrayList(scrollDataList));

        // Sync horizontal scrolling
        syncHorizontalScrollingOnce();

        // Adjust fixedTable height
        if (fixedRowCount > 0 && !fixedDataList.isEmpty()) {
            fixedTable.setManaged(true);
            fixedTable.setVisible(true);

            // 使用 Platform.runLater 确保在表格渲染后计算高度
            Platform.runLater(() -> {
                double totalHeight = 0;
                // 获取表头高度
                Node header = fixedTable.lookup(".column-header-background");
                if (header != null) {
                    totalHeight += header.getBoundsInLocal().getHeight();
                } else {
                    totalHeight += 46; // 回退值
                }

                // 获取所有可见行并累加高度
                int rows = fixedTable.getItems().size();
                if (rows > 0) {
                    // 由于 TableView 是虚拟化的，我们取第一行的高度作为基准
                    Node rowNode = fixedTable.lookup(".table-row-cell");
                    if (rowNode != null) {
                        double singleRowHeight = rowNode.getBoundsInLocal().getHeight();
                        totalHeight += singleRowHeight * rows;
                    } else {
                        totalHeight += 38 * rows; // 回退值
                    }
                }

                totalHeight += 2; // 微量缓冲区确保边框显示

                if (totalHeight > 220) {
                    totalHeight = 220;
                    if (!fixedTable.getStyleClass().contains("show-vertical-scrollbar")) {
                        fixedTable.getStyleClass().add("show-vertical-scrollbar");
                    }
                } else {
                    fixedTable.getStyleClass().remove("show-vertical-scrollbar");
                }

                fixedTable.setPrefHeight(totalHeight);
                fixedTable.setMinHeight(totalHeight);
                fixedTable.setMaxHeight(totalHeight);
            });

            // Hide header of scrollableTable when fixedTable is visible
            if (!scrollableTable.getStyleClass().contains("hide-header")) {
                scrollableTable.getStyleClass().add("hide-header");
            }
        } else {
            fixedTable.getStyleClass().remove("show-vertical-scrollbar");
            fixedTable.setManaged(false);
            fixedTable.setVisible(false);
            fixedTable.setPrefHeight(0);
            fixedTable.setMinHeight(0);

            if (showHeaderCheckBox.isSelected()) {
                scrollableTable.getStyleClass().remove("hide-header");
            }
        }

    }

    private TableColumn<List<String>, String> createRowNoColumn() {
        TableColumn<List<String>, String> rowNoCol = new TableColumn<>("#");
        rowNoCol.setCellValueFactory(data -> {
            List<String> row = data.getValue();
            String rowNo = row.get(row.size() - 1);
            return new SimpleStringProperty(rowNo);
        });
        rowNoCol.setPrefWidth(50);
        rowNoCol.setMinWidth(50);
        rowNoCol.setEditable(false);
        rowNoCol.getStyleClass().add("csv-row-no-column");
        return rowNoCol;
    }

    private TableCell<List<String>, String> createStyledCell() {
        TableCell<List<String>, String> cell = new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("");
                }
                if (!getStyleClass().contains("csv-table-cell")) {
                    getStyleClass().add("csv-table-cell");
                }
            }
        };

        // Add context menu for copying
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("复制");
        copyItem.setOnAction(e -> {
            String text = cell.getText();
            if (text != null && !text.isEmpty()) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(text);
                clipboard.setContent(content);
            }
        });
        contextMenu.getItems().add(copyItem);
        cell.setContextMenu(contextMenu);

        return cell;
    }

    private boolean matchesFilter(String text, String filterText) {
        if (filterText.isEmpty()) {
            return true;
        }

        String lowerText = text.toLowerCase();
        String lowerFilter = filterText.toLowerCase();

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

    private boolean matchesContentFilter(List<String> row, List<Integer> visibleColumnIndices, String filterText) {
        if (visibleColumnIndices.isEmpty()) {
            return false;
        }

        StringBuilder rowText = new StringBuilder();
        for (int colIndex : visibleColumnIndices) {
            if (colIndex < row.size()) {
                rowText.append(row.get(colIndex)).append(" ");
            }
        }

        return matchesFilter(rowText.toString(), filterText);
    }

    private ScrollBar cachedFixedHBar;
    private ScrollBar cachedScrollHBar;

    private void syncHorizontalScrollingOnce() {
        if (cachedFixedHBar == null) {
            cachedFixedHBar = findScrollBar(fixedTable, Orientation.HORIZONTAL);
        }
        if (cachedScrollHBar == null) {
            cachedScrollHBar = findScrollBar(scrollableTable, Orientation.HORIZONTAL);
        }

        if (cachedFixedHBar != null && cachedScrollHBar != null) {
            // Unbind first to avoid multiple bindings
            cachedFixedHBar.valueProperty().unbindBidirectional(cachedScrollHBar.valueProperty());
            cachedFixedHBar.valueProperty().bindBidirectional(cachedScrollHBar.valueProperty());

            // Optional: hide the fixed horizontal bar more aggressively
            cachedFixedHBar.setMaxHeight(0);
            cachedFixedHBar.setMinHeight(0);
            cachedFixedHBar.setPrefHeight(0);
            cachedFixedHBar.setOpacity(0);
            cachedFixedHBar.setVisible(false);
            cachedFixedHBar.setManaged(false);
        }
    }

    private ScrollBar findScrollBar(Node node, Orientation orientation) {
        for (Node n : node.lookupAll(".scroll-bar")) {
            if (n instanceof ScrollBar bar && bar.getOrientation() == orientation) {
                return bar;
            }
        }
        return null;
    }
}
