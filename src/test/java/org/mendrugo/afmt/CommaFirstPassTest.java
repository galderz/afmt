package org.mendrugo.afmt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the comma-first transformation pass.
 */
class CommaFirstPassTest
{
    @Test
    void transformsTrailingCommaToLeadingComma()
    {
        final String input = "method(\n"
                + "    first,\n"
                + "    second,\n"
                + "    third\n"
                + ")";
        final String output = CommaFirstPass.apply(input);

        assertTrue(output.contains(", second"), "comma should lead 'second': " + output);
        assertTrue(output.contains(", third"), "comma should lead 'third': " + output);
        // 'first' should not have a leading comma
        assertTrue(output.contains("    first"), "first should not have leading comma: " + output);
    }

    @Test
    void leavesCommasInsideStringsAlone()
    {
        final String input = "String s = \"hello, world\";";
        final String output = CommaFirstPass.apply(input);

        assertEquals(input, output, "string content should not be changed");
    }

    @Test
    void leavesSingleLineListAlone()
    {
        final String input = "method(a, b, c)";
        final String output = CommaFirstPass.apply(input);

        assertEquals(input, output, "single-line list should not change");
    }

    @Test
    void doesNotTransformCommaBeforeClosingParen()
    {
        final String input = "method(\n"
                + "    first,\n"
                + ")";
        final String output = CommaFirstPass.apply(input);

        // Trailing comma before closing paren should stay
        assertTrue(output.contains("first,"), "trailing comma before ) should stay: " + output);
    }

    @Test
    void handlesBlockCommentsAcrossLines()
    {
        final String input = "/* this is,\n"
                + "   a comment */\n"
                + "method(a, b)";
        final String output = CommaFirstPass.apply(input);

        assertTrue(output.contains("/* this is,"), "comment should be unchanged: " + output);
    }

    @Test
    void handlesTextBlocks()
    {
        // Text block containing commas should not be transformed
        final String input = "String s = \"\"\"\n"
                + "    first,\n"
                + "    second\n"
                + "    \"\"\";";
        final String output = CommaFirstPass.apply(input);

        assertEquals(input, output, "text block content should not be changed");
    }

    @Test
    void handlesLineCommentAfterComma()
    {
        final String input = "method(\n"
                + "    first, // comment\n"
                + "    second\n"
                + ")";
        final String output = CommaFirstPass.apply(input);

        // The comma after 'first' should move to lead 'second'
        assertTrue(output.contains(", second"), "comma should lead 'second': " + output);
    }
}
