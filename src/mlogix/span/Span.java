package mlogix.span;

/**
 * @param index 该Span所在SourceMap的索引
 */
public record Span(int index, int start, int end) implements Spanned {
    // start是开头的字符偏移量，end是末尾的字符偏移量+1
    // xxx some_chars xxx
    //     ^         ^
    //     start     end

    public static Span between(Spanned from, Spanned to) {
        if(from.span().index != to.span().index) {
            throw new RuntimeException("不能对index不同的Span使用between(_)");
        }
        return new Span(from.span().index, from.span().start, to.span().end);
    }

    public String toString() {
        return String.format("Span{%d,%d,%d}", index, start, end);
    }

    @Override
    public Span span() {
        return this;
    }
}