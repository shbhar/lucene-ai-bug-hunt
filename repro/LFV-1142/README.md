# FSTCompiler.Builder.build() stores its lazily created default on-heap DataOutput in the Builder, so a second build() from the same Builder shares the first compiler's output and the second FST silently misses its own keys

Finding LFV-1142 · Area: util / FST (FSTCompiler.Builder.build: the default on-heap DataOutput) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1142.html](../../bugs/LFV-1142.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create one FSTCompiler.Builder (BYTE1, PositiveIntOutputs, no dataOutput set), build() a compiler, add a -&gt; 1 and read the FST back with FST.fromFSTReader(compile(), getFSTReader()) as compile()'s javadoc shows, then call build() again on the same Builder for a second FST with b -&gt; 2; you expect two independent FSTs, as two Builders give and as the same code gave up to 9.9.2. Instead the second FST returns null for b with assertions off (the first still answers a -&gt; 1), and with -ea the second compile() fails on an assert in ReadWriteDataOutput.writeByte. build() writes its default output into the Builder's own dataOutput field, so the second compiler appends to the first compiler's already frozen output while numbering its node addresses from 1, and its root points into the first FST's bytes.

## Expected vs actual

- Expected: FST 2 maps `b` to 2: `Util.get(fst2, "b")` returns 2.
- Actual: Assertions off: `expected:<2> but was:<null>`; assertions on: `AssertionError` at `ReadWriteDataOutput.writeByte:52` inside the second `compile()`.

## Files

- [src/org/apache/lucene/util/fst/TestFSTCompilerBuilderReuse.java](src/org/apache/lucene/util/fst/TestFSTCompilerBuilderReuse.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.fst.TestFSTCompilerBuilderReuse
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFSTCompilerBuilderReuse.java](TestFSTCompilerBuilderReuse.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
