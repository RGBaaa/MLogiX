package mlogix.test.compiler;

import mlogix.compiler.issue.*;
import mlogix.compiler.struct.*;
import mlogix.compiler.struct.SourceMapManager.*;
import mlogix.util.*;

public class IssueTest {
    public void test() {
        SourceMapManager sourceMapManager = new SourceMapManager();
        SourceMap sourceMap = sourceMapManager.loadSourceMap("hello everyone\n" +
                "the code is for IssueTest");

        Log.error(new Issue.ParserIssue(sourceMap, "error1", Issue.IssueLevel.ERROR)
                .info(6, 14, "who is everyone?")
                .info(6, 14, "oh no")
                .point(6, 14, "idk everyone")
                .toString()
        );
    }
}
