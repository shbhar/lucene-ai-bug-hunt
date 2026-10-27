# AnalyzingSuggester and FuzzySuggester check a payload-enabled surface form for the reserved byte U+001F in bytes[0, length) of the backing array instead of in the BytesRef slice [offset, offset + length), so a valid slice is rejected with IllegalArgumentException and a slice that contains U+001F is accepted and later mis-split

Bug LFV-399 · Area: suggest / analyzing suggester (AnalyzingSuggester.build payload check, inherited by FuzzySuggester) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an AnalyzingSuggester or FuzzySuggester with payloads from your own InputIterator whose surface forms are BytesRef slices of a larger byte array, for example "ab" at offset 1 of {0x1F, 'a', 'b'}, and expect it to behave exactly as for compact copies of the same bytes. Instead build looks for the reserved separator U+001F in bytes[0, length) of the backing array, ignoring the slice's offset, while two lines later it writes the real slice: the valid slice "ab" is rejected with IllegalArgumentException 'surface form cannot contain unit separator character U+001F', and "a&lt;U+001F&gt;b" at offset 3 of {'x','x','x','a',0x1F,'b'}, whose compact copy is rejected, is accepted. The accepted entry is then split at the wrong place: without EXACT_FIRST, lookup("a") returns key "a" with payload "b&lt;U+001F&gt;p" instead of payload "p"; with the default EXACT_FIRST it returns nothing for that entry with -da and throws AssertionError with -ea.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: build looks for the reserved separator U+001F in bytes[0, length) of the backing array, ignoring the slice's offset, while two lines later it writes the real slice: the valid slice "ab" is rejected with IllegalArgumentException 'surface form cannot contain unit separator character U+001F', and "a&lt;U+001F&gt;b" at offset 3 of {'x','x','x','a',0x1F,'b'}, whose compact copy is rejected, is accepted.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterSurfaceFormSlice.java](src/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterSurfaceFormSlice.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestAnalyzingSuggesterSurfaceFormSlice
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
