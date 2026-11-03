# When an AttributeFactory throws while IndexWriter adds TermFrequencyAttribute, FieldInvertState keeps the previous stream's attribute handles, so later accepted documents of that field are indexed with the wrong positions and offsets until the next flush

Finding LFV-1154 · Area: index / IndexingChain inversion (FieldInvertState.setAttributeSource attribute cache) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1154.html](../../bugs/LFV-1154.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a document whose field value is your own TokenStream ending in two removed stop words ('x the the' through StopFilter), then a document whose analysis fails because your analyzer's AttributeFactory throws when IndexWriter asks for TermFrequencyAttribute, then the plain-text document 'a b' through the same analyzer with the factory working again. You expect the failed document to be dropped and 'a b' to be indexed at positions 0 and 1, as it is without the failure. Instead 'a b' is accepted at positions 1 and 3 and PhraseQuery f:"a b" finds nothing, with no error, because FieldInvertState.setAttributeSource recorded the analyzer's reused stream as cached before it fetched that stream's attributes, so every later document of the field keeps reading the first stream's position increment (frozen at 2) until the next flush.

## Expected vs actual

- Expected: `PhraseQuery f:"a b"` counts 1: the re-added document is at positions 0 and 1.
- Actual: `expected:<1> but was:<0>`: the document is accepted at positions 1 and 3, with no error and the writer still open.

## Files

- [src/org/apache/lucene/index/TestFieldInvertStateAttributeFactoryFailure.java](src/org/apache/lucene/index/TestFieldInvertStateAttributeFactoryFailure.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFieldInvertStateAttributeFactoryFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFieldInvertStateAttributeFactoryFailure.java](TestFieldInvertStateAttributeFactoryFailure.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
