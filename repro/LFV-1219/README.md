# (Latent) NotDocIdSet throws NullPointerException on the first nextDoc() or advance() when the wrapped DocIdSet's iterator() is null, as it is for an empty RoaringDocIdSet, instead of returning every document (since 5.0.0)

Finding LFV-1219 · Area: core / util: doc id sets (NotDocIdSet, the negation of a DocIdSet, over a set whose iterator() is null) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1219.html](../../bugs/LFV-1219.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You negate an empty set, for example new NotDocIdSet(10, new RoaringDocIdSet.Builder(10).build()), and expect its iterator to return every document 0 to 9, because NotDocIdSet 'encodes the negation of another DocIdSet' and DocIdSet.iterator() 'can return null if there are no docs that match', which is exactly what an empty RoaringDocIdSet returns. Instead iterator() returns normally and the first nextDoc() throws NullPointerException ('Cannot invoke DocIdSetIterator.advance(int) because this.val$inIterator is null') at NotDocIdSet.java:65, because NotDocIdSet.iterator() keeps in.iterator() without a null check. Lucene's own only NotDocIdSet wraps a non-empty ShortArrayDocIdSet, so only direct users of these internal classes are hit.

## Expected vs actual

- Expected: `nextDoc()` returns doc 0, the negation of the empty set over one document, and the test passes.
- Actual: `java.lang.NullPointerException: Cannot invoke "org.apache.lucene.search.DocIdSetIterator.advance(int)" because "this.val$inIterator" is null` at `NotDocIdSet.java:65`.

## Files

- [src/org/apache/lucene/util/TestNotDocIdSetOfEmptySet.java](src/org/apache/lucene/util/TestNotDocIdSetOfEmptySet.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestNotDocIdSetOfEmptySet
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestNotDocIdSetOfEmptySet.java](TestNotDocIdSetOfEmptySet.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
