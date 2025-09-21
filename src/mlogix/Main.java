package mlogix;

import java.nio.file.*;

import mlogix.compiler.Compiler;
import mlogix.util.*;
import mlogix.test.compiler.*;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("未传入参数");
            return;
        }

        if (args.length >= 2) {
            switch (args[1]) {
                case "d":
                    Log.setLevel(Log.LogType.DEBUG);
                    break;
            }
        }

        switch (args[0]) {
            case "c":
                compile();
                break;

            case "t":
                test();
                break;
        }
    }

    static void compile() {
        // 获取当前工作目录
        Path projectDirectory = Paths.get(System.getProperty("user.dir"));

        Compiler compiler = new Compiler(projectDirectory);
        boolean result = compiler.compile();
    }

    static void test() {
        new LexerTest().test();
    }
}