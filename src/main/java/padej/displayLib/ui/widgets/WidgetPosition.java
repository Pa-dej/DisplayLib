package padej.displayLib.ui.widgets;

public class WidgetPosition {
    private double horizontal;  // rightMultiplier -> horizontal
    private double vertical;    // upMultiplier -> vertical
    private double depth;       // добавляем depth

    public WidgetPosition(double horizontal, double vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.depth = 0;
    }

    public WidgetPosition(double horizontal, double vertical, double depth) {
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.depth = depth;
    }

    public WidgetPosition add(double horizontal, double vertical, double depth) {
        this.horizontal += horizontal;
        this.vertical += vertical;
        this.depth += depth;
        return this;
    }

    public WidgetPosition add(double horizontal, double vertical) {
        return add(horizontal, vertical, 0);
    }

    public WidgetPosition addHorizontal(double value) {
        this.horizontal += value;
        return this;
    }

    public WidgetPosition addVertical(double value) {
        this.vertical += value;
        return this;
    }

    public WidgetPosition addDepth(double value) {
        this.depth += value;
        return this;
    }

    public WidgetPosition clone() {
        return new WidgetPosition(horizontal, vertical, depth);
    }

    public double getHorizontal() {
        return horizontal;
    }

    public double getVertical() {
        return vertical;
    }

    public double getDepth() {
        return depth;
    }

    public WidgetPosition setHorizontal(double horizontal) {
        this.horizontal = horizontal;
        return this;
    }

    public WidgetPosition setVertical(double vertical) {
        this.vertical = vertical;
        return this;
    }

    public WidgetPosition setDepth(double depth) {
        this.depth = depth;
        return this;
    }

    // Для обратной совместимости
    public double getRightMultiplier() {
        return horizontal;
    }

    public double getUpMultiplier() {
        return vertical;
    }
}