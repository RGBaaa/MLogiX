package mlogix.span

class Span(
    val index: Int,  // 该Span所在SourceMap的索引
    val start: Int,  // 起始字符偏移量
    val end: Int     // 末尾字符偏移量+1
) : Spanned {

    override fun span(): Span = this

    override fun toString(): String = "Span{$index,$start,$end}"

    companion object {
        fun between(from: Spanned, to: Spanned): Span {
            require(from.span().index == to.span().index) {
                "不能对index不同的Span使用between(_)"
            }
            return Span(from.span().index, from.span().start, to.span().end)
        }
    }
}