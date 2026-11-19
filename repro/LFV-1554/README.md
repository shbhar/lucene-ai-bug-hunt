# On branch_11x and main (both unreleased), BitSetDocIdStream.intoBitSet, added by #16757, throws IndexOutOfBoundsException when the offset is above the stream's cursor and upTo is above it too, although every doc ID in the stream is at or above the offset (run: the stream BooleanScorer hands out for a two-clause disjunction scored over doc IDs [1, 3) of three documents, loaded with offset 1 into 2 bits); latent: Lucene's own caller, the query cache, passes offset 0 and is not affected

Finding LFV-1554 · Area: search / DocIdStream bulk collection (BitSetDocIdStream.intoBitSet; the stream BooleanScorer hands to LeafCollector.collect(DocIdStream)) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

On branch_11x and main (both unreleased) you write a LeafCollector that fills a bit set for the doc-ID range it is scoring: you index three documents, score doc IDs [1, 3) of a two-term OR query that matches all three (scores not needed) with BulkScorer.score and, in collect(DocIdStream), call the new stream.intoBitSet(3, new FixedBitSet(2), 1), so that doc 1 becomes bit 0; every doc ID in the stream is in [1, 3), so you expect 2 bits, which is what the documented default body (forEach(upTo, doc -&gt; bitSet.set(doc - offset))) loads from the same stream. Instead the call throws IndexOutOfBoundsException 'Range [-1, -1 + 3) out of bounds for length 2', with assertions on and off: BooleanScorer's stream starts at an internal window base (the first match rounded down to a multiple of 4,096, here 0) that a caller cannot see, and the override added by #16757 computes its copy from that hidden position and not from the doc IDs. It is latent and loud: Lucene's own caller, the query cache, passes offset 0 and is not affected, no wrong bit set was seen in any run, and no release has the method; branch_11x as it stood at 5d7ca54ba503 on 2026-11-19 has it (backport 6474fbe3e80e), and the same call throws there the same way, so 11.0.0 would be the first release with it.

## Expected vs actual

- Expected: You expect 2 bits, which is what the documented default body (forEach(upTo, doc -&gt; bitSet.set(doc - offset))) loads from the same stream.
- Actual: The call throws IndexOutOfBoundsException 'Range [-1, -1 + 3) out of bounds for length 2', with assertions on and off: BooleanScorer's stream starts at an internal window base (the first match rounded down to a multiple of 4,096, here 0) that a caller cannot see, and the override added by #16757 computes its copy from that hidden position and not from the doc IDs.

## Files

- [src/org/apache/lucene/search/TestBitSetDocIdStreamIntoBitSetOffset.java](src/org/apache/lucene/search/TestBitSetDocIdStreamIntoBitSetOffset.java) (JUnit test)

## Run it

- Tested on Lucene main d7162870c1fd (a commit of 2026-11-19): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 5d7ca54ba503 (a commit of 2026-11-19): reproduced, with assertions on and off (set V=11.0.0-SNAPSHOT).
- Tested on Lucene main 1ae70c6e45b8 (a commit of 2026-11-19): reproduced, with assertions on and off; this is the commit that added the method.
- Where the test compiles: DocIdStream.intoBitSet exists on main from 1ae70c6e45b8 on and on branch_11x from 6474fbe3e80e on (both commits are of 2026-11-19). On a build without the method the test does not compile ('cannot find symbol: method intoBitSet(int,FixedBitSet,int)') and nothing runs: so on main f6e694652c3d, the commit before, and with the 10.6.0 release jars (runs of 2026-11-19; for the release, the three 10.6.0 jars from Maven Central, org.apache.lucene: lucene-core, lucene-codecs and lucene-test-framework, stand in place of the three $LUCENE jars). On a checkout of branch_11x set V=11.0.0-SNAPSHOT in the commands below.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.IndexOutOfBoundsException: Range [-1, -1 + 3) out of bounds for length 2; a compile error (cannot find symbol: method intoBitSet) means that the build does not have the method.
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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestBitSetDocIdStreamIntoBitSetOffset
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
