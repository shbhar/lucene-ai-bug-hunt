# JavascriptCompiler.compile lets the bytecode generator's method-size failure escape untranslated: a well-formed, shallowly nested expression that is too big for one JVM method throws ASM's MethodTooLargeException (or an ArrayIndexOutOfBoundsException from ASM's frame computation) on 8.5.0 to 10.5.2 and the 10.6.0 release line, and the class-file API's IllegalArgumentException on main, instead of the documented ParseException / IllegalStateException

Bug LFV-906 · Area: expressions / JavascriptCompiler code generation (compileExpression exception translation for class-file size limits) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You compile a well-formed expression that is too big for one JVM method but nested far below the 1024 limit, for example a balanced sum of 10,000 terms or a depth-12 decision tree, with JavascriptCompiler.compile; the javadoc says it throws ParseException 'on failure to compile' and, since 10.6.0 (#16423), IllegalStateException 'if the resulting expression class fails to link (e.g., complexity)', and the author of #16423 said in its review that bytecode-limit refusals now become IllegalStateException. Instead the bytecode generator's own size-limit exception escapes untranslated, because it is thrown before the class is defined and outside every translating catch: org.objectweb.asm.MethodTooLargeException on the 10.6.0 release line and 8.5.0 to 10.5.2 (plus ArrayIndexOutOfBoundsException 'Index -32768 out of bounds for length 40960' from ASM's frame computation when a 127-argument custom function is nested 130 or more deep), ASM's plain RuntimeException 'Method code too large!' on 4.6.0 to 8.4.1, and java.lang.IllegalArgumentException 'Code length N is outside the allowed range' from the JDK class-file API on main. Every expression that does compile evaluates correctly; only the exception type is wrong, and the main-only IllegalArgumentException leg is debatable (a JDK-documented type that maintainers may prefer to document rather than wrap).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The bytecode generator's own size-limit exception escapes untranslated, because it is thrown before the class is defined and outside every translating catch: org.objectweb.asm.MethodTooLargeException on the 10.6.0 release line and 8.5.0 to 10.5.2 (plus ArrayIndexOutOfBoundsException 'Index -32768 out of bounds for length 40960' from ASM's frame computation when a 127-argument custom function is nested 130 or more deep), ASM's plain RuntimeException 'Method code too large!' on 4.6.0 to 8.4.1, and java.lang.IllegalArgumentException 'Code length N is outside the allowed range' from the JDK class-file API on main.

## Files

- [src/org/apache/lucene/expressions/js/TestJavascriptCompilerBytecodeLimits.java](src/org/apache/lucene/expressions/js/TestJavascriptCompilerBytecodeLimits.java) (JUnit test)
- [src/ShallowLongSumMain.java](src/ShallowLongSumMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/expressions/build/libs/lucene-expressions-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.expressions.js.TestJavascriptCompilerBytecodeLimits

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" ShallowLongSumMain
```

The module(s) expressions also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/expressions-shallow-large-expression-escapes-documented-exceptions/](../../models/expressions-shallow-large-expression-escapes-documented-exceptions/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
