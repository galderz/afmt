package org.mendrugo.afmt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the final-insertion pass.
 */
class FinalLocalsPassTest
{
    @Test
    void insertsFinalOnEffectivelyFinalLocal()
    {
        final String input = """
                class Example {
                    void run() {
                        String name = "test";
                        System.out.println(name);
                    }
                }
                """;
        final String output = FinalLocalsPass.apply(input);

        assertTrue(output.contains("final String name"), "should insert final: " + output);
    }

    @Test
    void doesNotInsertFinalOnReassignedLocal()
    {
        final String input = """
                class Example {
                    void run() {
                        int count = 0;
                        count = count + 1;
                    }
                }
                """;
        final String output = FinalLocalsPass.apply(input);

        assertFalse(output.contains("final int count"), "should not insert final on reassigned var: " + output);
    }

    @Test
    void doesNotDoubleFinal()
    {
        final String input = """
                class Example {
                    void run() {
                        final String name = "test";
                    }
                }
                """;
        final String output = FinalLocalsPass.apply(input);

        assertFalse(output.contains("final final"), "should not double final: " + output);
    }

    @Test
    void insertsFinalOnMethodParameters()
    {
        final String input = """
                class Example {
                    void run(String name) {
                        System.out.println(name);
                    }
                }
                """;
        final String output = FinalLocalsPass.apply(input);

        assertTrue(output.contains("final String name"), "should insert final on param: " + output);
    }

    @Test
    void doesNotInsertFinalOnFields()
    {
        final String input = """
                class Example {
                    String name = "test";
                }
                """;
        final String output = FinalLocalsPass.apply(input);

        // Fields should NOT get final from this pass (it only handles locals)
        assertFalse(output.contains("final String name"), "should not insert final on field: " + output);
    }

    @Test
    void handlesVarDeclarations()
    {
        final String input = """
                class Example {
                    void run() {
                        var config = load();
                    }
                }
                """;
        final String output = FinalLocalsPass.apply(input);

        assertTrue(output.contains("final var config"), "should insert final on var: " + output);
    }

    @Test
    void doesNotInsertFinalOnIncrementedVariable()
    {
        final String input = """
                class Example {
                    void run() {
                        int i = 0;
                        i++;
                    }
                }
                """;
        final String output = FinalLocalsPass.apply(input);

        assertFalse(output.contains("final int i"), "should not insert final on incremented var: " + output);
    }

    @Test
    void doesNotInsertFinalOnCompoundAssigned()
    {
        final String input = """
                class Example {
                    void run() {
                        int total = 0;
                        total += 5;
                    }
                }
                """;
        final String output = FinalLocalsPass.apply(input);

        assertFalse(output.contains("final int total"), "should not insert final on compound-assigned var: " + output);
    }
}
