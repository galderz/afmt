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
 * Inserts braces around unbraced control-flow bodies ({@code if}, {@code else},
 * {@code for}, {@code while}, {@code do-while}).
 *
 * <p>Uses the javac parser to identify braceless control bodies, then inserts
 * {@code &#123;} and {@code &#125;} at the correct source positions.
 */
final class BraceInsertionPass
{
    private BraceInsertionPass() {}

    static String apply(final String source)
    {
        try
        {
            return doApply(source);
        }
        catch (final Exception e)
        {
            return source;
        }
    }

    private static String doApply(final String source) throws Exception
    {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
        {
            return source;
        }

        final var fileObject = new FinalLocalsPass.SimpleSourceFileObject("BraceTarget.java", source);
        final var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();

        final JavacTask task = (JavacTask) compiler.getTask(
            null, null, diagnostics
            , List.of("--enable-preview", "-source", "25")
            , null, List.of(fileObject)
        );

        final Iterable<? extends CompilationUnitTree> units;
        try
        {
            units = task.parse();
        }
        catch (final Exception e)
        {
            return source;
        }

        final var cu = units.iterator().hasNext() ? units.iterator().next() : null;
        if (cu == null)
        {
            return source;
        }

        final var collector = new UnbracedBodyCollector(cu, source);
        collector.scan(cu, null);

        if (collector.insertions.isEmpty())
        {
            return source;
        }

        // Sort insertions by position descending so we can apply them without shifting
        collector.insertions.sort(Comparator.comparingInt(BraceInsertion::position).reversed());

        final var result = new StringBuilder(source);
        for (final var insertion : collector.insertions)
        {
            if (insertion.position >= 0 && insertion.position <= result.length())
            {
                result.insert(insertion.position, insertion.text);
            }
        }
        return result.toString();
    }

    private record BraceInsertion(int position, String text) {}

    private static final class UnbracedBodyCollector extends TreeScanner<Void, Void>
    {
        final List<BraceInsertion> insertions = new ArrayList<>();
        private final CompilationUnitTree cu;
        private final String source;

        UnbracedBodyCollector(final CompilationUnitTree cu, final String source)
        {
            this.cu = cu;
            this.source = source;
        }

        @Override
        public Void visitIf(final IfTree node, final Void unused)
        {
            insertBracesIfNeeded(node.getThenStatement());
            if (node.getElseStatement() != null
                    && node.getElseStatement().getKind() != Tree.Kind.IF)
            {
                insertBracesIfNeeded(node.getElseStatement());
            }
            return super.visitIf(node, unused);
        }

        @Override
        public Void visitForLoop(final ForLoopTree node, final Void unused)
        {
            insertBracesIfNeeded(node.getStatement());
            return super.visitForLoop(node, unused);
        }

        @Override
        public Void visitEnhancedForLoop(final EnhancedForLoopTree node, final Void unused)
        {
            insertBracesIfNeeded(node.getStatement());
            return super.visitEnhancedForLoop(node, unused);
        }

        @Override
        public Void visitWhileLoop(final WhileLoopTree node, final Void unused)
        {
            insertBracesIfNeeded(node.getStatement());
            return super.visitWhileLoop(node, unused);
        }

        @Override
        public Void visitDoWhileLoop(final DoWhileLoopTree node, final Void unused)
        {
            insertBracesIfNeeded(node.getStatement());
            return super.visitDoWhileLoop(node, unused);
        }

        private void insertBracesIfNeeded(final StatementTree stmt)
        {
            if (stmt == null || stmt.getKind() == Tree.Kind.BLOCK)
            {
                return;
            }

            final int startPos = (int) ((com.sun.tools.javac.tree.JCTree) stmt).getStartPosition();
            final int endPos = (int) ((com.sun.tools.javac.tree.JCTree) stmt).getEndPosition(
                    ((com.sun.tools.javac.tree.JCTree.JCCompilationUnit) cu).endPositions);

            if (startPos < 0 || endPos < 0 || endPos > source.length())
            {
                return;
            }

            // Find indentation: look backwards from startPos to find line start
            final String indent = findIndentAt(startPos);

            // Insert "{\n" + indent before the statement, and "\n" + outerIndent + "}" after
            final String outerIndent = indent.length() >= 4 ? indent.substring(0, indent.length() - 4) : indent;
            insertions.add(new BraceInsertion(endPos, "\n" + outerIndent + "}"));
            insertions.add(new BraceInsertion(startPos, "{\n" + indent));
        }

        private String findIndentAt(final int position)
        {
            int lineStart = position;
            while (lineStart > 0 && source.charAt(lineStart - 1) != '\n')
            {
                lineStart--;
            }
            final var indent = new StringBuilder();
            for (int i = lineStart; i < position && source.charAt(i) == ' '; i++)
            {
                indent.append(' ');
            }
            return indent.toString();
        }
    }
}
