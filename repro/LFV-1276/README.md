# CheckIndex's seek-by-ord / seek-by-term test is gated on 'status.termCount - termCountStart &gt; 0', which since LUCENE-4485 equals (live terms of this field) - (fully-deleted terms of all earlier fields), so on a segment with deletions the test is silently skipped for later fields of an ord-capable terms dictionary (and always for a field whose terms all lack a live doc), and a broken seekExact(ord) passes CheckIndex (since 5.3.0)

Finding LFV-1276 · Area: index / CheckIndex postings check (terms dictionary seek-by-ord test) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1276.html](../../bugs/LFV-1276.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one segment with BlockTreeOrdsPostingsFormat and NoMergePolicy (document 0 has terms a0 and a1 in field a, document 1 has terms b0 and b1 in field b), delete document 0 so that both terms of field a have no live document, and run CheckIndex.testPostings over a reader whose field-b terms enum swaps ords 0 and 1 on seekExact(ord), as a broken terms dictionary could. You expect the seek-by-ord test to report it, as it does on stock main when nothing is deleted ('seek to ord 1 returned ord 0'). Instead status.error is null on main (-ea and -da), 10.6.0, 10.5.2 and 9.12.3: for field b the gate computes 2 live terms minus the 2 fully-deleted terms of field a = 0, so the test never runs for field b.

## Expected vs actual

- Expected: `CheckIndex.testPostings(...).error` reports `seek to ord 0 returned ord 1` for field `b`.
- Actual: `error` is null (`termCount=2 delTermCount=2`), so the test fails at `assertNotNull`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestCheckIndexSeekByOrdAfterDeletedTerms.java](src/org/apache/lucene/index/TestCheckIndexSeekByOrdAfterDeletedTerms.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestCheckIndexSeekByOrdAfterDeletedTerms
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCheckIndexSeekByOrdAfterDeletedTerms.java](TestCheckIndexSeekByOrdAfterDeletedTerms.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
