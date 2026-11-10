# FSTCompiler.add passes the input's length where Arrays.mismatch expects an end index, so an IntsRef input with a non-zero offset gets a too-short shared prefix and the compiled FST silently lacks that key (unreleased regression on main from #13924)

Finding LFV-1416 · Area: util / FST (FSTCompiler.add: the shared-prefix computation) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1416.html](../../bugs/LFV-1416.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an FST with FSTCompiler, add "ab" -&gt; 1, then add "ac" -&gt; 2 passed as an IntsRef slice that starts at offset 2 of a larger int[] (new IntsRef(new int[] {0, 0, 'a', 'c'}, 2, 2)), compile, and look up "ac"; IntsRef is documented as a slice (offset + length), add() only requires IntsRef#compareTo order, and 10.6.0 returns 2. On main, add() computes the shared prefix with Arrays.mismatch(lastInput.ints(), 0, lastInput.length(), input.ints, input.offset, input.length), whose last argument is an exclusive end index, so it compares an empty range, freezes the 'a' node of "ab" too early and gives the root a second arc labelled 'a'. With assertions off compile() returns normally and Util.get("ac") returns null ("ab" still answers 1); with -ea an assertion fires inside add().

## Expected vs actual

- Expected: `Util.get(fst, "ac")` returns 2.
- Actual: `null` with assertions off (`expected:<2> but was:<null>`); an `AssertionError` inside `add()` with assertions on.

## Files

- [src/org/apache/lucene/util/fst/TestFSTCompilerAddIntsRefSlice.java](src/org/apache/lucene/util/fst/TestFSTCompilerAddIntsRefSlice.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.fst.TestFSTCompilerAddIntsRefSlice
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFSTCompilerAddIntsRefSlice.java](TestFSTCompilerAddIntsRefSlice.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
