package org.mendrugo.afmt;

/**
 * Composes the formatting pipeline for Attimo's code style.
 *
 * <p>The pipeline runs five passes in sequence:
 * <ol>
 *   <li><strong>BraceInsertion</strong> — inserts braces around unbraced control-flow
 *       bodies</li>
 *   <li><strong>Eclipse JDT</strong> — Allman braces, 4-space indent, structural formatting</li>
 *   <li><strong>FinalLocals</strong> — inserts {@code final} on effectively-final local
 *       variables</li>
 *   <li><strong>Eclipse JDT</strong> (again) — re-normalizes whitespace after {@code final}
 *       insertions</li>
 *   <li><strong>ParamPerLine</strong> — wraps method/constructor parameters one-per-line
 *       when the declaration exceeds 80 characters</li>
 *   <li><strong>CommaFirst</strong> — transforms trailing commas in multi-line lists to
 *       leading commas on continuation lines (Elm-style)</li>
 * </ol>
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
    public String format(String source)
    {
        // Pass 1: Insert braces around unbraced control bodies
        String result = BraceInsertionPass.apply(source);

        // Pass 2: Eclipse JDT — structural formatting (Allman braces, indent)
        result = eclipse.format(result);

        // Pass 3: Insert 'final' on effectively-final locals and parameters
        result = FinalLocalsPass.apply(result);

        // Re-format after 'final' insertion to fix indentation
        result = eclipse.format(result);

        // Pass 4: Wrap method/constructor params one-per-line when declaration > 80 chars
        result = ParamPerLinePass.apply(result);

        // Pass 5: Comma-first transformation
        result = CommaFirstPass.apply(result);

        return result;
    }
}
