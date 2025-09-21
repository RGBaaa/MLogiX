package mlogix.compiler.struct;

import mlogix.logix.*;

/**
 * @param index 该Span所在SourceMap的索引
 */
public record Span(int index, int start, int end) {
	// start是开头的字符偏移量，end是末尾的字符偏移量+1
	// xxx some_chars xxx
	//     ^         ^
	//     start     end

	public static Span between(Token from, Token to) {
		return between(from.span, to.span);
	}

	public static Span between(Span from, Span to) {
		if(from.index != to.index) {
			throw new RuntimeException("不能对index不同的Span使用between(_)");
		}
		return new Span(from.index, from.start, to.end);
	}

	public String toString() {
		return String.format("Span{%d,%d,%d}", index, start, end);
	}
}