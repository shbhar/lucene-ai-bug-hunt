# A sloppy PhraseQuery or MultiPhraseQuery with two entries at one position that a third entry pulls into the same repetition group misses documents its exact (slop 0) form matches, and for MultiPhraseQuery one unchanged query matches on some evaluations and misses on others, because SloppyPhraseMatcher checks its 'same offset is not a repetition' rule only pairwise and breaks equal-offset ties in HashSet order

Bug LFV-193 · Area: search / phrase queries (SloppyPhraseMatcher repetition groups: gatherRptGroups same-offset exclusion, collide, advanceRepeatGroups, sortRptGroups tie order) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a sloppy phrase query by hand with two entries at the same position, for example PhraseQuery a@0 a@1 a@1 (the second 'a' added twice at position 1), and expect slop 1 to match everything slop 0 matches and the query to give the same answer every time it runs. SloppyPhraseMatcher's rule that two entries from one query position are not repetitions of each other is checked only pairwise, so a third entry (a@0) pulls both into one repetition group and the matcher then forces them onto different document positions: the document 'a a' matches with slop 0 but not with slop 1 to 4. For MultiPhraseQuery the same collision is resolved in HashSet iteration order, because groups are sorted by offset only, so the one immutable query (a|b)@0 b@2 a@2 ~2 on one unchanged reader matches the document a@0, a@1 with b@1 stacked, a@2 in about half of 200 evaluations and misses it in the rest, with no error.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestSloppyPhraseSameOffsetRepeats.java](src/org/apache/lucene/search/TestSloppyPhraseSameOffsetRepeats.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.search.TestSloppyPhraseSameOffsetRepeats
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
