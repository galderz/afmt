package org.mendrugo.afmt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

/**
 * Wraps method and constructor parameters one-per-line when the declaration
 * exceeds 80 characters.
 *
 * <p>Given a method declaration that exceeds 80 characters on a single line:
 * <pre>{@code
 * static int run(String[] args, InputStream in, PrintStream out, PrintStream err)
 * }</pre>
 *
 * <p>This pass produces:
 * <pre>{@code
 * static int run(
 *     String[] args,
 *     InputStream in,
 *     PrintStream out,
 *     PrintStream err
 * )
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

        final var collector = new MethodParamCollector(cu, source);
        collector.scan(cu, null);

        if (collector.replacements.isEmpty())
        {
            return source;
        }

        collector.replacements.sort(Comparator.comparingInt(Replacement::start).reversed());

        final var result = new StringBuilder(source);
        for (final var r : collector.replacements)
        {
            result.replace(r.start, r.end, r.text);
        }
        return result.toString();
    }

    private record Replacement(int start, int end, String text) {}

    private static final class MethodParamCollector extends TreeScanner<Void, Void>
    {
        final List<Replacement> replacements = new ArrayList<>();
        private final CompilationUnitTree cu;
        private final String source;

        MethodParamCollector(CompilationUnitTree cu, String source)
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
                tryWrapParams(params);
            }
            return super.visitMethod(node, unused);
        }

        private void tryWrapParams(List<? extends VariableTree> params)
        {
            final int firstParamStart = startPos(params.get(0));
            final int lastParamEnd = endPos(params.get(params.size() - 1));

            if (firstParamStart < 0 || lastParamEnd < 0)
            {
                return;
            }

            int openParen = firstParamStart - 1;
            while (openParen >= 0 && source.charAt(openParen) != '(')
            {
                openParen--;
            }
            if (openParen < 0)
            {
                return;
            }

            int closeParen = lastParamEnd;
            while (closeParen < source.length() && source.charAt(closeParen) != ')')
            {
                closeParen++;
            }
            if (closeParen >= source.length())
            {
                return;
            }

            final var paramTexts = new ArrayList<String>();
            for (int i = 0; i < params.size(); i++)
            {
                final int pStart = startPos(params.get(i));
                final int pEnd = endPos(params.get(i));
                if (pStart < 0 || pEnd < 0)
                {
                    return;
                }
                paramTexts.add(source.substring(pStart, pEnd).strip());
            }

            int lineStart = openParen;
            while (lineStart > 0 && source.charAt(lineStart - 1) != '\n')
            {
                lineStart--;
            }

            final String baseIndent = leadingSpaces(lineStart);
            final String paramIndent = baseIndent + "    ";

            final String prefix = source.substring(lineStart, openParen);
            final String singleLine = prefix + "(" + String.join(", ", paramTexts) + ")";

            if (singleLine.length() <= MAX_LINE_LENGTH)
            {
                return;
            }

            final var wrapped = new StringBuilder();
            wrapped.append("(\n");
            for (int i = 0; i < paramTexts.size(); i++)
            {
                wrapped.append(paramIndent).append(paramTexts.get(i));
                if (i < paramTexts.size() - 1)
                {
                    wrapped.append(",");
                }
                wrapped.append("\n");
            }
            wrapped.append(baseIndent).append(")");

            replacements.add(new Replacement(openParen, closeParen + 1, wrapped.toString()));
        }

        private int startPos(VariableTree param)
        {
            return (int) ((com.sun.tools.javac.tree.JCTree) param).getStartPosition();
        }

        private int endPos(VariableTree param)
        {
            return (int) ((com.sun.tools.javac.tree.JCTree) param).getEndPosition(
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
