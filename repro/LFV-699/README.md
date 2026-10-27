# TSTLookup.store/load writes each tree node's split char with DataOutput.writeString, so the two surrogate halves of an emoji or CJK Extension B character come back as U+FFFD: after load, get and lookups whose prefix runs through such a character silently find nothing, and some neighbouring BMP keys are lost too (every release since 4.7.0)

Bug LFV-699 · Area: suggest / ternary search tree suggester persistence (TSTLookup.store and load, writeRecursively and readRecursively) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-699.html](../../bugs/LFV-699.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a TSTLookup from ordinary text that contains an emoji or a CJK Extension B character (for example the key 𠮷野家 with weight 5), store it, load it back, and expect the reloaded copy to answer like the original, as Lookup.load promises ('load it from a previously saved copy'). Instead lookup('𠮷', true, 5) returns instead of [𠮷野家/5] and get('𠮷野家') returns null after the reload, because TSTLookup keeps one tree node per UTF-16 code unit, writeRecursively writes each node's split char with DataOutput.writeString of a one-char String, and a lone surrogate has no UTF-8 form, so it is written and reloaded as U+FFFD and the tree's char comparisons then take the wrong branches. ASCII keys and getCount survive, while some BMP keys next to a surrogate node are lost too (in the finder's test lookup('Ａ') returns instead of [Ａb/4]).

## Expected vs actual

- Expected: After `load`, `lookup("𠮷")` = `[𠮷野家/5]`, `get("𠮷野家")` = 5 and `lookup("Ａ")` = `[Ａb/4]`, as before `store`.
- Actual: `lookup("𠮷")` = `` and `lookup("Ａ")` = `` while `load` returns true and the ASCII controls pass: 2 of 2 tests fail, with assertions on and off.

## Files

- [src/org/apache/lucene/search/suggest/tst/TestTSTLookupStoreLoadSupplementary.java](src/org/apache/lucene/search/suggest/tst/TestTSTLookupStoreLoadSupplementary.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.tst.TestTSTLookupStoreLoadSupplementary
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestTSTLookupSurrogatePersistence.java](TestTSTLookupSurrogatePersistence.java)
- [TestTSTLookupStoreLoadSupplementary.java](TestTSTLookupStoreLoadSupplementary.java)
- [TestTSTStoreLoadSupplementaryV1.java](TestTSTStoreLoadSupplementaryV1.java)
- [TstStoreCompatProbe.java](TstStoreCompatProbe.java)
- [TstTreeDumpProbe.java](TstTreeDumpProbe.java)
- [TstSurrogateBoundaryProbe.java](TstSurrogateBoundaryProbe.java)
- [TstRepairFromTokensProbe.java](TstRepairFromTokensProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
