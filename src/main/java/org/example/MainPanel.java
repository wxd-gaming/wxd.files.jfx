package org.example;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;

final class MainPanel extends BorderPane {

    enum LayoutMode {
        HORIZONTAL,
        VERTICAL,
        MIXED;

        static LayoutMode fromStoredValue(String value) {
            if (value == null || value.isBlank()) {
                return MIXED;
            }
            try {
                return LayoutMode.valueOf(value);
            } catch (IllegalArgumentException exception) {
                return MIXED;
            }
        }
    }

    private final GridPane contentGrid = new GridPane();
    List<AppStateStore.PanelBean> panelBeanList = new ArrayList<>();
    private int splitCount;
    private LayoutMode layoutMode;

    MainPanel(Integer requestedSplitCount) {
        int storedSplitCount = AppStateStore.loadSplitCount();
        splitCount = requestedSplitCount == null ? storedSplitCount : normalizeSplitCount(requestedSplitCount);
        layoutMode = LayoutMode.fromStoredValue(AppStateStore.loadLayoutMode());
        panelBeanList = AppStateStore.loadPanePaths();
        AppStateStore.saveSplitCount(splitCount);
        AppStateStore.saveLayoutMode(layoutMode.name());
        setPadding(new Insets(4));
        setStyle("-fx-background-color: linear-gradient(to bottom, #f4f7fb, #dde6f2);");
        setTop(createToolbar());
        setCenter(contentGrid);
        BorderPane.setMargin(contentGrid, new Insets(8, 0, 0, 0));
        rebuildPanels();
    }

    private Node createToolbar() {
        Label title = new Label("無心道  Folder Manager");
        title.setFont(Font.font(20));
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #203040;");

        ToggleGroup group = new ToggleGroup();
        
        HBox splitButtons = new HBox(2);
        splitButtons.setAlignment(Pos.CENTER_LEFT);
        for (int i = 1; i <= AppStateStore.MAX_SPLIT_COUNT; i++) {
            RadioButton splitRadio = new RadioButton(i + " 屏");
            splitRadio.setToggleGroup(group);
            splitRadio.setSelected(splitCount == i);
            final int count = i;
            splitRadio.setOnAction(event -> switchSplitCount(count));
            splitButtons.getChildren().add(splitRadio);
        }

        ToggleGroup layoutGroup = new ToggleGroup();
        RadioButton horizontal = new RadioButton("纵向");
        horizontal.setToggleGroup(layoutGroup);
        horizontal.setSelected(layoutMode == LayoutMode.HORIZONTAL);

        RadioButton vertical = new RadioButton("横向");
        vertical.setToggleGroup(layoutGroup);
        vertical.setSelected(layoutMode == LayoutMode.VERTICAL);

        RadioButton mixed = new RadioButton("纵横交错");
        mixed.setToggleGroup(layoutGroup);
        mixed.setSelected(layoutMode == LayoutMode.MIXED);

        horizontal.setOnAction(event -> switchLayoutMode(LayoutMode.HORIZONTAL));
        vertical.setOnAction(event -> switchLayoutMode(LayoutMode.VERTICAL));
        mixed.setOnAction(event -> switchLayoutMode(LayoutMode.MIXED));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Region buttonSeparator = new Region();
        buttonSeparator.setPrefWidth(16);

        HBox toolbar = new HBox(4, title, spacer, splitButtons, buttonSeparator, horizontal, vertical, mixed);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(4, 20, 4, 4));
        toolbar.setStyle("""
                -fx-background-color: rgba(255,255,255,0.92);
                -fx-background-radius: 14;
                -fx-border-color: rgba(32,48,64,0.10);
                -fx-border-radius: 8;
                """);
        return toolbar;
    }

    private void switchSplitCount(int count) {
        if (splitCount == count) {
            return;
        }
        splitCount = normalizeSplitCount(count);
        AppStateStore.saveSplitCount(splitCount);
        rebuildPanels();
    }

    private void switchLayoutMode(LayoutMode mode) {
        if (layoutMode == mode) {
            return;
        }
        layoutMode = mode;
        AppStateStore.saveLayoutMode(layoutMode.name());
        rebuildPanels();
    }

    private void rebuildPanels() {
        contentGrid.getChildren().clear();
        contentGrid.getColumnConstraints().clear();
        contentGrid.getRowConstraints().clear();
        contentGrid.setHgap(2);
        contentGrid.setVgap(2);

        int columns = calculateColumns();
        int rows = calculateRows(columns);

        for (int column = 0; column < columns; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / columns);
            constraints.setHgrow(Priority.ALWAYS);
            contentGrid.getColumnConstraints().add(constraints);
        }

        for (int row = 0; row < rows; row++) {
            RowConstraints constraints = new RowConstraints();
            constraints.setPercentHeight(100.0 / rows);
            constraints.setVgrow(Priority.ALWAYS);
            contentGrid.getRowConstraints().add(constraints);
        }

        for (int index = 0; index < splitCount; index++) {
            AppStateStore.PanelBean initialPath = panelBeanList.get(index);
            FileBrowserPanel pane = new FileBrowserPanel(initialPath);
            GridPane.setHgrow(pane, Priority.ALWAYS);
            GridPane.setVgrow(pane, Priority.ALWAYS);
            int row = rowFor(index, columns);
            int column = columnFor(index, columns);
            contentGrid.add(pane, column, row);
        }
    }

    private int calculateColumns() {
        return switch (layoutMode) {
            case HORIZONTAL -> splitCount;
            case VERTICAL -> 1;
            case MIXED -> (int) Math.ceil(Math.sqrt(splitCount));
        };
    }

    private int calculateRows(int columns) {
        return switch (layoutMode) {
            case HORIZONTAL -> 1;
            case VERTICAL -> splitCount;
            case MIXED -> (int) Math.ceil((double) splitCount / columns);
        };
    }

    private int rowFor(int index, int columns) {
        return switch (layoutMode) {
            case HORIZONTAL -> 0;
            case VERTICAL -> index;
            case MIXED -> index / columns;
        };
    }

    private int columnFor(int index, int columns) {
        return switch (layoutMode) {
            case HORIZONTAL -> index;
            case VERTICAL -> 0;
            case MIXED -> {
                int row = index / columns;
                int columnInRow = index % columns;
                if (row % 2 == 0) {
                    yield columnInRow;
                }
                yield columns - 1 - columnInRow;
            }
        };
    }

    private static int normalizeSplitCount(int splitCount) {
        if (splitCount >= 1 && splitCount <= AppStateStore.MAX_SPLIT_COUNT) {
            return splitCount;
        }
        return 4;
    }

}
