package krik.java.viewer;

public interface Viewer {
    void showMessage(String message);

    void showMessage(String message, Object... args);
}
