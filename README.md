# afmt

Java source formatter for [Attimo](https://github.com/galderz/attimo)'s Aeron-inspired, comma-first code style.

## Style

`afmt` implements the style described in Attimo's [CONTRIBUTING.md](https://github.com/galderz/attimo/blob/main/CONTRIBUTING.md#code-style):

| Rule | Description |
|------|-------------|
| **Allman braces** | Opening `{` on its own line |
| **`final` on locals** | `final var config = AttimoConfig.load();` (local variables only, not method/constructor/catch parameters) |
| **4-space indent** | No tabs |
| **Params per line** | Method/constructor declarations and method calls exceeding 80 chars get one parameter per line (single-argument calls are exempt) |
| **Comma-first** | `, secondArg` on a new line |
| **Condition per line** | Compound `if`/`while`/`do-while` conditions exceeding 80 chars split on `&&`/`\|\|` |
| **Ternary wrap** | Ternary expressions exceeding 80 chars split `?` and `:` onto separate lines |
| **Switch arrow body on new line** | Arrow-case body on a separate line, cases indented inside switch braces |
| **Braces always required** | Even for single-line `if`/`for`/`while` |
| **Comments preserved** | Javadoc, block comments, and line comments are never reformatted |

### Example

Given this input:

```java
public class SpotAdvisor {
    public String recommend(String feature, String preferred, boolean fallback) {
        var group = regions.stream()
                .filter(r -> r.startsWith(preferred))
                .collect(Collectors.toList());
        if (group.isEmpty()) {
            if (fallback)
                return preferred;
            return null;
        }
        return null;
    }
}
```

`afmt` produces:

```java
public class SpotAdvisor
{
    public String recommend(String feature, String preferred, boolean fallback)
    {
        final var group = regions.stream()
                .filter(r -> r.startsWith(preferred))
                .collect(Collectors.toList());
        if (group.isEmpty())
        {
            if (fallback)
            {
                return preferred;
            }
            return null;
        }
        return null;
    }
}
```

## Usage

```sh
# Format files in place
afmt File.java AnotherFile.java

# Check formatting without changing files
afmt --check File.java

# Format from stdin to stdout
afmt -

# Print help
afmt --help
```

## Building

Requires JDK 25 and Maven 3.9+.

```sh
mvn package
java -jar target/afmt-0.1.0-SNAPSHOT.jar --help
```

## Architecture

`afmt` uses a multi-pass formatting pipeline:

```
Source
  │
  ▼
┌─────────────────────────────────┐
│ 1. BraceInsertionPass           │  Insert braces around unbraced
│    (javac parser)               │  if/for/while/do bodies
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│ 2. Eclipse JDT Formatter        │  Allman braces, 4-space indent,
│    (org.eclipse.jdt.core)       │  structural whitespace formatting
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│ 3. FinalLocalsPass              │  Insert 'final' on effectively-final
│    (javac parser + AST walk)    │  local variables
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│ 4. Eclipse JDT Formatter        │  Re-format after 'final' insertion
│    (normalize indentation)      │  to fix whitespace
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│ 5. TernaryWrapPass              │  Wrap ternary expressions when
│    (javac parser + AST walk)    │  line > 80 chars
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│ 6. ParamPerLinePass             │  Wrap method/constructor params
│    (javac parser + AST walk)    │  one-per-line when line > 80 chars
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│ 7. ConditionWrapPass            │  Wrap compound boolean conditions
│    (javac parser + AST walk)    │  one-per-line when line > 80 chars
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│ 8. CommaFirstPass               │  Move trailing commas to leading
│    (lexical transformer)        │  position on continuation lines
└─────────────────────────────────┘
```

### Pass details

- **BraceInsertionPass** — Uses javac's parser to build an AST, walks `if`, `for`, `while`, and `do-while` statements, and inserts `{`/`}` around bodies that lack them.

- **Eclipse JDT Formatter** — The Eclipse JDT `CodeFormatter` with ~30 configured options for Allman brace placement on all constructs (classes, methods, blocks, switches, lambdas, records, etc.), `else`/`catch`/`finally` on new lines, 4-space indentation with spaces only. Switch `case` labels are indented inside the switch braces, and arrow-case bodies are always placed on a separate line. Comments (Javadoc, block, and line) are left untouched — the formatter does not reformat or re-wrap comment content.

- **FinalLocalsPass** — Uses javac's parser to find local variables that are effectively final (never reassigned, incremented, or compound-assigned). Inserts `final` keyword. Skips fields, method/constructor parameters, catch parameters, lambda parameters, and record components.

- **TernaryWrapPass** — Uses javac's parser to find ternary conditional expressions (`condition ? trueExpr : falseExpr`) whose line exceeds 80 characters. The condition stays on the original line; the `?` and `:` branches each go on a new line, indented one level deeper than the statement. Short ternaries that fit within 80 characters are left on a single line. Nested ternaries where one branch is itself a ternary are handled by filtering overlapping replacements. Runs before ParamPerLinePass so that ternary wrapping takes priority over method call argument splitting.

- **ParamPerLinePass** — Uses javac's parser to find method declarations, constructor declarations, method calls, and constructor invocations (`new`) whose parameter/argument list would exceed 80 characters on a single line. Wraps each parameter or argument onto its own line, indented one level deeper than the declaration or call. The closing `)` goes on its own line at the statement's indentation level. Method calls and constructor invocations with a single argument are exempt from wrapping. Runs after Eclipse JDT so the wrapping is not collapsed back, and before CommaFirstPass so trailing commas are converted to leading commas.

- **ConditionWrapPass** — Uses javac's parser to find `if`, `while`, and `do-while` statements whose compound boolean condition (`&&` or `||`) would exceed 80 characters on a single line. Flattens the top-level chain of the same operator and wraps each operand onto its own line. The first operand stays on the same line as the keyword; subsequent operands are prefixed with the operator and indented one level deeper. The closing `)` goes on its own line at the statement's indentation level. Conditions with a single operand are not wrapped.

- **CommaFirstPass** — Lexically aware transformer that tracks strings, characters, text blocks, and comments. Finds trailing commas on lines where the next continuation line is not a closing delimiter, and moves the comma to lead the next line. Applies iteratively until stable.

## Known limitations

- **Wildcard import expansion** is not implemented. Eclipse JDT's formatter does not expand `import java.util.*` into individual imports. This would require compile-context resolution similar to jfmt's `ImportNormalizer`. Wildcard imports pass through unchanged.

- **Import ordering** follows Eclipse JDT defaults. Custom import group ordering (e.g., separating `java.*` from third-party) is not yet configurable.

- **Comma-first alignment** places the comma at the same indentation as the first element. Complex nested expressions may not align perfectly in all cases.

- **`final` on for-each variables** — The pass adds `final` to enhanced for-loop variables (`for (final String s : list)`), which is correct Java but may not be desired by all style guides.

## License

Apache License 2.0
