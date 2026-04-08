package org.example;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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
final class CsvTablePanel extends Tab {

    private final TableView<List<String>> csvTable = new TableView<>();

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

        tabContent.setCenter(createCsvTable());
        tabContent.setBottom(createTableFooter());
        tabContent.getStyleClass().add("csv-table-panel");

        setContent(tabContent);
        setClosable(true);

        // Load CSV data
        loadCsvFile(skipRows);
    }

    private Node createCsvTable() {
        csvTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        csvTable.setPlaceholder(new Label("没有数据"));
        csvTable.setEditable(true);
        csvTable.getStyleClass().add("csv-table");

        return csvTable;
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

        // Create filter row with all elements
        HBox filterRow = new HBox(6, saveButton, headerLabel, headerFilterField, contentLabel, contentFilterField);
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

            for (int i = 0; i < skipRows; i++) {
                if (lines.isEmpty()) break;
                lines.removeFirst();
            }

            if (lines.isEmpty()) {
                csvTable.getColumns().clear();
                csvTable.getItems().clear();
                return;
            }

            // Parse header
            originalHeaders = parseCsvLine(lines.get(0));
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

            // Parse data rows
            originalData = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String[] values = parseCsvLine(lines.get(i));
                originalData.add(new ArrayList<>(List.of(values)));
            }

            // Clear filter fields and apply
            headerFilterField.clear();
            contentFilterField.clear();
            applyFilters();

        } catch (IOException e) {
            MainApplication.showError("读取 CSV 文件失败", e.getMessage());
        }
    }

    private TableCell<List<String>, String> createEditableCell(int colIndex) {
        return new TableCell<>() {
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
                    if (rowIndex >= 0 && rowIndex < csvTable.getItems().size()) {
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
                    // UTF-8 failed, try GBK
                    try {
                        new String(buffer, 0, bytesRead, Charset.forName("GBK"));
                        return Charset.forName("GBK");
                    } catch (Exception gbkException) {
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
                }

                return StandardCharsets.UTF_8;
            }
        } catch (IOException e) {
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

        // Step 1: Filter columns based on header filter, but always show first column
        List<Integer> visibleColumnIndices = new ArrayList<>();

        if (originalHeaders.length > 0) {
            visibleColumnIndices.add(0);
        }

        for (int i = 1; i < originalHeaders.length; i++) {
            if (headerFilterText.isEmpty() || matchesFilter(originalHeaders[i], headerFilterText)) {
                visibleColumnIndices.add(i);
            }
        }

        // Update visible columns
        csvTable.getColumns().clear();
        for (int colIndex : visibleColumnIndices) {
            csvTable.getColumns().add(allColumns.get(colIndex));
        }

        // Step 2: Filter rows based on content filter
        List<List<String>> filteredData = new ArrayList<>();
        if (contentFilterText.isEmpty()) {
            filteredData = originalData;
        } else {
            for (List<String> row : originalData) {
                if (matchesContentFilter(row, visibleColumnIndices, contentFilterText)) {
                    filteredData.add(row);
                }
            }
        }

        csvTable.setItems(FXCollections.observableArrayList(filteredData));
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
