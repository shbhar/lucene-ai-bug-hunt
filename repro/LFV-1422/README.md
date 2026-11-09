# MultiTermsEnum.next() after a successful seekExact re-seeks every sub-reader with the BytesRef that one sub's own term() returned, and MemoryIndex's terms enum overwrites that BytesRef during its binary search, so a merged terms enum over MemoryIndex readers skips a term, repeats one or goes back to an earlier one; since 4.1.0

Finding LFV-1422 · Area: core / index MultiTermsEnum (merged terms view, MultiTerms.getTerms) with memory / MemoryIndex terms enum · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1422.html](../../bugs/LFV-1422.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You put two MemoryIndex readers, each holding the terms a, b, c, d and e in field f, into a MultiReader, take MultiTerms.getTerms(reader, "f").iterator(), call seekExact("a"), which returns true, and then next(). You expect "b", as the TermsEnum contract ('Each term in the enumeration is greater than the one before it') and upstream TestTermsEnum require, but you get "c". After seekExact the merged enum keeps the second sub's own term() BytesRef as its current term and next() re-seeks every sub with it; MemoryIndex's seekCeil binary-searches by writing each probe term into that same BytesRef, so it overwrites the target with the middle term, finds it equal and stops there.

## Expected vs actual

- Expected: `next()` returns `b`, the term after `a`.
- Actual: `next()` returns `c`: `expected:<[b]> but was:<[c]>`.

## Files

- [src/org/apache/lucene/index/memory/TestMultiTermsNextAfterSeekExact.java](src/org/apache/lucene/index/memory/TestMultiTermsNextAfterSeekExact.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.memory.TestMultiTermsNextAfterSeekExact
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMultiTermsNextAfterSeekExact.java](TestMultiTermsNextAfterSeekExact.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
