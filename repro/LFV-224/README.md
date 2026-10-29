# LFV-224: withdrawn

Withdrawn after review: the target must lie within one filesystem block of Long.MAX_VALUE; the call fails loudly with nothing read or written, and the stock directories throw the same IllegalArgumentException on that overflow.
