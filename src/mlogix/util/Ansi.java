package mlogix.util;

public class Ansi {
	public static String DEFAULT = "\033[0m";//默认
	public static String BRIGHT = "\033[1m";//高亮
	public static String BOLD = "\033[2m";//粗体
	public static String UNDERLINE = "\033[4m";//下划线
	public static String BLINK = "\033[5m";//闪烁
	public static String REVERSED = "\033[7m";//反转
	public static String INVISIBLE = "\033[8m";//不可见
	public static String NON_BOLD = "\033[22m";//非粗体
	public static String NON_UNDERLINE = "\033[24m";//非下划线
	public static String NON_BLINK = "\033[25m";//非闪烁
	public static String NON_REVERSED = "\033[27m";//非反转
	public static String VISIBLE = "\033[28m";//可见
	
	public static String BLACK = "\033[30m";//黑色
	public static String RED = "\033[31m";//红色
	public static String GREEN = "\033[32m";//绿色
	public static String YELLOW = "\033[33m";//黄色
	public static String BLUE = "\033[34m";//蓝色
	public static String MAGENTA = "\033[35m";//洋红色
	public static String CYAN = "\033[36m";//青色
	public static String GRAY = "\033[37m";//灰色
	
	//以下为背景色
	public static String B_BLACK = "\033[40m";//黑色
	public static String B_RED = "\033[41m";//红色
	public static String B_GREEN = "\033[42m";//绿色
	public static String B_YELLOW = "\033[43m";//黄色
	public static String B_BLUE = "\033[44m";//蓝色
	public static String B_MAGENTA = "\033[45m";//洋红色
	public static String B_CYAN = "\033[46m";//青色
	public static String B_WHITE = "\033[47m";//白色
	
	//光标移动
	public static String UP(int n) {
		return "\033["+n+"A";
	}
	public static String DOWN(int n) {
		return "\033["+n+"B";
	}
	public static String RIGHT(int n) {
		return "\033["+n+"C";
	}
	public static String LEFT(int n) {
		return "\033["+n+"D";
	}
	
	public static String SAVE_CURSOR = "\033[s";//保存光标位置
	public static String ROLLBACK_CURSOR = "\033[u";//恢复光标位置
	public static String HIDE_CURSOR = "\033[?25l";//隐藏光标
	public static String SHOW_CURSOR = "\033[?25h";//显示光标
	
	//移动光标
	public static String MOVE_CURSOR(int x, int y) {
		return "\033["+y+";"+x+"H";
	}
	
	public static String CLEAR_END = "\033[0J";
	public static String CLEAR_START = "\033[1J";
	public static String CLEAR = "\033[2J";
	
	public static String CLEAR_LINE_END = "\033[0J";
	public static String CLEAR_LINE_START = "\033[1J";
	public static String CLEAR_LINE = "\033[2J";

}