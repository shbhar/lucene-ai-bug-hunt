# LFV-076: withdrawn

Withdrawn after review: DocIdsWriter is package-private and its only caller passes start = 0 at all three call sites (BKDWriter.java:836, 1733, 1988 on main) in every release since LUCENE-8888; no index was ever affected, so a cleanup.
