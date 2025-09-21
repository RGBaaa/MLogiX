package mlogix.util;

import static mlogix.util.Ansi.*;

public class Printer {
	public static void printLogic(String code) {
		String[] lines = code.split("\n");
		int lineIdDightNum = Integer.toString(lines.length).length();
		int row = 0;
		for (String line : lines) {
			System.out.print(CYAN);
			System.out.print(" ".repeat(lineIdDightNum - Integer.toString(row).length()));
			System.out.print(row + "┃" + DEFAULT);
			System.out.println(line);
			row++;
		}
	}

	public static void printText(String code) {
		String[] lines = code.split("\n");
		int lineIdDightNum = Integer.toString(lines.length).length();
		int row = 1;
		for (String line : lines) {
			System.out.print(CYAN);
			System.out.print(" ".repeat(lineIdDightNum - Integer.toString(row).length()));
			System.out.print(row + "┃" + DEFAULT);
			System.out.println(line);
			row++;
		}
	}
}