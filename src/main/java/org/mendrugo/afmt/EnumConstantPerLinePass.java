package org.mendrugo.afmt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Flags;

/**
 * Places each enum constant on its own line, regardless of line length.
 *
 * <p>Given an enum with constants on one line:
 * <pre>{@code
 * private enum LexicalState
 * {
 *     NORMAL, STRING, CHAR, TEXT_BLOCK, BLOCK_COMMENT
 * }
 * }</pre>
 *
 * <p>This pass produces:
 * <pre>{@code
 * private enum LexicalState
 * {
 *     NORMAL,
 *     STRING,
 *     CHAR,
 *     TEXT_BLOCK,
 *     BLOCK_COMMENT
 * }
 * }</pre>
 *
 * <p>The trailing commas are subsequently transformed to leading commas
 * by {@link CommaFirstPass}. If the enum has additional members (methods,
 * constructors, fields), the terminating {@code ;} is placed on its own line.
 */
final class EnumConstantPerLinePass
{
    private EnumConstantPerLinePass() {}

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

        final var fileObject = new FinalLocalsPass.SimpleSourceFileObject("EnumTarget.java", source);
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

        final var collector = new EnumCollector(cu, source);
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

    private static final class EnumCollector extends TreeScanner<Void, Void>
    {
        final List<Replacement> replacements = new ArrayList<>();
        private final CompilationUnitTree cu;
        private final String source;

        EnumCollector(CompilationUnitTree cu, String source)
        {
            this.cu = cu;
            this.source = source;
        }

        @Override
        public Void visitClass(ClassTree node, Void unused)
        {
            if (node.getKind() == Tree.Kind.ENUM)
            {
                tryWrapConstants(node);
            }
            return super.visitClass(node, unused);
        }

        private void tryWrapConstants(ClassTree enumTree)
        {
            final var constants = new ArrayList<Tree>();
            for (final var member : enumTree.getMembers())
            {
                if (isEnumConstant(member))
                {
                    constants.add(member);
                }
            }

            if (constants.isEmpty())
            {
                return;
            }

            final var constTexts = new ArrayList<String>();
            for (final var c : constants)
            {
                final int cStart = startPos(c);
                final int cEnd = endPos(c);
                if (cStart < 0 || cEnd < 0)
                {
                    return;
                }
                constTexts.add(source.substring(cStart, cEnd).strip());
            }

            final int firstStart = startPos(constants.get(0));
            int replaceEnd = endPos(constants.get(constants.size() - 1));

            if (firstStart < 0 || replaceEnd < 0)
            {
                return;
            }

            int lineStart = firstStart;
            while (lineStart > 0 && source.charAt(lineStart - 1) != '\n')
            {
                lineStart--;
            }
            final String indent = leadingSpaces(lineStart);

            boolean hasSemicolon = false;
            int pos = replaceEnd;
            while (pos < source.length() && Character.isWhitespace(source.charAt(pos)))
            {
                pos++;
            }
            if (pos < source.length() && source.charAt(pos) == ',')
            {
                pos++;
                replaceEnd = pos;
                while (pos < source.length() && Character.isWhitespace(source.charAt(pos)))
                {
                    pos++;
                }
            }
            if (pos < source.length() && source.charAt(pos) == ';')
            {
                hasSemicolon = true;
                replaceEnd = pos + 1;
            }

            final var wrapped = new StringBuilder();
            for (int i = 0; i < constTexts.size(); i++)
            {
                if (i > 0)
                {
                    wrapped.append(indent);
                }
                wrapped.append(constTexts.get(i));
                if (i < constTexts.size() - 1)
                {
                    wrapped.append(",");
                }
                wrapped.append("\n");
            }
            if (hasSemicolon)
            {
                wrapped.append(indent).append(";");
            }
            else
            {
                wrapped.setLength(wrapped.length() - 1);
            }

            replacements.add(new Replacement(firstStart, replaceEnd, wrapped.toString()));
        }

        private boolean isEnumConstant(Tree member)
        {
            return member instanceof com.sun.tools.javac.tree.JCTree.JCVariableDecl varDecl
                && (varDecl.mods.flags & Flags.ENUM) != 0;
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
            final var sb = new StringBuilder();
            for (int i = lineStart; i < source.length() && source.charAt(i) == ' '; i++)
            {
                sb.append(' ');
            }
            return sb.toString();
        }
    }
}
