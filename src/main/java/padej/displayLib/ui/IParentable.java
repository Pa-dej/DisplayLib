package padej.displayLib.ui;

public interface IParentable {
    Class<? extends WidgetManager> getParentManager();
}