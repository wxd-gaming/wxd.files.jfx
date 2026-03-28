package org.example;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

final class MainPanel extends BorderPane {

    private final GridPane contentGrid = new GridPane();
    private int splitCount;

    MainPanel(int splitCount) {
        this.splitCount = splitCount;
        setPadding(new Insets(12));
        setStyle("-fx-background-color: linear-gradient(to bottom, #f4f7fb, #dde6f2);");
        setTop(createToolbar());
        setCenter(contentGrid);
        BorderPane.setMargin(contentGrid, new Insets(12, 0, 0, 0));
        rebuildPanels();
    }

    private Node createToolbar() {
        Label title = new Label("Folder Manager");
        title.setFont(Font.font(20));
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #203040;");

        ToggleGroup group = new ToggleGroup();
        RadioButton twoSplit = new RadioButton("2 屏");
        twoSplit.setToggleGroup(group);
        twoSplit.setSelected(splitCount == 2);

        RadioButton fourSplit = new RadioButton("4 屏");
        fourSplit.setToggleGroup(group);
        fourSplit.setSelected(splitCount == 4);

        twoSplit.setOnAction(event -> switchSplitCount(2));
        fourSplit.setOnAction(event -> switchSplitCount(4));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(10, title, spacer, twoSplit, fourSplit);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 14, 10, 14));
        toolbar.setStyle("""
                -fx-background-color: rgba(255,255,255,0.92);
                -fx-background-radius: 14;
                -fx-border-color: rgba(32,48,64,0.10);
                -fx-border-radius: 14;
                """);
        return toolbar;
    }

    private void switchSplitCount(int count) {
        if (splitCount == count) {
            return;
        }
        splitCount = count;
        rebuildPanels();
    }

    private void rebuildPanels() {
        contentGrid.getChildren().clear();
        contentGrid.getColumnConstraints().clear();
        contentGrid.getRowConstraints().clear();
        contentGrid.setHgap(12);
        contentGrid.setVgap(12);

        int columns = 2;
        int rows = splitCount == 4 ? 2 : 1;

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
            FileBrowserPane pane = new FileBrowserPane();
            GridPane.setHgrow(pane, Priority.ALWAYS);
            GridPane.setVgrow(pane, Priority.ALWAYS);
            int row = splitCount == 4 ? index / 2 : 0;
            int column = splitCount == 4 ? index % 2 : index;
            contentGrid.add(pane, column, row);
        }
    }
}
