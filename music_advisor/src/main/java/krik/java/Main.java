package krik.java;

import krik.java.viewer.Terminal;
import krik.java.viewer.Viewer;

import java.util.Objects;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Viewer viewer = new Terminal(System.out);
        var scanner = new Scanner(System.in);
        var app = new Application(
                getAccessParamValue(args),
                getResourceParamValue(args),
                getPageParamValue(args),
                viewer,
                scanner
        );
        app.run();
    }

    private static String getAccessParamValue(String[] args) {
        return getParamValue(args, "-access");
    }

    private static String getResourceParamValue(String[] args) {
        return getParamValue(args, "-resource");
    }

    private static Integer getPageParamValue(String[] args) {
        var page = getParamValue(args, "-page");
        return page == null ? null : Integer.valueOf(page);
    }

    private static String getParamValue(String[] args, String paramName) {
        for (int i = 0; i < args.length - 1; i++) {
            if (Objects.equals(args[i], paramName)) {
                return args[i + 1];
            }
        }
        return null;
    }
}
