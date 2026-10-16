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
 * Wraps compound boolean conditions in {@code if}, {@code while}, and
 * {@code do-while} statements when the line exceeds 80 characters and the
 * condition contains more than one operand.
 *
 * <p>Given a condition that exceeds 80 characters:
 * <pre>{@code
 * if (node.getElseStatement() != null && node.getElseStatement().getKind() != Tree.Kind.IF)
 * }</pre>
 *
 * <p>This pass produces:
 * <pre>{@code
 * if (node.getElseStatement() != null
 *     && node.getElseStatement().getKind() != Tree.Kind.IF
 * )
 * }</pre>
 *
 * <p>The first condition stays on the same line as the keyword. Each subsequent
 * condition goes on a new line, prefixed with the operator ({@code &&} or
 * {@code ||}), indented one level deeper than the statement. The closing
 * {@code )} goes on its own line at the statement's indentation level.
 *
 * <p>Only the top-level chain of the same operator is split. Mixed operators
 * (e.g., {@code a && b || c}) are split at the outermost operator level only.
 */
final class ConditionWrapPass
{
    static final int MAX_LINE_LENGTH = 80;

    private ConditionWrapPass() {}

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

        final var fileObject = new FinalLocalsPass.SimpleSourceFileObject("CondWrapTarget.java", source);
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

        final var collector = new ConditionCollector(cu, source);
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

    private static final class ConditionCollector extends TreeScanner<Void, Void>
    {
        final List<Replacement> replacements = new ArrayList<>();
        private final CompilationUnitTree cu;
        private final String source;

        ConditionCollector(CompilationUnitTree cu, String source)
        {
            this.cu = cu;
            this.source = source;
        }

        @Override
        public Void visitIf(IfTree node, Void unused)
        {
            tryWrapCondition(node.getCondition());
            return super.visitIf(node, unused);
        }

        @Override
        public Void visitWhileLoop(WhileLoopTree node, Void unused)
        {
            tryWrapCondition(node.getCondition());
            return super.visitWhileLoop(node, unused);
        }

        @Override
        public Void visitDoWhileLoop(DoWhileLoopTree node, Void unused)
        {
            tryWrapCondition(node.getCondition());
            return super.visitDoWhileLoop(node, unused);
        }

        private void tryWrapCondition(ExpressionTree condition)
        {
            ExpressionTree unwrapped = condition;
            while (unwrapped instanceof ParenthesizedTree pt)
            {
                unwrapped = pt.getExpression();
            }

            if (!(unwrapped instanceof BinaryTree binary))
            {
                return;
            }

            final Tree.Kind operator = binary.getKind();
            if (operator != Tree.Kind.CONDITIONAL_AND && operator != Tree.Kind.CONDITIONAL_OR)
            {
                return;
            }

            final var conditions = flattenConditions(unwrapped, operator);
            if (conditions.size() < 2)
            {
                return;
            }

            final var condTexts = new ArrayList<String>();
            for (final var cond : conditions)
            {
                final int cStart = startPos(cond);
                final int cEnd = endPos(cond);
                if (cStart < 0 || cEnd < 0)
                {
                    return;
                }
                final String text = source.substring(cStart, cEnd).strip();
                if (text.contains("\n"))
                {
                    return;
                }
                condTexts.add(text);
            }

            final int firstCondStart = startPos(conditions.get(0));
            final int lastCondEnd = endPos(conditions.get(conditions.size() - 1));

            if (firstCondStart < 0 || lastCondEnd < 0)
            {
                return;
            }

            int openParen = firstCondStart - 1;
            while (openParen >= 0 && source.charAt(openParen) != '(')
            {
                openParen--;
            }
            if (openParen < 0)
            {
                return;
            }

            int closeParen = lastCondEnd;
            while (closeParen < source.length() && source.charAt(closeParen) != ')')
            {
                closeParen++;
            }
            if (closeParen >= source.length())
            {
                return;
            }

            int lineStart = openParen;
            while (lineStart > 0 && source.charAt(lineStart - 1) != '\n')
            {
                lineStart--;
            }

            final String baseIndent = leadingSpaces(lineStart);
            final String condIndent = baseIndent + "    ";

            final String operatorStr = (operator == Tree.Kind.CONDITIONAL_AND) ? "&&" : "||";

            final String prefix = source.substring(lineStart, openParen);
            final String singleLine = prefix + "("
                + String.join(" " + operatorStr + " ", condTexts) + ")";

            if (singleLine.length() <= MAX_LINE_LENGTH)
            {
                return;
            }

            final var wrapped = new StringBuilder();
            wrapped.append("(").append(condTexts.get(0)).append("\n");
            for (int i = 1; i < condTexts.size(); i++)
            {
                wrapped.append(condIndent)
                    .append(operatorStr).append(" ")
                    .append(condTexts.get(i)).append("\n");
            }
            wrapped.append(baseIndent).append(")");

            replacements.add(new Replacement(openParen, closeParen + 1, wrapped.toString()));
        }

        private List<ExpressionTree> flattenConditions(ExpressionTree expr, Tree.Kind operator)
        {
            if (expr instanceof BinaryTree binary && binary.getKind() == operator)
            {
                final var result = new ArrayList<ExpressionTree>();
                result.addAll(flattenConditions(binary.getLeftOperand(), operator));
                result.addAll(flattenConditions(binary.getRightOperand(), operator));
                return result;
            }
            return List.of(expr);
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
