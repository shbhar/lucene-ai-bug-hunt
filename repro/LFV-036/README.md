# LFV-036: withdrawn

Withdrawn after review: it is wrong only in a state the only production caller (FSTSuffixNodeCache, through append()) cannot reach, because append() throws first; a one-cast cleanup in a @lucene.internal method.
