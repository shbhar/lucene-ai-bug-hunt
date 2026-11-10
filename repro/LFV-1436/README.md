# MultiTerms.getTermPostingsEnum(IndexReader, String, BytesRef) javadoc says it returns null when positions were not indexed, but it returns a PostingsEnum whose nextPosition() is -1, so a caller who relies on the documented null to detect missing positions reads -1 as every position (javadoc only; the code is correct); since 5.1.0

Finding LFV-1436 · Area: index / merged postings helpers (MultiTerms.getTermPostingsEnum 3-argument overload javadoc; TermsEnum.postings never returns null) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1436.html](../../bugs/LFV-1436.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a field without positions (IndexOptions.DOCS_AND_FREQS) and call MultiTerms.getTermPostingsEnum(reader, field, term) for a term that exists. Its javadoc says 'This will return null if the field or term does not exist or positions were not indexed', so you expect null and could use that null to learn that positions are unavailable. Instead you get a non-null PostingsEnum whose nextPosition() returns -1 for every occurrence: the method asks TermsEnum.postings for PostingsEnum.ALL, and that method has never returned null since 5.1.0 (LUCENE-6271, on purpose), so only the sentence is wrong.

## Expected vs actual

- Expected: `getTermPostingsEnum` returns `null` for a field indexed without positions, as its javadoc says.
- Actual: It returns a `PostingsEnum` whose `nextPosition()` is -1: `AssertionError: expected null, got a PostingsEnum with nextPosition()=-1`, with assertions on and off, on main and 10.6.0.

## Files

- [src/org/apache/lucene/index/TestMultiTermsPostingsNoPositions.java](src/org/apache/lucene/index/TestMultiTermsPostingsNoPositions.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestMultiTermsPostingsNoPositions
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMultiTermsPostingsNoPositions.java](TestMultiTermsPostingsNoPositions.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
