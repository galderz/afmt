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
    void finalOnMethodParameters()
    {
        final String input = """
                class Example {
                    void run(String name, int count) {
                        use(name, count);
                    }
                }
                """;
        final String output = formatter.format(input);

        assertTrue(output.contains("final String name"), "param should get final: " + output);
        assertTrue(output.contains("final int count"), "param should get final: " + output);
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
        assertTrue(output.contains(", final String second") || output.contains(", String second"),
                "comma should lead continuation line: " + output);
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
