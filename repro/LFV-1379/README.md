# GraphTokenFilter.end(), the base of FixedShingleFilter, keeps only the final position increment and offset of its input's end state once its read-ahead has reached the end of the input, so any other end-of-stream attribute value set by the input's end() (a FlagsAttribute, TypeAttribute or custom attribute value) comes back as the attribute's default (latent: no shipped Lucene producer or consumer uses such a value; since 8.0.0)

Finding LFV-1379 · Area: analysis / token streams (GraphTokenFilter.end() in core; FixedShingleFilter in analysis-common is the shipped subclass) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1379.html](../../bugs/LFV-1379.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You put a TokenFilter whose end() calls super.end() and then sets FlagsAttribute to 42 under FixedShingleFilter(.., 2) on a WhitespaceTokenizer over 'a b c', consume the stream and call end(). You expect the flags to read 42, as they do without the shingle filter or through the older ShingleFilter, because TokenFilter#end chains to the input and the TokenStream#end javadoc lets end() adjust other attributes whose end-of-stream value matters. Instead they read 0: GraphTokenFilter's read-ahead already called input.end() and recorded only the position increment and end offset, and its end() then resets every attribute (endAttributes()) and restores only those two.

## Expected vs actual

- Expected: After `end()` the flags read 42, the value the input's `end()` set, and the test passes.
- Actual: The flags read 0: `AssertionError: expected:<42> but was:<0>` at line 38, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/shingle/TestFixedShingleFilterEndState.java](src/org/apache/lucene/analysis/shingle/TestFixedShingleFilterEndState.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.shingle.TestFixedShingleFilterEndState
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFixedShingleFilterEndState.java](TestFixedShingleFilterEndState.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
