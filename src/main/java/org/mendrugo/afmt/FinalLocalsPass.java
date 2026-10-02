package org.mendrugo.afmt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

/**
 * Inserts {@code final} on local variable declarations that are not already
 * {@code final} and are never reassigned (effectively final).
 *
 * <p>Uses the javac parser to build an AST, walks it to find local variables,
 * then performs a second walk to find assignments. Variables that are declared
 * without {@code final} and are never reassigned get {@code final} inserted.
 *
 * <p>This pass operates on source text using character offsets from the AST,
 * applying replacements in reverse order to preserve positions.
 */
final class FinalLocalsPass
{
    private FinalLocalsPass() {}

    static String apply(final String source)
    {
        try
        {
            return doApply(source);
        }
        catch (final Exception e)
        {
            // If parsing fails, return source unchanged
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

        final var fileObject = new SimpleSourceFileObject("FormattingTarget.java", source);
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
        catch (final Exception e)
        {
            return source;
        }

        final var compilationUnit = units.iterator().hasNext() ? units.iterator().next() : null;
        if (compilationUnit == null)
        {
            return source;
        }

        // Phase 1: collect local variable declarations without 'final'
        final var candidates = new ArrayList<LocalVarInfo>();
        final var scanner = new LocalVarCollector(compilationUnit);
        scanner.scan(compilationUnit, null);
        candidates.addAll(scanner.candidates);

        if (candidates.isEmpty())
        {
            return source;
        }

        // Phase 2: find all assigned variable names (simple name-based analysis)
        final var assignedNames = new HashSet<String>();
        final var assignmentScanner = new AssignmentCollector();
        assignmentScanner.scan(compilationUnit, null);
        assignedNames.addAll(assignmentScanner.assignedNames);

        // Phase 3: filter — only variables never reassigned and not already final
        final var insertions = new ArrayList<Integer>();
        for (final var candidate : candidates)
        {
            if (!assignedNames.contains(candidate.name))
            {
                insertions.add(candidate.insertPosition);
            }
        }

        if (insertions.isEmpty())
        {
            return source;
        }

        // Phase 4: apply insertions in reverse order
        // Sort descending so insertions don't shift positions
        insertions.sort((a, b) -> Integer.compare(b, a));

        final var result = new StringBuilder(source);
        for (final int pos : insertions)
        {
            if (pos >= 0 && pos <= result.length())
            {
                result.insert(pos, "final ");
            }
        }

        return result.toString();
    }

    private record LocalVarInfo(String name, int insertPosition) {}

    /**
     * Collects local variable declarations that are not already 'final'.
     * Only considers variables inside method/constructor/initializer blocks.
     */
    private static final class LocalVarCollector extends TreeScanner<Void, Void>
    {
        final List<LocalVarInfo> candidates = new ArrayList<>();
        private final CompilationUnitTree compilationUnit;
        private boolean insideMethod = false;
        private final java.util.Set<Long> seen = new java.util.HashSet<>();
        private final java.util.Set<Long> lambdaParamPositions = new java.util.HashSet<>();

        LocalVarCollector(final CompilationUnitTree cu)
        {
            this.compilationUnit = cu;
        }

        @Override
        public Void visitMethod(final MethodTree node, final Void unused)
        {
            final boolean wasInside = insideMethod;
            insideMethod = true;

            // Add 'final' to method parameters
            for (final VariableTree param : node.getParameters())
            {
                addCandidate(param);
            }

            super.visitMethod(node, unused);
            insideMethod = wasInside;
            return null;
        }

        @Override
        public Void visitLambdaExpression(final LambdaExpressionTree node, final Void unused)
        {
            // Mark lambda parameter positions so we skip them
            for (final VariableTree param : node.getParameters())
            {
                final long pos = getStartPosition(param);
                if (pos >= 0)
                {
                    lambdaParamPositions.add(pos);
                }
            }
            return super.visitLambdaExpression(node, unused);
        }

        @Override
        public Void visitVariable(final VariableTree node, final Void unused)
        {
            if (insideMethod)
            {
                addCandidate(node);
            }
            return super.visitVariable(node, unused);
        }

        private void addCandidate(final VariableTree node)
        {
            if (hasModifier(node, "final"))
            {
                return;
            }
            final long pos = getStartPosition(node);
            if (pos >= 0 && seen.add(pos) && !lambdaParamPositions.contains(pos))
            {
                candidates.add(new LocalVarInfo(node.getName().toString(), (int) pos));
            }
        }

        private long getStartPosition(final Tree tree)
        {
            return compilationUnit.getSourceFile() != null
                    ? ((com.sun.tools.javac.tree.JCTree) tree).getStartPosition()
                    : -1;
        }

        private static boolean hasModifier(final VariableTree node, final String modifier)
        {
            return node.getModifiers().getFlags().contains(
                    javax.lang.model.element.Modifier.valueOf(modifier.toUpperCase()));
        }
    }

    /**
     * Collects names of variables that are assigned (excluding their declaration initializer).
     */
    private static final class AssignmentCollector extends TreeScanner<Void, Void>
    {
        final Set<String> assignedNames = new HashSet<>();

        @Override
        public Void visitAssignment(final AssignmentTree node, final Void unused)
        {
            if (node.getVariable() instanceof IdentifierTree id)
            {
                assignedNames.add(id.getName().toString());
            }
            return super.visitAssignment(node, unused);
        }

        @Override
        public Void visitCompoundAssignment(final CompoundAssignmentTree node, final Void unused)
        {
            if (node.getVariable() instanceof IdentifierTree id)
            {
                assignedNames.add(id.getName().toString());
            }
            return super.visitCompoundAssignment(node, unused);
        }

        @Override
        public Void visitUnary(final UnaryTree node, final Void unused)
        {
            // ++x, x++, --x, x-- are mutations
            switch (node.getKind())
            {
                case PREFIX_INCREMENT, PREFIX_DECREMENT,
                     POSTFIX_INCREMENT, POSTFIX_DECREMENT:
                    if (node.getExpression() instanceof IdentifierTree id)
                    {
                        assignedNames.add(id.getName().toString());
                    }
                    break;
                default:
                    break;
            }
            return super.visitUnary(node, unused);
        }

        @Override
        public Void visitForLoop(final ForLoopTree node, final Void unused)
        {
            // The update part of for(;;update) contains assignments
            return super.visitForLoop(node, unused);
        }
    }

    /**
     * Simple in-memory source file for javac parsing.
     */
    static final class SimpleSourceFileObject extends javax.tools.SimpleJavaFileObject
    {
        private final String content;

        SimpleSourceFileObject(final String name, final String content)
        {
            super(java.net.URI.create("string:///" + name), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(final boolean ignoreEncodingErrors)
        {
            return content;
        }
    }
}
