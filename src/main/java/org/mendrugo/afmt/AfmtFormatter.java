package org.mendrugo.afmt;

/**
 * Composes the formatting pipeline for Attimo's code style.
 *
 * <p>The pipeline runs three passes in sequence:
 * <ol>
 *   <li><strong>Eclipse JDT</strong> — Allman braces, 4-space indent, structural formatting,
 *       brace enforcement</li>
 *   <li><strong>FinalLocals</strong> — inserts {@code final} on effectively-final local
 *       variables and method parameters</li>
 *   <li><strong>CommaFirst</strong> — transforms trailing commas in multi-line lists to
 *       leading commas on continuation lines (Elm-style)</li>
 * </ol>
 *
 * <p>After the three passes, the Eclipse JDT formatter runs once more to normalize
 * any whitespace disturbed by the {@code final} insertions.
 */
public final class AfmtFormatter
{
    private final EclipseFormatter eclipse;

    public AfmtFormatter()
    {
        this.eclipse = new EclipseFormatter();
    }

    /**
     * Formats a Java source string according to Attimo's style.
     *
     * @param source the input Java source
     * @return the formatted source
     */
    public String format(final String source)
    {
        // Pass 1: Insert braces around unbraced control bodies
        String result = BraceInsertionPass.apply(source);

        // Pass 2: Eclipse JDT — structural formatting (Allman braces, indent)
        result = eclipse.format(result);

        // Pass 3: Insert 'final' on effectively-final locals and parameters
        result = FinalLocalsPass.apply(result);

        // Re-format after 'final' insertion to fix indentation
        result = eclipse.format(result);

        // Pass 4: Comma-first transformation
        result = CommaFirstPass.apply(result);

        return result;
    }
}
