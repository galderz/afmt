package org.mendrugo.afmt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

/**
 * Wraps ternary conditional expressions when the line exceeds 80 characters.
 *
 * <p>Given a ternary that exceeds 80 characters:
 * <pre>{@code
 * final String outerIndent = indent.length() >= 4 ? "helllllllllllllllllllllllllllllllo" : indent;
 * }</pre>
 *
 * <p>This pass produces:
 * <pre>{@code
 * final String outerIndent = indent.length() >= 4
 *     ? "helllllllllllllllllllllllllllllllo"
 *     : indent;
 * }</pre>
 *
 * <p>The condition stays on the original line. The {@code ?} and {@code :}
 * branches each go on a new line, indented one level deeper than the
 * statement.
 */
final class TernaryWrapPass
{
    static final int MAX_LINE_LENGTH = 80;

    private TernaryWrapPass() {}

    static String apply(String source)
    {
        try
        {
            return doApply(source);
        }
        catch (Exception e)
        {
            return source;
        }
    }

    private static String doApply(String source) throws Exception
    {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
        {
            return source;
        }

        final var fileObject = new FinalLocalsPass.SimpleSourceFileObject("TernaryWrapTarget.java", source);
        final var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();

        final JavacTask task = (JavacTask) compiler.getTask(
            null
            , null
            , diagnostics
            , List.of("--enable-preview", "-source", "25")
            , null
            , List.of(fileObject)
        );

        final Iterable<? extends CompilationUnitTree> units;
        try
        {
            units = task.parse();
        }
        catch (Exception e)
        {
            return source;
        }

        final var cu = units.iterator().hasNext() ? units.iterator().next() : null;
        if (cu == null)
        {
            return source;
        }

        final var collector = new TernaryCollector(cu, source);
        collector.scan(cu, null);

        if (collector.replacements.isEmpty())
        {
            return source;
        }

        final var filtered = filterOverlapping(collector.replacements);

        filtered.sort(Comparator.comparingInt(Replacement::start).reversed());

        final var result = new StringBuilder(source);
        for (final var r : filtered)
        {
            result.replace(r.start, r.end, r.text);
        }
        return result.toString();
    }

    private static List<Replacement> filterOverlapping(List<Replacement> replacements)
    {
        replacements.sort(Comparator.comparingInt(Replacement::start));
        final var filtered = new ArrayList<Replacement>();
        for (final var r : replacements)
        {
            boolean overlaps = false;
            for (final var existing : filtered)
            {
                if (r.start >= existing.start && r.end <= existing.end)
                {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps)
            {
                filtered.add(r);
            }
        }
        return filtered;
    }

    private record Replacement(int start, int end, String text) {}

    private static final class TernaryCollector extends TreeScanner<Void, Void>
    {
        final List<Replacement> replacements = new ArrayList<>();
        private final CompilationUnitTree cu;
        private final String source;

        TernaryCollector(CompilationUnitTree cu, String source)
        {
            this.cu = cu;
            this.source = source;
        }

        @Override
        public Void visitConditionalExpression(ConditionalExpressionTree node, Void unused)
        {
            tryWrap(node);
            return super.visitConditionalExpression(node, unused);
        }

        private void tryWrap(ConditionalExpressionTree node)
        {
            final int condStart = startPos(node.getCondition());
            final int trueEnd = endPos(node.getTrueExpression());
            final int falseEnd = endPos(node.getFalseExpression());

            if (condStart < 0 || trueEnd < 0 || falseEnd < 0)
            {
                return;
            }

            final String condText = source.substring(condStart, endPos(node.getCondition())).strip();
            final String trueText = source.substring(startPos(node.getTrueExpression()), trueEnd).strip();
            final String falseText = source.substring(startPos(node.getFalseExpression()), falseEnd).strip();

            if (condText.contains("\n") || trueText.contains("\n") || falseText.contains("\n"))
            {
                return;
            }

            int lineStart = condStart;
            while (lineStart > 0 && source.charAt(lineStart - 1) != '\n')
            {
                lineStart--;
            }

            int lineEnd = falseEnd;
            while (lineEnd < source.length() && source.charAt(lineEnd) != '\n')
            {
                lineEnd++;
            }

            final String fullLine = source.substring(lineStart, lineEnd);
            if (fullLine.length() <= MAX_LINE_LENGTH)
            {
                return;
            }

            final String baseIndent = leadingSpaces(lineStart);
            final String wrapIndent = baseIndent + "    ";

            final var wrapped = new StringBuilder();
            wrapped.append(condText).append("\n");
            wrapped.append(wrapIndent).append("? ").append(trueText).append("\n");
            wrapped.append(wrapIndent).append(": ").append(falseText);

            replacements.add(new Replacement(condStart, falseEnd, wrapped.toString()));
        }

        private int startPos(Tree tree)
        {
            return (int) ((com.sun.tools.javac.tree.JCTree) tree).getStartPosition();
        }

        private int endPos(Tree tree)
        {
            return (int) ((com.sun.tools.javac.tree.JCTree) tree).getEndPosition(
                ((com.sun.tools.javac.tree.JCTree.JCCompilationUnit) cu).endPositions);
        }

        private String leadingSpaces(int lineStart)
        {
            final var indent = new StringBuilder();
            for (int i = lineStart; i < source.length() && source.charAt(i) == ' '; i++)
            {
                indent.append(' ');
            }
            return indent.toString();
        }
    }
}
