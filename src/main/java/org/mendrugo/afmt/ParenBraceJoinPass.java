package org.mendrugo.afmt;

/**
 * Joins a closing {@code )} and opening {@code {}} onto the same line when
 * they appear on consecutive lines and the {@code )} is on a line by itself.
 *
 * <p>This targets multi-line method declarations and compound conditions
 * produced by {@link ParamPerLinePass} and {@link ConditionWrapPass}, where
 * the Allman-style {@code {}} ends up on a separate line after the lone
 * {@code )}:
 * <pre>{@code
 *     public Void visit(
 *         EnhancedForLoopTree node
 *         , final Void unused
 *     )
 *     {
 * }</pre>
 *
 * <p>This pass produces:
 * <pre>{@code
 *     public Void visit(
 *         EnhancedForLoopTree node
 *         , final Void unused
 *     ) {
 * }</pre>
 *
 * <p>Single-line declarations where the {@code )} has other content on the
 * same line (e.g., {@code void run()}) are not affected — the Allman
 * {@code {}} remains on its own line for those.
 */
final class ParenBraceJoinPass
{
    private ParenBraceJoinPass() {}

    static String apply(String source)
    {
        final String[] lines = source.split("\n", -1);
        final var result = new StringBuilder();
        int i = 0;
        while (i < lines.length)
        {
            if (i + 1 < lines.length
                && isOnlyCloseParen(lines[i])
                && isOnlyOpenBrace(lines[i + 1]))
            {
                result.append(lines[i]).append(" {\n");
                i += 2;
            }
            else
            {
                result.append(lines[i]);
                if (i < lines.length - 1)
                {
                    result.append("\n");
                }
                i++;
            }
        }
        return result.toString();
    }

    private static boolean isOnlyCloseParen(String line)
    {
        return line.strip().equals(")");
    }

    private static boolean isOnlyOpenBrace(String line)
    {
        return line.strip().equals("{");
    }
}
