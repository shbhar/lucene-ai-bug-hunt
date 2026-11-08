# A reused IntsRefFSTEnum gives wrong seekCeil/seekFloor results after visiting a term with label Integer.MAX_VALUE, because FSTEnum.rewindPrefix compares labels by subtraction and it overflows

Finding LFV-1147 · Area: util / FST (FSTEnum.rewindPrefix label compare; IntsRefFSTEnum seekCeil/seekFloor) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1147.html](../../bugs/LFV-1147.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a BYTE4 FST with [3] -&gt; 1 and [3, Integer.MAX_VALUE] -&gt; 2, move one IntsRefFSTEnum to [3, MAX] with seekCeil, then call seekCeil([3]) on the same enum; you expect [3], the smallest term &gt;= [3], as a fresh enum returns. Instead it returns [3, MAX] (and seekFloor([3]) returns null), with or without assertions. FSTEnum.rewindPrefix compares labels as getCurrentLabel() - getTargetLabel(); once the target is used up its label is END_LABEL (-1), so MAX - (-1) wraps to Integer.MIN_VALUE and the enum keeps the MAX arc instead of going back to the node's first arc.

## Expected vs actual

- Expected: `[3]`, as a fresh enum returns.
- Actual: `[3, 7fffffff]`, a key above the ceiling: `java.lang.AssertionError: expected:<[3]> but was:<[3 7fffffff]>`.

## Files

- [src/org/apache/lucene/util/fst/TestFSTEnumSeekAfterMaxLabel.java](src/org/apache/lucene/util/fst/TestFSTEnumSeekAfterMaxLabel.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.fst.TestFSTEnumSeekAfterMaxLabel
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFSTEnumSeekAfterMaxLabel.java](TestFSTEnumSeekAfterMaxLabel.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
