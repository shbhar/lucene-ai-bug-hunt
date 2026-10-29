# BlockTreeOrds TermsEnum: seekExact(term, termState) right after seekExact(ord) marks the enum positioned over an incomplete frame stack, so a later seekExact returns false for an existing term and seekCeil/next throw or return made-up terms

Bug LFV-487 · Area: codecs / BlockTreeOrds terms dictionary (lucene-codecs OrdsSegmentTermsEnum) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a field with BlockTreeOrdsPostingsFormat (here 26 terms a00..a24 and b in one segment, default block sizes), position a TermsEnum with seekExact(0), take its termState and call seekExact(term, state) on the same enum, which the TermsEnum javadoc allows and which leaves it on a00. You expect a later seekExact("b") to return true, seekCeil("b") to return FOUND and next after a24 to return "b", as they do without the extra TermState call and with every other terms dictionary. Instead seekExact("b") returns false, seekCeil("b") throws EOFException (or, in a segment whose terms file is larger than about 7 KB, returns NOT_FOUND with a made-up term) and next after a24 throws, because seekExact(ord) loads only the target block and deliberately leaves the enum unpositioned, while the TermState seek's 'already on this term' shortcut marks it positioned anyway, so the next call reuses parent frames that were never loaded.

The test shows that on a `BlockTreeOrdsPostingsFormat` TermsEnum, `seekExact(0)` followed by `seekExact(term(), termState())` on the same enum leaves it unable to find another existing term (26 terms a00..a24 and b, default block sizes).

## Expected vs actual

Expected `seekExact("b")` to return `true`; actual `false` (`existing term b: expected seekExact true, got false`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/codecs/blocktreeords/TestOrdsTermStateSeekAfterOrdSeek.java](src/org/apache/lucene/codecs/blocktreeords/TestOrdsTermStateSeekAfterOrdSeek.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.blocktreeords.TestOrdsTermStateSeekAfterOrdSeek
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
