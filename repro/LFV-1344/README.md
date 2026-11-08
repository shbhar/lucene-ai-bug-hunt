# CharsRefBuilder.append(CharSequence, int, int) returns normally and appends nothing when start &gt; end, instead of throwing the IndexOutOfBoundsException that java.lang.Appendable, which the class implements, requires

Finding LFV-1344 · Area: core / util (CharsRefBuilder, its java.lang.Appendable implementation) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1344.html](../../bugs/LFV-1344.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You hold a CharsRefBuilder, which implements java.lang.Appendable, and call append("abc", 2, 1), for example because your own code computed the end of a range wrongly. Appendable's contract, which CharsRefBuilder inherits with no javadoc of its own, says this call must throw IndexOutOfBoundsException, as StringBuilder and the other Appendable in lucene-core, CharTermAttributeImpl, do. Instead the call returns normally and appends nothing, so the caller's bug goes unnoticed, because the method has no up-front range check: grow(length + end - start) asks for a smaller size and the copy loop from start to end runs zero times.

## Expected vs actual

- Expected: `builder.append("abc", 2, 1)` throws `IndexOutOfBoundsException`.
- Actual: It returns normally; the builder still holds `"abc"`, length 3 (`Expected exception IndexOutOfBoundsException but no exception was thrown`).

## Files

- [src/org/apache/lucene/util/TestCharsRefBuilderAppendRange.java](src/org/apache/lucene/util/TestCharsRefBuilderAppendRange.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestCharsRefBuilderAppendRange
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCharsRefBuilderAppendRange.java](TestCharsRefBuilderAppendRange.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
