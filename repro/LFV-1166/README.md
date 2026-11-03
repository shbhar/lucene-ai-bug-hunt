# Outputs.skipFinalOutput's default calls skipOutput, not readFinalOutput as its javadoc says, so an Outputs subclass with its own final-output encoding makes Util.get and FSTEnum.seekExact silently miss keys that are in the FST

Finding LFV-1166 · Area: util / FST (Outputs.skipFinalOutput default; FST.findTargetArc list-node scan) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1166.html](../../bugs/LFV-1166.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write an Outputs&lt;Long&gt; that delegates everything to PositiveIntOutputs except writeFinalOutput and readFinalOutput, which store final outputs as a fixed 8-byte long, and you keep skipFinalOutput, whose javadoc says it 'defaults to just calling readFinalOutput and discarding the result'; you build an FST from a=5, ab=3, b=7 with the default FSTCompiler.Builder and call Util.get(fst, "b"). You expect 7, but get null, with or without assertions (BytesRefFSTEnum.seekExact("b") misses the same way). The default body actually calls skipOutput, the regular-output decoder, so the linear scan of the root list node in FST.findTargetArc skips one byte of arc a's 8-byte final output, loses its place in the node and gives up.

## Expected vs actual

- Expected: `Util.get(fst, "b")` returns 7.
- Actual: `expected:<7> but was:<null>`, with assertions on and off.

## Files

- [src/org/apache/lucene/util/fst/TestSkipFinalOutputDefault.java](src/org/apache/lucene/util/fst/TestSkipFinalOutputDefault.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.fst.TestSkipFinalOutputDefault
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSkipFinalOutputDefault.java](TestSkipFinalOutputDefault.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
