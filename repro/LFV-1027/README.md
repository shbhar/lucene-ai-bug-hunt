# The org.apache.lucene.util.fst package overview's code examples do not compile on any release since 9.0.0: they call Util.getByOutput (removed in 9.0.0), the public FSTCompiler(INPUT_TYPE, Outputs) constructor (removed in 9.9.0) and the Util.Result fields input/output (private record components since 10.0.0) (documentation defect)

Bug LFV-1027 · Area: core / util/fst (package overview examples, package-info.java) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The org.apache.lucene.util.fst package overview shows five usage examples (build an FST, look up by key, look up by value, iterate in sorted order, N-shortest paths) and the values they print. Copied into code on any release since 9.0.0 they do not compile: Util.getByOutput was removed in 9.0.0 (LUCENE-8638, which deleted the page's link to it but kept the code block), the public FSTCompiler(INPUT_TYPE, Outputs) constructor was removed in 9.9.0 (#12715; FSTCompiler.Builder is the API), and Util.Result became a record in 10.0.0 (#13328), so '.input' and '.output' are private and need input and output. With those three edits, dropping the lookup-by-value block because it has no replacement, the examples compile on main and print exactly the documented values, so only the javadoc is wrong.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/util/fst/TestFstPackageExamples.java](src/org/apache/lucene/util/fst/TestFstPackageExamples.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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
     execute --select-class org.apache.lucene.util.fst.TestFstPackageExamples
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/core-fst-package-examples-compile-and-print-documented-outputs/](../../models/core-fst-package-examples-compile-and-print-documented-outputs/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
