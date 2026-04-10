package org.example;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * CSV Table Panel - A tab component for displaying and editing CSV files
 */
@Slf4j
final class CsvTablePanel extends Tab {

    private final TableView<List<String>> scrollableTable = new TableView<>();
    private final TableView<List<String>> fixedTable = new TableView<>();
    private final ComboBox<Integer> fixedRowSelector = new ComboBox<>();
    private final CheckBox showHeaderCheckBox = new CheckBox("显示表头");
    private final VBox tableContainer = new VBox();
    private int fixedRowCount = 2;

    // CSV data storage for filtering
    private String[] originalHeaders;
    private List<List<String>> originalData;
    private List<TableColumn<List<String>, String>> allColumns;

    // Filter fields
    private TextField headerFilterField;
    private TextField contentFilterField;

    // Current file info for saving
    private final Path filePath;
    private Charset charset;

    private static final String STYLESHEET = Objects.requireNonNull(
            CsvTablePanel.class.getResource("/org/example/csv-viewer.css"),
            "Missing stylesheet: /org/example/csv-viewer.css"
    ).toExternalForm();

    CsvTablePanel(String fileName, Path filePath, int skipRows) {
        super(fileName);
        this.filePath = filePath;

        // Create content
        BorderPane tabContent = new BorderPane();

        // Load stylesheet on the content pane instead of the Tab
        if (!tabContent.getStylesheets().contains(STYLESHEET)) {
            tabContent.getStylesheets().add(STYLESHEET);
        }

        tabContent.setCenter(createMainContent());
        tabContent.setBottom(createTableFooter());
        tabContent.getStyleClass().add("csv-table-panel");

        setContent(tabContent);
        setClosable(true);

        // Load CSV data
        loadCsvFile(skipRows);
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
        scrollableTable.setEditable(true);
        scrollableTable.getStyleClass().add("csv-table");

        tableContainer.getChildren().addAll(fixedTable, scrollableTable);
        return tableContainer;
    }

    private VBox createTableFooter() {
        Label label = new Label("📊 " + getFileNameOnly(filePath));
        label.getStyleClass().add("csv-footer-label");
        label.setPadding(new Insets(4, 0, 4, 0));

        // Save button
        Button saveButton = new Button("💾 保存");
        saveButton.getStyleClass().add("csv-button");
        saveButton.setOnAction(event -> saveToCsv());

        // Header filter
        Label headerLabel = new Label("📋 表头");
        headerLabel.getStyleClass().add("csv-footer-sublabel");
        headerLabel.setPadding(new Insets(6, 4, 6, 0));

        headerFilterField = new TextField();
        headerFilterField.setPromptText("过滤 (|| 或, && 与)");
        headerFilterField.getStyleClass().add("csv-filter-field");
        HBox.setHgrow(headerFilterField, Priority.ALWAYS);

        headerFilterField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Content filter
        Label contentLabel = new Label("📄 内容");
        contentLabel.getStyleClass().add("csv-footer-sublabel");
        contentLabel.setPadding(new Insets(6, 4, 6, 0));

        contentFilterField = new TextField();
        contentFilterField.setPromptText("过滤 (|| 或, && 与)");
        contentFilterField.getStyleClass().add("csv-filter-field");
        HBox.setHgrow(contentFilterField, Priority.ALWAYS);

        contentFilterField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Fixed row selector
        Label fixedLabel = new Label("📌 固定行");
        fixedLabel.getStyleClass().add("csv-footer-sublabel");
        fixedLabel.setPadding(new Insets(6, 4, 6, 10));

        fixedRowSelector.setItems(FXCollections.observableArrayList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        fixedRowSelector.setValue(2);
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
        HBox filterRow = new HBox(6, saveButton, headerLabel, headerFilterField, contentLabel, contentFilterField, fixedLabel, fixedRowSelector, showHeaderCheckBox);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        VBox footer = new VBox(6, label, filterRow);
        footer.setPadding(new Insets(12, 12, 12, 12));
        footer.getStyleClass().add("csv-footer");
        return footer;
    }

    private String getFileNameOnly(Path path) {
        return path != null && path.getFileName() != null ? path.getFileName().toString() : "未知文件";
    }

    private void loadCsvFile(int skipRows) {
        // Detect encoding automatically
        Charset detectedCharset = detectCharset(filePath);
        charset = detectedCharset;
        List<String> lines;

        try {
            lines = Files.readAllLines(filePath, detectedCharset);

            if (lines.isEmpty()) {
                scrollableTable.getColumns().clear();
                scrollableTable.getItems().clear();
                fixedTable.getColumns().clear();
                fixedTable.getItems().clear();
                return;
            }

            // Parse first line to determine column count
            String[] firstLineValues = parseCsvLine(lines.get(0));
            int columnCount = firstLineValues.length;

            // Use generic headers: 列 1, 列 2, ...
            originalHeaders = new String[columnCount];
            for (int i = 0; i < columnCount; i++) {
                originalHeaders[i] = "列 " + (i + 1);
            }

            allColumns = new ArrayList<>();
            for (int i = 0; i < originalHeaders.length; i++) {
                final int colIndex = i;
                TableColumn<List<String>, String> column = new TableColumn<>(originalHeaders[i]);
                column.setCellValueFactory(data -> {
                    List<String> row = data.getValue();
                    String value = colIndex < row.size() ? row.get(colIndex) : "";
                    return new SimpleStringProperty(value);
                });
                column.setMinWidth(80);
                column.setPrefWidth(120);
                column.setEditable(true);

                column.getStyleClass().add("csv-table-column");

                column.setCellFactory(col -> createEditableCell(colIndex));
                allColumns.add(column);
            }

            // Parse data rows - treat ALL lines as data including the first one
            originalData = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                String[] values = parseCsvLine(lines.get(i));
                List<String> row = new ArrayList<>(List.of(values));
                // Add original row index at the end of the list (1-based)
                row.add(String.valueOf(i + 1));
                originalData.add(row);
            }

            // Clear filter fields and apply
            headerFilterField.clear();
            contentFilterField.clear();
            applyFilters();

        } catch (IOException e) {
            log.error("读取 CSV 文件失败: {}", filePath, e);
            MainApplication.showError("读取 CSV 文件失败", e.getMessage());
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

    private TableCell<List<String>, String> createEditableCell(int colIndex) {
        TableCell<List<String>, String> cell = new TableCell<>() {
            private final TextField textField = new TextField();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    if (getGraphic() != null) {
                        textProperty().unbind();
                        setText(getGraphic() instanceof TextField ? ((TextField) getGraphic()).getText() : null);
                        setGraphic(null);
                    }
                    setText(null);
                    setStyle("");
                } else {
                    if (isEditing()) {
                        if (getGraphic() != textField) {
                            textField.setText(item);
                            setGraphic(textField);
                            setText(null);
                        }
                        textField.selectAll();
                    } else {
                        if (getGraphic() != null) {
                            textProperty().unbind();
                            setGraphic(null);
                        }
                        setText(item);
                        // Let CSS handle text fill
                        setStyle("");
                    }
                }
                if (!getStyleClass().contains("csv-table-cell")) {
                    getStyleClass().add("csv-table-cell");
                }
            }

            @Override
            public void startEdit() {
                super.startEdit();
                if (isEmpty() || getTableRow() == null || getTableRow().isEmpty()) {
                    return;
                }
                setText(null);
                setGraphic(textField);
                textField.setText(getItem());
                textField.selectAll();
                textField.requestFocus();

                // Commit on Enter
                textField.setOnAction(e -> commitEdit(textField.getText()));
                // Commit on focus loss
                textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                    if (!newVal) {
                        commitEdit(textField.getText());
                    }
                });
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }

            @Override
            public void commitEdit(String newValue) {
                super.commitEdit(newValue);
                // Update the original data
                List<String> row = getTableRow().getItem();
                if (row != null) {
                    int rowIndex = getIndex();
                    if (rowIndex >= 0 && rowIndex < scrollableTable.getItems().size()) {
                        if (colIndex < row.size()) {
                            List<String> newRow = new ArrayList<>(row);
                            while (newRow.size() <= colIndex) {
                                newRow.add("");
                            }
                            newRow.set(colIndex, newValue);
                            getTableView().getItems().set(rowIndex, newRow);

                            // Update originalData as well
                            int originalRowIndex = findOriginalRowIndex(row);
                            if (originalRowIndex >= 0 && originalRowIndex < originalData.size()) {
                                List<String> originalRow = originalData.get(originalRowIndex);
                                List<String> newOriginalRow = new ArrayList<>(originalRow);
                                while (newOriginalRow.size() <= colIndex) {
                                    newOriginalRow.add("");
                                }
                                newOriginalRow.set(colIndex, newValue);
                                originalData.set(originalRowIndex, newOriginalRow);
                            }
                        }
                    }
                }
                setText(newValue);
                setGraphic(null);
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

    private int findOriginalRowIndex(List<String> row) {
        for (int i = 0; i < originalData.size(); i++) {
            List<String> originalRow = originalData.get(i);
            if (originalRow.equals(row)) {
                return i;
            }
        }
        return -1;
    }

    private Charset detectCharset(Path filePath) {
        try {
            byte[] buffer = new byte[4096];
            try (var is = Files.newInputStream(filePath)) {
                int bytesRead = is.read(buffer);
                if (bytesRead <= 0) {
                    return StandardCharsets.UTF_8;
                }

                // Check for BOM
                if (bytesRead >= 3 && buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB && buffer[2] == (byte) 0xBF) {
                    return StandardCharsets.UTF_8;
                }

                // Try UTF-8 first
                try {
                    String sample = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    if (!hasReplacementChar(sample)) {
                        return StandardCharsets.UTF_8;
                    }
                } catch (Exception utf8Exception) {
                    log.error("UTF-8 解码失败，尝试 GBK: {}", filePath);
                    // UTF-8 failed, try GBK
                    try {
                        new String(buffer, 0, bytesRead, Charset.forName("GBK"));
                        return Charset.forName("GBK");
                    } catch (Exception gbkException) {
                        log.error("GBK 解码也失败: {}", filePath);
                        return StandardCharsets.UTF_8;
                    }
                }

                // If UTF-8 has replacement character, try GBK
                try {
                    String sample = new String(buffer, 0, bytesRead, Charset.forName("GBK"));
                    if (!hasReplacementChar(sample)) {
                        return Charset.forName("GBK");
                    }
                } catch (Exception e) {
                    // GBK failed, fall back to UTF-8
                    log.error("GBK 解码失败: {}", filePath);
                }

                return StandardCharsets.UTF_8;
            }
        } catch (IOException e) {
            log.error("探测字符集失败: {}", filePath, e);
            return StandardCharsets.UTF_8;
        }
    }

    private boolean hasReplacementChar(String text) {
        return text.indexOf('\uFFFD') >= 0;
    }

    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i < line.length() - 1 && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values.toArray(new String[0]);
    }

    private void applyFilters() {
        if (originalHeaders == null || originalData == null) {
            return;
        }

        // Get filter texts
        String headerFilterText = headerFilterField.getText().trim();
        String contentFilterText = contentFilterField.getText().trim();

        // Separate original data into fixed part and filterable part
        List<List<String>> fixedRows = new ArrayList<>();
        List<List<String>> filterableRows = new ArrayList<>();

        for (int i = 0; i < originalData.size(); i++) {
            if (i < fixedRowCount) {
                fixedRows.add(originalData.get(i));
            } else {
                filterableRows.add(originalData.get(i));
            }
        }

        // Step 1: Filter columns based on header filter
        List<Integer> visibleColumnIndices = new ArrayList<>();

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
        List<TableColumn<List<String>, ?>> newFixedCols = new ArrayList<>();
        List<TableColumn<List<String>, ?>> newScrollCols = new ArrayList<>();

        // Add Row Number column first
        TableColumn<List<String>, String> fixedRowNoCol = createRowNoColumn();
        TableColumn<List<String>, String> scrollRowNoCol = createRowNoColumn();
        newFixedCols.add(fixedRowNoCol);
        newScrollCols.add(scrollRowNoCol);

        for (int colIndex : visibleColumnIndices) {
            TableColumn<List<String>, String> scrollCol = allColumns.get(colIndex);

            // Create a sync column for fixed table
            TableColumn<List<String>, String> fixedCol = new TableColumn<>(scrollCol.getText());
            fixedCol.setCellValueFactory(scrollCol.getCellValueFactory());
            fixedCol.setMinWidth(scrollCol.getMinWidth());
            fixedCol.setPrefWidth(scrollCol.getPrefWidth());

            // Sync widths
            fixedCol.widthProperty().addListener((obs, oldVal, newVal) -> scrollCol.setPrefWidth(newVal.doubleValue()));
            scrollCol.widthProperty().addListener((obs, oldVal, newVal) -> fixedCol.setPrefWidth(newVal.doubleValue()));

            newFixedCols.add(fixedCol);
            newScrollCols.add(scrollCol);
        }

        fixedTable.getColumns().setAll(newFixedCols);
        scrollableTable.getColumns().setAll(newScrollCols);

        // Step 2: Filter rows based on content filter (only filter non-fixed rows)
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

    private boolean matchesFilter(String text, String filterText) {
        if (filterText.isEmpty()) {
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

    private void saveToCsv() {
        if (originalHeaders == null) {
            MainApplication.showError("保存失败", "没有数据可保存");
            return;
        }

        try {
            List<String> lines = new ArrayList<>();

            lines.add(formatCsvLine(originalHeaders));

            for (List<String> row : originalData) {
                lines.add(formatCsvLine(row.toArray(new String[0])));
            }

            Files.write(filePath, lines, charset);

            MainApplication.showInfo("保存成功", "CSV文件已保存到: " + filePath);

        } catch (IOException e) {
            log.error("保存 CSV 文件失败: {}", filePath, e);
            MainApplication.showError("保存失败", "无法保存CSV文件: " + e.getMessage());
        }
    }

    private String formatCsvLine(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            String value = values[i];
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                sb.append("\"").append(value.replace("\"", "\"\"")).append("\"");
            } else {
                sb.append(value);
            }
        }
        return sb.toString();
    }
}
