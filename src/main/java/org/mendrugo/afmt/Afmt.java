package org.mendrugo.afmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line entry point for afmt.
 *
 * <p>Usage mirrors jfmt:
 * <pre>{@code
 * afmt File.java AnotherFile.java     # format files in place
 * afmt --check File.java              # check without changing files
 * afmt -                              # read stdin, write stdout
 * }</pre>
 */
public final class Afmt
{
    private Afmt() {}

    public static void main(final String[] args)
    {
        System.exit(run(args, System.in, System.out, System.err));
    }

    static int run(final String[] args, final InputStream in, final PrintStream out, final PrintStream err)
    {
        if (args.length == 0)
        {
            printUsage(err);
            return 1;
        }

        boolean check = false;
        boolean help = false;
        final List<String> files = new ArrayList<>();

        for (final String arg : args)
        {
            switch (arg)
            {
                case "--check" -> check = true;
                case "--help", "-h", "-?" -> help = true;
                default -> files.add(arg);
            }
        }

        if (help)
        {
            printHelp(out);
            return 0;
        }

        final AfmtFormatter formatter = new AfmtFormatter();

        // stdin mode
        if (files.size() == 1 && "-".equals(files.getFirst()))
        {
            return formatStdin(formatter, in, out, err);
        }

        if (files.isEmpty())
        {
            err.println("afmt: no files specified");
            return 1;
        }

        boolean hasErrors = false;
        boolean hasUnformatted = false;

        for (final String file : files)
        {
            final Path path = Path.of(file);
            if (!Files.isRegularFile(path))
            {
                err.println(path + ": not a file");
                hasErrors = true;
                continue;
            }

            try
            {
                final String input = Files.readString(path, StandardCharsets.UTF_8);
                final String output = formatter.format(input);

                if (check)
                {
                    if (!input.equals(output))
                    {
                        err.println(path + ": not formatted");
                        hasUnformatted = true;
                    }
                }
                else if (!input.equals(output))
                {
                    Files.writeString(path, output, StandardCharsets.UTF_8);
                }
            }
            catch (final IOException e)
            {
                err.println(path + ": " + e.getMessage());
                hasErrors = true;
            }
            catch (final Exception e)
            {
                err.println(path + ": formatting failed: " + e.getMessage());
                hasErrors = true;
            }
        }

        if (hasErrors)
        {
            return 1;
        }
        return check && hasUnformatted ? 1 : 0;
    }

    private static int formatStdin(final AfmtFormatter formatter, final InputStream in
            , final PrintStream out, final PrintStream err)
    {
        try
        {
            final String input = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            final String output = formatter.format(input);
            out.print(output);
            out.flush();
            return 0;
        }
        catch (final IOException e)
        {
            err.println("<stdin>: " + e.getMessage());
            return 1;
        }
    }

    private static void printUsage(final PrintStream err)
    {
        err.println("Usage: afmt [--check] <files...>");
        err.println("       afmt -");
        err.println("       afmt --help");
    }

    private static void printHelp(final PrintStream out)
    {
        out.println("Usage: afmt [OPTIONS] FILE...");
        out.println();
        out.println("Format Java source using Attimo's Aeron-inspired, comma-first style.");
        out.println();
        out.println("Style rules:");
        out.println("  - Allman braces (opening { on its own line)");
        out.println("  - 4-space indentation, no tabs");
        out.println("  - final on local variables and method parameters");
        out.println("  - Comma-first on multi-line argument/parameter lists");
        out.println("  - No wildcard imports");
        out.println("  - Braces always required for if/for/while/do bodies");
        out.println();
        out.println("Arguments:");
        out.println("  FILE        Java source file(s) to format, or - for stdin");
        out.println();
        out.println("Options:");
        out.println("  --check     Check formatting without changing files");
        out.println("  --help, -h  Print this help message");
    }
}
