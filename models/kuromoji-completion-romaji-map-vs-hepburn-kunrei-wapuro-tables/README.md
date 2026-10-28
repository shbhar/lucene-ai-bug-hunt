# Formal models and scripts: kuromoji-completion-romaji-map-vs-hepburn-kunrei-wapuro-tables

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Japanese completion suggester (JapaneseCompletionAnalyzer / JapaneseCompletionFilter): words with a small tsu before shi, chi or tsu (雑誌, 決して, マッチ, 三つ) are never suggested for their Hepburn or input-method spellings zasshi, kesshite, macchi/matchi, mittsu, because romaji_map.txt maps ッシ only to 'ssi', ッチ only to 'tti' and ッツ only to 'ttu', while the base rows シ/チ/ツ list shi/chi/tsu and the neighbouring rows ッシャ and ッチャ do double the consonant ('ssha', 'ccha') (bug LFV-849; [repro](../../repro/LFV-849/), [bug page](../../bugs/LFV-849.html))
- Kuromoji's completion table romaji_map.txt has no rows for チェ, イェ, ツァ/ツィ/ツェ/ツォ, ディ/デュ and ウォ, although it has rows for their siblings (チャ/チュ/チョ, シェ/ジェ, ッイェ, ティ/テュ, ウィ/ウェ), so raw-romaji prefixes such as 'che', 'ye' and 'tsa' suggest nothing for words like チェック, イェール and ツァー (bug LFV-850; [repro](../../repro/LFV-850/), [bug page](../../bugs/LFV-850.html))
- Kuromoji's completion table romaji_map.txt has three typo rows: the ッヘ row uses a hiragana へ (U+3078) in its key, so katakana ッヘ is romanized 'xtuhe' instead of 'hhe' and a word like ヨッヘン is not suggested for 'yohhen', and the ッスォ and ッドゥ rows carry the copied spellings 'suxo' and 'ddoxi' (bug LFV-848; [repro](../../repro/LFV-848/), [bug page](../../bugs/LFV-848.html))
- Kuromoji completion suggester (JapaneseCompletionAnalyzer / JapaneseCompletionFilter, QUERY mode) suggests nothing while an input method shows a small tsu plus a pending c, j, f or v ('まっｃ' while typing maccha for 抹茶, 'ばっｊ' for bajji, 'すたっｆ' for sutaffu), because romaji_map.txt has the partial-input rows ッk..ッw but none for ッc, ッj, ッf or ッv, although its full rows double those letters (ッチャ ccha, ッジ jji, ッフ ffu, ッヴ vvu) (bug LFV-854; [repro](../../repro/LFV-854/), [bug page](../../bugs/LFV-854.html))

## Files

- [romaji-map-malformed-rows-hiragana-key-and-copy-errors/model/table_laws.py](romaji-map-malformed-rows-hiragana-key-and-copy-errors/model/table_laws.py) (used for LFV-848)
- [romaji-map-missing-extended-katakana-rows/model/table_laws.py](romaji-map-missing-extended-katakana-rows/model/table_laws.py) (used for LFV-850)
- [romaji-map-partial-input-rows-miss-c-f-j-v/model/table_laws.py](romaji-map-partial-input-rows-miss-c-f-j-v/model/table_laws.py) (used for LFV-854)
- [romaji-map-sokuon-rows-miss-doubled-spellings/model/table_laws.py](romaji-map-sokuon-rows-miss-doubled-spellings/model/table_laws.py) (used for LFV-849)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
