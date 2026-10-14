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
 * Wraps method/constructor parameters and method call arguments one-per-line
 * when the line exceeds 80 characters.
 *
 * <p>Applies to method declarations, constructor declarations, method calls,
 * and constructor invocations ({@code new}). Given a line that exceeds 80
 * characters:
 * <pre>{@code
 * final TextEdit edit = formatter.format(K_COMPILATION_UNIT, source, 0, source.length(), 0, "\n");
 * }</pre>
 *
 * <p>This pass produces:
 * <pre>{@code
 * final TextEdit edit = formatter.format(
 *     K_COMPILATION_UNIT,
 *     source,
 *     0,
 *     source.length(),
 *     0,
 *     "\n"
 * );
 * }</pre>
 *
 * <p>The trailing commas are subsequently transformed to leading commas
 * by {@link CommaFirstPass}.
 */
final class ParamPerLinePass
{
    static final int MAX_LINE_LENGTH = 80;

    private ParamPerLinePass() {}

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

        final var fileObject = new FinalLocalsPass.SimpleSourceFileObject("WrapTarget.java", source);
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

        final var collector = new ParamCollector(cu, source);
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

    private static final class ParamCollector extends TreeScanner<Void, Void>
    {
        final List<Replacement> replacements = new ArrayList<>();
        private final CompilationUnitTree cu;
        private final String source;

        ParamCollector(CompilationUnitTree cu, String source)
        {
            this.cu = cu;
            this.source = source;
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused)
        {
            final var params = node.getParameters();
            if (!params.isEmpty())
            {
                tryWrap(params);
            }
            return super.visitMethod(node, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused)
        {
            final var args = node.getArguments();
            if (!args.isEmpty())
            {
                tryWrap(args);
            }
            return super.visitMethodInvocation(node, unused);
        }

        @Override
        public Void visitNewClass(NewClassTree node, Void unused)
        {
            final var args = node.getArguments();
            if (!args.isEmpty())
            {
                tryWrap(args);
            }
            return super.visitNewClass(node, unused);
        }

        private void tryWrap(List<? extends Tree> elements)
        {
            final int firstStart = startPos(elements.get(0));
            final int lastEnd = endPos(elements.get(elements.size() - 1));

            if (firstStart < 0 || lastEnd < 0)
            {
                return;
            }

            int openParen = firstStart - 1;
            while (openParen >= 0 && source.charAt(openParen) != '(')
            {
                openParen--;
            }
            if (openParen < 0)
            {
                return;
            }

            int closeParen = lastEnd;
            while (closeParen < source.length() && source.charAt(closeParen) != ')')
            {
                closeParen++;
            }
            if (closeParen >= source.length())
            {
                return;
            }

            final var elementTexts = new ArrayList<String>();
            for (int i = 0; i < elements.size(); i++)
            {
                final int eStart = startPos(elements.get(i));
                final int eEnd = endPos(elements.get(i));
                if (eStart < 0 || eEnd < 0)
                {
                    return;
                }
                final String text = source.substring(eStart, eEnd).strip();
                if (text.contains("\n"))
                {
                    return;
                }
                elementTexts.add(text);
            }

            int lineStart = openParen;
            while (lineStart > 0 && source.charAt(lineStart - 1) != '\n')
            {
                lineStart--;
            }

            final String baseIndent = leadingSpaces(lineStart);
            final String paramIndent = baseIndent + "    ";

            final String prefix = source.substring(lineStart, openParen);
            final String singleLine = prefix + "(" + String.join(", ", elementTexts) + ")";

            if (singleLine.length() <= MAX_LINE_LENGTH)
            {
                return;
            }

            final var wrapped = new StringBuilder();
            wrapped.append("(\n");
            for (int i = 0; i < elementTexts.size(); i++)
            {
                wrapped.append(paramIndent).append(elementTexts.get(i));
                if (i < elementTexts.size() - 1)
                {
                    wrapped.append(",");
                }
                wrapped.append("\n");
            }
            wrapped.append(baseIndent).append(")");

            replacements.add(new Replacement(openParen, closeParen + 1, wrapped.toString()));
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
