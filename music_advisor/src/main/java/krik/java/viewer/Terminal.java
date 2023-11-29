package krik.java.viewer;

import java.io.PrintStream;

public class Terminal implements Viewer {
    private final PrintStream output;

    public Terminal(PrintStream output) {
        this.output = output;
    }

    @Override
    public void showMessage(String message) {
        this.output.println(message);
    }

    @Override
    public void showMessage(String message, Object... args) {
        this.output.printf(message + "%n", args);
    }
}
