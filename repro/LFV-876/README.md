# ThaiCharFilter tests its multi-character rules (double Sara E, decomposed Sara Am, tone mark before an above/below vowel) on the raw look-ahead characters, before its own zero-width removal and deduplication have run on them, so a zero-width character or a repeated tone mark inside such a pattern leaves the anomaly in the output and the default ThaiAnalyzer merges the word with its neighbours

Bug LFV-876 · Area: analysis-common / Thai (ThaiCharFilter.read single-pass look-ahead; the 'thai' char filter factory and the default ThaiAnalyzer as consumers; new in 10.6.0) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-876.html](../../bugs/LFV-876.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Thai text with the default ThaiAnalyzer, which runs ThaiCharFilter before the tokenizer, and expect a word typed with two of the typos the filter documents fixing (ที่ with its tone mark typed twice and before the vowel, ท่่ี, or แมว written as two Sara E with an invisible zero-width joiner between them) to be indexed like the clean word, because the filter's javadoc says it removes zero-width characters, deduplicates repeated tone marks, reorders a tone mark typed before an above/below vowel, joins double Sara E and recomposes decomposed Sara Am precisely so that the BreakIterator does not merge adjacent words. ThaiCharFilter makes one pass and tests its multi-character patterns against the raw next characters, while zero-width removal and deduplication act only on the character at the front of its buffer, after the earlier character has already been emitted, so the output still holds the tone-before-vowel, double Sara E or decomposed Sara Am sequence (ท่ี, เเมว, ทํา), which a second pass of the same filter would clean. The BreakIterator then merges words: ฉันทำงานท่่ีบ้าน is indexed as [ฉัน, ทำ, งาน, ที่บ้าน] instead of [ฉัน, ทำ, งาน, บ้าน], ฉันรักเ + ZWJ + เมวมาก as [ฉัน, รักแมวมาก], and a TermQuery for บ้าน or แมว finds 0 documents.

## Expected vs actual

- Expected: ท่่ี -&gt; ที่, เ+ZWJ+เมว -&gt; แมว, ทํ+ZWSP+า -&gt; ทำ; ThaiAnalyzer keeps [ฉัน, ทำ, งาน, บ้าน]; a TermQuery for บ้าน finds 1 document.
- Actual: ท่ี, เเมว and ทํา; [ฉัน, ทำ, งาน, ที่บ้าน]; 0 documents (6 of 7 tests fail; the control passes).

## Files

- [src/org/apache/lucene/analysis/th/TestThaiCharFilterLookahead.java](src/org/apache/lucene/analysis/th/TestThaiCharFilterLookahead.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed 3390C1A5
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=3390C1A5 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.th.TestThaiCharFilterLookahead
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestThaiCharFilterCombinedTypos.java](TestThaiCharFilterCombinedTypos.java)
- [fix-option-A.diff](fix-option-A.diff)
- [TestThaiCharFilterLookahead.java](TestThaiCharFilterLookahead.java)
- [TestThaiCharFilterCombinedAnomalies.java](TestThaiCharFilterCombinedAnomalies.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
