# afmt

Java source formatter for [Attimo](https://github.com/galderz/attimo)'s Aeron-inspired, comma-first code style.

## Style

`afmt` implements the style described in Attimo's [CONTRIBUTING.md](https://github.com/galderz/attimo/blob/main/CONTRIBUTING.md#code-style):

| Rule | Description |
|------|-------------|
| **Allman braces** | Opening `{` on its own line |
| **`final` on locals** | `final var config = AttimoConfig.load();` |
| **4-space indent** | No tabs |
| **Comma-first** | `, secondArg` on a new line |
| **Braces always required** | Even for single-line `if`/`for`/`while` |

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
    public String recommend(final String feature, final String preferred, final boolean fallback)
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
│    (javac parser + AST walk)    │  local variables and method parameters
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
│ 5. CommaFirstPass               │  Move trailing commas to leading
│    (lexical transformer)        │  position on continuation lines
└─────────────────────────────────┘
```

### Pass details

- **BraceInsertionPass** — Uses javac's parser to build an AST, walks `if`, `for`, `while`, and `do-while` statements, and inserts `{`/`}` around bodies that lack them.

- **Eclipse JDT Formatter** — The Eclipse JDT `CodeFormatter` with ~30 configured options for Allman brace placement on all constructs (classes, methods, blocks, switches, lambdas, records, etc.), `else`/`catch`/`finally` on new lines, 4-space indentation with spaces only.

- **FinalLocalsPass** — Uses javac's parser to find local variables and method parameters that are effectively final (never reassigned, incremented, or compound-assigned). Inserts `final` keyword. Skips fields, lambda parameters, and record components.

- **CommaFirstPass** — Lexically aware transformer that tracks strings, characters, text blocks, and comments. Finds trailing commas on lines where the next continuation line is not a closing delimiter, and moves the comma to lead the next line. Applies iteratively until stable.

## Known limitations

- **Wildcard import expansion** is not implemented. Eclipse JDT's formatter does not expand `import java.util.*` into individual imports. This would require compile-context resolution similar to jfmt's `ImportNormalizer`. Wildcard imports pass through unchanged.

- **Import ordering** follows Eclipse JDT defaults. Custom import group ordering (e.g., separating `java.*` from third-party) is not yet configurable.

- **Comma-first alignment** uses a simple heuristic (indent - 2 spaces) for comma placement. Complex nested expressions may not align perfectly in all cases.

- **`final` on for-each variables** — The pass adds `final` to enhanced for-loop variables (`for (final String s : list)`), which is correct Java but may not be desired by all style guides.

## License

Apache License 2.0
