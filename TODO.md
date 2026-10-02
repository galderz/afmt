# TODO

## Wildcard import expansion

Attimo's style forbids wildcard imports (`import java.util.*`).
Currently `afmt` passes them through unchanged because Eclipse JDT's formatter does not expand or remove wildcards — it only reorders existing import statements.

### What needs to happen

Expand `import java.util.*` into individual imports for each type actually used in the compilation unit, e.g.:

```java
// before
import java.util.*;

// after
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
```

### Why it is hard

Wildcard expansion requires **compile-context resolution**: the formatter must know which types from `java.util` are actually referenced in the source.
A simple text search is insufficient because:

- The same simple name can exist in multiple wildcard-imported packages (ambiguity)
- Fully-qualified references (`java.time.Duration`) should be shortened only when unambiguous
- Inner classes, static imports, and module imports add further resolution complexity
- Types referenced only in Javadoc `@link`/`@see` tags must also be considered

jfmt solves this with `ImportNormalizer`, which invokes the javac compiler's `Elements` API to perform full attribution (name resolution) on the source files, then rewrites imports based on the resolved types.

### Approach options

1. **Port jfmt's `ImportNormalizer`** — Adapt the ~800-line `ImportNormalizer.java` from jfmt, which uses `javax.tools.JavaCompiler` to parse and attribute the source, then walks the AST collecting type references.
   This is the most complete solution but requires a compilation classpath to resolve all types.

2. **Javac parse-only heuristic** — Parse with javac (no attribution), collect all `IdentifierTree` and `MemberSelectTree` nodes, match them against known types from the wildcard packages using the JDK's `javax.lang.model` API.
   Simpler but cannot resolve ambiguous names or non-JDK types.

3. **Integrate OpenRewrite's `NoStaticImport` / `RemoveUnusedImports`** — OpenRewrite has wildcard expansion recipes, but brings a large dependency tree and currently lacks a `rewrite-java-25` module.

### Suggested implementation

Option 1 is the most robust.
The new pass (`WildcardExpansionPass`) would:

1. Accept optional compiler options (`--class-path`, `--module-path`, `--source-path`) through the CLI, matching jfmt's interface
2. Use `javax.tools.JavaCompiler.getTask()` to parse and attribute the source
3. Walk the attributed AST to collect all referenced type names
4. Replace each wildcard import with the specific types it resolves to
5. Remove any imports that are unused after expansion
6. Run before the Eclipse JDT formatter so that import ordering is applied to the expanded imports

### Files to change

- `Afmt.java` — add `--class-path`, `--module-path`, `--source-path` options
- `AfmtFormatter.java` — add `WildcardExpansionPass` as pass 0 (before brace insertion)
- `WildcardExpansionPass.java` — new file, ~400–800 lines
- `EclipseFormatter.java` — optionally configure import ordering groups (java/javax, third-party, static)

### Acceptance criteria

- `import java.util.*` is expanded to individual imports for each used type
- Unused imports are removed
- Fully-qualified type references (`java.time.Duration`) are shortened and a corresponding import is added
- Static wildcard imports (`import static org.junit.jupiter.api.Assertions.*`) are expanded
- Sources that cannot be attributed (missing classpath) are left unchanged with a diagnostic
- Existing tests continue to pass
- New tests cover: single wildcard, multiple wildcards, ambiguous names, static wildcards, no-op on already-expanded imports
