# FSTCompiler.compile's javadoc shows how to read back an FST written to a non-FSTReader DataOutput with 'fst = new FST&lt;&gt;(fstMetadata, dataInput, new OffHeapFSTStore)', but #13524 removed both the no-argument OffHeapFSTStore constructor and the 3-argument FST constructor in 9.12.0, so the example names APIs that no longer exist (documentation defect)

Bug LFV-988 · Area: core / util/fst (FSTCompiler.compile javadoc example) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The javadoc of FSTCompiler.compile explains how to turn its result into an FST, and for an FST written to an ordinary IndexOutput it shows 'fst = new FST&lt;&gt;(fstMetadata, dataInput, new OffHeapFSTStore);'. Copied into code on any release since 9.12.0 this does not compile: OffHeapFSTStore now needs (IndexInput, offset, metadata) and the 3-argument FST constructor no longer exists, because #13524 changed both and left this javadoc as it was. The working form is FST.fromFSTReader(fstMetadata, new OffHeapFSTStore(indexInput, offset, fstMetadata)), with a null check because compile returns null for an FST that accepts nothing; Lucene's own codecs and tests already use it, so only the javadoc is wrong.

The test shows that the off-heap example in the `FSTCompiler.compile()` javadoc (`fst = new FST<>(fstMetadata, dataInput, new OffHeapFSTStore());`, FSTCompiler.java:992-993) does not compile on main, because #13524 (9.12.0) removed the no-argument `OffHeapFSTStore` and the 3-argument `FST` constructors (a documentation defect).

## Expected vs actual

Expected: the example compiles. Actual: `javac errors in the FSTCompiler.compile() javadoc example expected:<[]> but was:<[constructor OffHeapFSTStore in class org.apache.lucene.util.fst.OffHeapFSTStore cannot be applied to given types;... found: no arguments...]>` (-ea and -da).

## Files

- [src/org/apache/lucene/util/fst/TestFSTCompilerCompileJavadocExample.java](src/org/apache/lucene/util/fst/TestFSTCompilerCompileJavadocExample.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.fst.TestFSTCompilerCompileJavadocExample
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/](../../models/javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
