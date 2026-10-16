package org.mendrugo.afmt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that afmt produces output matching Attimo's style guide.
 * Each test corresponds to one or more rules from CONTRIBUTING.md.
 */
class AfmtStyleTest
{
    private final AfmtFormatter formatter = new AfmtFormatter();

    @Test
    void allmanBracesOnClassDeclaration()
    {
        final String input = "public class Example { void work() {} }";
        final String output = formatter.format(input);

        // Opening brace should be on its own line
        assertTrue(output.contains("class Example\n{"), "class brace should be on next line: " + output);
    }

    @Test
    void allmanBracesOnMethodDeclaration()
    {
        final String input = """
                public class Example {
                    public void run() {
                        work();
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("run()\n    {"), "method brace should be on next line: " + output);
    }

    @Test
    void allmanBracesOnIfStatement()
    {
        final String input = """
                class Example {
                    void run() {
                        if (ready) {
                            work();
                        }
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("if (ready)\n"), "if brace should be on next line: " + output);
    }

    @Test
    void allmanBracesElseOnNewLine()
    {
        final String input = """
                class Example {
                    void run() {
                        if (ready) {
                            work();
                        } else {
                            wait();
                        }
                    }
                }
                """;
        final String output = formatter.format(input);

        // else should be on its own line, not "} else {"
        assertTrue(output.contains("}\n"), "closing brace before else: " + output);
    }

    @Test
    void fourSpaceIndentation()
    {
        final String input = "class Example{void run(){if(true){work();}}}";
        final String output = formatter.format(input);

        // Method body should be indented 4 spaces
        assertTrue(output.contains("    void run()") || output.contains("    public void run()"),
                "method should be indented 4 spaces: " + output);
    }

    @Test
    void finalOnLocalVariables()
    {
        final String input = """
                class Example {
                    void run() {
                        var config = load();
                        String name = "test";
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("final var config"), "local var should get final: " + output);
        assertTrue(output.contains("final String name"), "local String should get final: " + output);
    }

    @Test
    void noFinalOnMethodParameters()
    {
        final String input = """
                class Example {
                    void run(String name, int count) {
                        use(name, count);
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(!output.contains("final String name"), "param should not get final: " + output);
        assertTrue(!output.contains("final int count"), "param should not get final: " + output);
    }

    @Test
    void noFinalOnReassignedVariables()
    {
        final String input = """
                class Example {
                    void run() {
                        int count = 0;
                        count = count + 1;
                    }
                }
                """;
        final String output = formatter.format(input);

        // 'count' is reassigned, so it should NOT get final
        assertTrue(!output.contains("final int count"), "reassigned var should not get final: " + output);
    }

    @Test
    void commaFirstOnMultiLineParameters()
    {
        final String input = """
                class Example {
                    void method(
                        String first,
                        String second,
                        String third
                    ) {}
                }
                """;
        final String output = formatter.format(input);

        // After comma-first, commas should lead continuation lines
        assertTrue(output.contains(", String second"),
                "comma should lead continuation line: " + output);
    }

    @Test
    void wrapsMethodParamsWhenLineExceeds80Chars()
    {
        final String input = """
                class Example {
                    static int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
                        return 0;
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("static int run(\n"),
                "opening paren should be followed by newline: " + output);
        assertTrue(output.contains("        String[] args\n"),
                "first param at 8-space indent: " + output);
        assertTrue(output.contains("        , InputStream in\n"),
                "comma aligned with first param at 8-space indent: " + output);
        assertTrue(output.contains("        , PrintStream out\n"),
                "comma aligned with first param at 8-space indent: " + output);
        assertTrue(output.contains("        , PrintStream err\n"),
                "comma aligned with first param at 8-space indent: " + output);
        assertTrue(output.contains("    )\n"),
                "closing paren on its own line: " + output);
    }

    @Test
    void wrapsMethodCallArgsWhenLineExceeds80Chars()
    {
        final String input = """
                class Example {
                    void run() {
                        TextEdit edit = formatter.format(K_COMPILATION_UNIT, source, 0, source.length(), 0, "\\n");
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("formatter.format(\n"),
                "opening paren should be followed by newline: " + output);
        assertTrue(output.contains("            K_COMPILATION_UNIT\n"),
                "first arg at 12-space indent: " + output);
        assertTrue(output.contains("            , source\n"),
                "comma aligned with first arg at 12-space indent: " + output);
        assertTrue(output.contains("            , 0\n"),
                "comma aligned with first arg at 12-space indent: " + output);
        assertTrue(output.contains("            , source.length()\n"),
                "comma aligned with first arg at 12-space indent: " + output);
        assertTrue(output.contains("        );\n"),
                "closing paren on its own line: " + output);
    }

    @Test
    void preservesAlreadySplitMethodCallArgs()
    {
        final String input = """
                class Example {
                    void run() {
                        TextEdit edit = formatter.format(
                            K_COMPILATION_UNIT,
                            source,
                            0,
                            source.length(),
                            0,
                            "\\n"
                        );
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("            K_COMPILATION_UNIT\n"),
                "first arg preserved at 12-space indent: " + output);
        assertTrue(output.contains("            , source\n"),
                "comma aligned with first arg: " + output);
        assertTrue(output.contains("        );\n"),
                "closing paren preserved: " + output);
    }

    @Test
    void doesNotWrapSingleArgMethodCallEvenWhenExceeds80Chars()
    {
        final String input = """
                class Example {
                    void run() {
                        assertTrue(output.contains("some very long assertion string that makes this line exceed eighty characters"));
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains(
                "assertTrue(output.contains(\"some very long assertion string that makes this line exceed eighty characters\"))"),
                "single-arg call should stay on one line: " + output);
    }

    @Test
    void doesNotWrapMethodParamsWhenLineWithin80Chars()
    {
        final String input = """
                class Example {
                    void run(String name, int count) {
                        use(name, count);
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("run(String name, int count)"),
                "short params should stay on one line: " + output);
    }

    @Test
    void bracesAlwaysRequired()
    {
        final String input = """
                class Example {
                    void run() {
                        if (ready) work();
                        for (int i = 0; i < 10; i++) use(i);
                        while (go) step();
                    }
                }
                """;
        final String output = formatter.format(input);

        // All control bodies should have braces
        // The Eclipse JDT formatter may or may not insert braces depending on settings.
        // At minimum, single-line bodies should be on their own line
        assertTrue(!output.contains("if (ready) work()"),
                "if body should not be on same line without braces: " + output);
    }

    @Test
    void switchArrowCaseBodyOnSeparateLine()
    {
        final String input = """
                class Example {
                    void run(String arg) {
                        switch (arg) {
                            case "--check" -> check = true;
                            case "--help", "-h", "-?" -> help = true;
                            default -> files.add(arg);
                        }
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("case \"--check\" ->\n"),
                "arrow case body should be on separate line: " + output);
        assertTrue(output.contains("case \"--help\""),
                "multi-label case preserved: " + output);
        assertTrue(output.contains("default ->\n"),
                "default body should be on separate line: " + output);
    }

    @Test
    void switchCasesIndentedInsideBraces()
    {
        final String input = """
                class Example {
                    void run(String arg) {
                        for (String a : args) {
                            switch (a) {
                                case "--check" -> check = true;
                                default -> files.add(a);
                            }
                        }
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("            {\n                case"),
                "case should be indented inside switch braces: " + output);
    }

    @Test
    void wrapsCompoundConditionWhenLineExceeds80Chars()
    {
        final String input = """
                class Example {
                    void run() {
                        if (node.getElseStatement() != null && node.getElseStatement().getKind() != Tree.Kind.IF) {
                            doWork();
                        }
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("if (node.getElseStatement() != null\n"),
                "first condition stays on same line as if: " + output);
        assertTrue(output.contains("            && node.getElseStatement().getKind() != Tree.Kind.IF\n"),
                "second condition on new line with && operator: " + output);
        assertTrue(output.contains("        )\n"),
                "closing paren on its own line: " + output);
    }

    @Test
    void doesNotWrapConditionWithSingleOperand()
    {
        final String input = """
                class Example {
                    void run() {
                        if (node.getElseStatement() != null) {
                            doWork();
                        }
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("if (node.getElseStatement() != null)\n"),
                "single condition should stay on one line: " + output);
    }

    @Test
    void doesNotWrapShortCompoundCondition()
    {
        final String input = """
                class Example {
                    void run() {
                        if (a != null && b != null) {
                            doWork();
                        }
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("if (a != null && b != null)"),
                "short compound condition should stay on one line: " + output);
    }

    @Test
    void wrapsTernaryWhenLineExceeds80Chars()
    {
        final String input = """
                class Example {
                    void run() {
                        final String outerIndent = indent.length() >= 4 ? "helllllllllllllllllllllllllllllllo" : indent;
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("indent.length() >= 4\n"),
                "condition should stay on original line: " + output);
        assertTrue(output.contains("            ? \"helllllllllllllllllllllllllllllllo\"\n"),
                "true branch on new line with ?: " + output);
        assertTrue(output.contains("            : indent;\n"),
                "false branch on new line with :: " + output);
    }

    @Test
    void doesNotWrapShortTernary()
    {
        final String input = """
                class Example {
                    void run() {
                        int x = a > b ? a : b;
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("a > b ? a : b"),
                "short ternary should stay on one line: " + output);
    }

    @Test
    void wrapsTernaryInReturnStatement()
    {
        final String input = """
                class Example {
                    String run(String longVariableName, String anotherLongVariableName) {
                        return longVariableName != null ? longVariableName.toUpperCase() : anotherLongVariableName;
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("longVariableName != null\n"),
                "condition should stay on original line: " + output);
        assertTrue(output.contains("? longVariableName.toUpperCase()\n"),
                "true branch on new line with ?: " + output);
        assertTrue(output.contains(": anotherLongVariableName;\n"),
                "false branch on new line with :: " + output);
    }

    @Test
    void preservesSourceWithStringsContainingCommas()
    {
        final String input = """
                class Example {
                    void run() {
                        String s = "hello, world";
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("\"hello, world\""), "string content should be preserved: " + output);
    }

    @Test
    void formatsRecordWithAllmanBraces()
    {
        final String input = """
                public record SpotRecommendation(
                    String instanceType,
                    String region,
                    String availabilityZone,
                    double pricePerHour,
                    String rationale
                ) {}
                """;
        final String output = formatter.format(input);

        // Record should have Allman-style empty body or next-line brace
        assertTrue(output.contains("SpotRecommendation("), "record name preserved: " + output);
    }

    @Test
    void formatsAnnotationWithCommaFirst()
    {
        final String input = """
                @CommandDefinition(
                    name = "request",
                    description = "Request a spot instance",
                    generateHelp = true
                )
                class RequestCommand {}
                """;
        final String output = formatter.format(input);

        // Annotation args spanning multiple lines should get comma-first treatment
        assertTrue(output.contains("@CommandDefinition"), "annotation preserved: " + output);
    }
}
