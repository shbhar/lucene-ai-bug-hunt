# Block-tree TermsEnum.next after seekExact(term, TermState) on a never-positioned enum restarts at the field's first term instead of returning the next term

Bug LFV-157 · Area: codecs / block-tree terms dictionary (Lucene103 SegmentTermsEnum; also BlockTreeOrds and the Lucene90 block tree in backward-codecs) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You take the TermState of a term (say 'banana') from one TermsEnum, or from TermStates the way TermQuery does, and use it to position a brand-new TermsEnum of the same field and segment with seekExact(term, state); term and docFreq then correctly report 'banana'. You expect next to return the following term ('cherry'), as the comment inside next promises and as every other terms dictionary does. Instead, with the default block-tree terms dictionary, next returns the field's first term ('apple') and iteration starts over from the beginning, because next mistakes the not-yet-loaded enum for a fresh one and throws the TermState position away.

The test shows that on the default (block-tree) terms dictionary, a new `TermsEnum` positioned with `seekExact(term, state)`, using the `TermState` that `TermStates.build` returns (as `TermQuery` does), restarts at the field's first term on `next()`.

## Expected vs actual

Expected `next()` after the seek to "b" (terms a, b, c) to return "c"; actual: "a" (`expected:<[c]> but was:<[a]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestNextAfterSeekExactTermState.java](src/org/apache/lucene/index/TestNextAfterSeekExactTermState.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestNextAfterSeekExactTermState
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
