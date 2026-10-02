package org.mendrugo.afmt;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms comma-last formatted Java source into comma-first (Elm-style) layout.
 *
 * <p>When a comma-separated list spans multiple lines, this pass moves trailing
 * commas to the beginning of the next line, producing:
 * <pre>{@code
 * public SpotRecommendation recommend(
 *     final IsaFeature feature
 *     , final String preferredRegion
 * )
 * }</pre>
 *
 * <p>The pass is lexically aware: it tracks string literals, character literals,
 * text blocks, and comments to avoid transforming commas inside them.
 */
final class CommaFirstPass
{
    private CommaFirstPass() {}

    static String apply(final String source)
    {
        // Apply iteratively until stable (moving a comma to line N may create
        // a new trailing comma on line N that also needs moving)
        String current = source;
        for (int pass = 0; pass < 50; pass++)
        {
            final String next = applyOnce(current);
            if (next.equals(current))
            {
                return current;
            }
            current = next;
        }
        return current;
    }

    private static String applyOnce(final String source)
    {
        final List<String> lines = splitLines(source);
        final List<String> result = new ArrayList<>(lines.size());

        // Compute lexical state at the START of each line
        final LexicalState[] stateAtLineStart = new LexicalState[lines.size() + 1];
        stateAtLineStart[0] = LexicalState.NORMAL;
        for (int i = 0; i < lines.size(); i++)
        {
            stateAtLineStart[i + 1] = advanceState(lines.get(i), stateAtLineStart[i]);
        }

        for (int i = 0; i < lines.size(); i++)
        {
            final String line = lines.get(i);
            final LexicalState lineStartState = stateAtLineStart[i];

            // If this line starts inside a block comment, text block, or string, pass through
            if (lineStartState != LexicalState.NORMAL)
            {
                result.add(line);
                continue;
            }

            // Check if this line ends with a comma (trailing whitespace/comment allowed)
            // but the comma must be in NORMAL lexical context
            final int commaPos = findTrailingCommaInNormalContext(line);
            if (commaPos < 0)
            {
                result.add(line);
                continue;
            }

            // Find the next non-blank line
            final int nextIdx = nextNonBlankLine(lines, i + 1);
            if (nextIdx < 0)
            {
                result.add(line);
                continue;
            }

            final String nextLine = lines.get(nextIdx);
            final String nextTrimmed = nextLine.stripLeading();

            // Don't transform if the next line starts with a closing delimiter
            if (nextTrimmed.startsWith(")")
                    || nextTrimmed.startsWith("]")
                    || nextTrimmed.startsWith("}")
                    || nextTrimmed.startsWith("//")
                    || nextTrimmed.startsWith("/*"))
            {
                result.add(line);
                continue;
            }

            // Move the comma: remove from end of this line, prepend to next line
            final String thisWithoutComma = line.substring(0, commaPos).stripTrailing()
                    + line.substring(commaPos + 1).stripTrailing();
            final int nextIndent = leadingSpaces(nextLine);
            final String nextContent = nextLine.stripLeading();

            // Place ", " at the same indentation as the continuation line
            // Adjust: comma goes at (indent - 2) to align visually
            final int commaIndent = Math.max(0, nextIndent - 2);
            final String transformedNext = " ".repeat(commaIndent) + ", " + nextContent;

            result.add(thisWithoutComma);

            // Copy blank lines between current and next
            for (int j = i + 1; j < nextIdx; j++)
            {
                result.add(lines.get(j));
            }
            result.add(transformedNext);
            i = nextIdx; // skip to after the transformed next line
        }

        return String.join("\n", result);
    }

    /**
     * Finds the position of a trailing comma on this line that is in NORMAL
     * lexical context (not inside a string, char, block comment, or line comment).
     * Returns -1 if no such comma exists.
     */
    private static int findTrailingCommaInNormalContext(final String line)
    {
        boolean inString = false;
        boolean inChar = false;
        boolean inBlockComment = false;
        int lastNormalComma = -1;

        for (int i = 0; i < line.length(); i++)
        {
            final char c = line.charAt(i);
            if (inBlockComment)
            {
                if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/')
                {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString)
            {
                if (c == '\\' && i + 1 < line.length())
                {
                    i++;
                }
                else if (c == '"')
                {
                    inString = false;
                }
            }
            else if (inChar)
            {
                if (c == '\\' && i + 1 < line.length())
                {
                    i++;
                }
                else if (c == '\'')
                {
                    inChar = false;
                }
            }
            else if (c == '"')
            {
                inString = true;
            }
            else if (c == '\'')
            {
                inChar = true;
            }
            else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/')
            {
                // Rest of line is a line comment; stop scanning
                break;
            }
            else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*')
            {
                inBlockComment = true;
                i++;
            }
            else if (c == ',')
            {
                lastNormalComma = i;
            }
        }

        // Check that everything after the last comma is whitespace or a line comment
        if (lastNormalComma >= 0)
        {
            final String after = line.substring(lastNormalComma + 1).stripLeading();
            if (after.isEmpty() || after.startsWith("//"))
            {
                return lastNormalComma;
            }
        }
        return -1;
    }

    private static int nextNonBlankLine(final List<String> lines, final int from)
    {
        for (int i = from; i < lines.size(); i++)
        {
            if (!lines.get(i).isBlank())
            {
                return i;
            }
        }
        return -1;
    }

    private static int leadingSpaces(final String line)
    {
        int count = 0;
        for (int i = 0; i < line.length() && line.charAt(i) == ' '; i++)
        {
            count++;
        }
        return count;
    }

    private static List<String> splitLines(final String source)
    {
        final List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < source.length(); i++)
        {
            if (source.charAt(i) == '\n')
            {
                lines.add(source.substring(start, i));
                start = i + 1;
            }
        }
        if (start <= source.length())
        {
            lines.add(source.substring(start));
        }
        return lines;
    }

    /**
     * Advances the lexical state through one line of source.
     * Returns the state at the END of the line (which is the start state for the next line).
     */
    private static LexicalState advanceState(final String line, LexicalState state)
    {
        for (int i = 0; i < line.length(); i++)
        {
            final char c = line.charAt(i);
            switch (state)
            {
                case NORMAL:
                    if (c == '"' && i + 2 < line.length()
                            && line.charAt(i + 1) == '"'
                            && line.charAt(i + 2) == '"')
                    {
                        state = LexicalState.TEXT_BLOCK;
                        i += 2;
                    }
                    else if (c == '"')
                    {
                        state = LexicalState.STRING;
                    }
                    else if (c == '\'')
                    {
                        state = LexicalState.CHAR;
                    }
                    else if (c == '/' && i + 1 < line.length())
                    {
                        if (line.charAt(i + 1) == '/')
                        {
                            return state; // rest of line is comment; state unchanged
                        }
                        else if (line.charAt(i + 1) == '*')
                        {
                            state = LexicalState.BLOCK_COMMENT;
                            i++;
                        }
                    }
                    break;
                case STRING:
                    if (c == '\\' && i + 1 < line.length())
                    {
                        i++;
                    }
                    else if (c == '"')
                    {
                        state = LexicalState.NORMAL;
                    }
                    break;
                case CHAR:
                    if (c == '\\' && i + 1 < line.length())
                    {
                        i++;
                    }
                    else if (c == '\'')
                    {
                        state = LexicalState.NORMAL;
                    }
                    break;
                case TEXT_BLOCK:
                    if (c == '"' && i + 2 < line.length()
                            && line.charAt(i + 1) == '"'
                            && line.charAt(i + 2) == '"')
                    {
                        state = LexicalState.NORMAL;
                        i += 2;
                    }
                    break;
                case BLOCK_COMMENT:
                    if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/')
                    {
                        state = LexicalState.NORMAL;
                        i++;
                    }
                    break;
            }
        }
        return state;
    }

    private enum LexicalState
    {
        NORMAL,
        STRING,
        CHAR,
        TEXT_BLOCK,
        BLOCK_COMMENT
    }
}
