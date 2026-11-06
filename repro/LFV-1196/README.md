# The default postings enum's intoBitSet throws IndexOutOfBoundsException when the destination bit set ends before upTo inside a dense (bit-set-encoded) postings block, although every matching doc below upTo fits, contrary to the DocIdSetIterator#intoBitSet contract (latent: no Lucene caller passes such a window; since 10.2.0, #14133)

Finding LFV-1196 · Area: codecs / postings bulk iteration (Lucene104PostingsReader BlockPostingsEnum.intoBitSet, bit-set-encoded blocks) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1196.html](../../bugs/LFV-1196.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 512 documents with a StringField term t in every other one (docs 0, 2, ..., 510), so its first full postings block is stored as a bit set, position t's PostingsEnum on doc 0 and call intoBitSet(400, new FixedBitSet(399), 0); every matching doc below 400 (0, 2, ..., 398) fits in 399 bits, and the javadoc says the call must behave exactly like the loop 'for (doc = docID(); doc &lt; upTo; doc = nextDoc()) bitSet.set(doc - offset)', so you expect bits 0, 2, ..., 398 and docID() 400. Instead it throws IndexOutOfBoundsException 'Range [1, 1 + 399) out of bounds for length 399' from FixedBitSet.orRange, with -ea and -da: the bit-set branch copies the whole slice [current doc + 1, min(upTo, last doc of the block + 1)) with one orRange, which bound-checks the full length against the destination whether the bits are set or not. Upstream declared this call shape legal when it fixed the same failure in BitSetIterator (#14142) and IndexedDISI (#14922), but the postings reader was never changed; no Lucene caller passes such a window, so only direct callers hit it.

## Expected vs actual

- Expected: Bit 0 is set and `docID()` is 2.
- Actual: `IndexOutOfBoundsException: Range [1, 1 + 1) out of bounds for length 1` from `FixedBitSet.orRange`, with assertions on and off.

## Files

- [src/org/apache/lucene/codecs/lucene104/TestBlockPostingsIntoBitSetShortDest.java](src/org/apache/lucene/codecs/lucene104/TestBlockPostingsIntoBitSetShortDest.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene104.TestBlockPostingsIntoBitSetShortDest
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestBlockPostingsIntoBitSetShortDest.java](TestBlockPostingsIntoBitSetShortDest.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
