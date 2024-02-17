# Java Apple Lossless Decoder

Copyright (c) 2011-2014 Peter McQuillan</br>
All Rights Reserved.</br>
Distributed under the BSD Software License (see [license.txt](../../../../../../license.txt))</br>

This package contains a Java implementation of an Apple Lossless decoder.
It is ported from v0.2.0 of the Apple Lossless decoder written by David Hammerton.
This code supports both 16-bit and 24-bit Apple Lossless files.

It is packaged with a demo command-line program that accepts an
Apple Lossless audio file as input and output a RIFF wav file.

The Java source code files can be compiled to class files very simply by going
to the directory where you have downloaded the .java files and running

`javac *.java`

To run the demo program, use the following command

`java DecoderDemo <input.m4a> <output.wav>`

where input.m4a is the name of the Apple Lossless file you wish to decode to a WAV file.

This code is ported from v0.2.0 of the Apple Lossless decoder written by David Hammerton.
However there are also some extra changes, for example:

* The original code to read the hdlr atom was capable of generating a minus value seek
  after reading strlen - this causes problems if there is poor or non-existent seeking
  support
* The stream handling code is now written so that it keeps track of where in the input
  stream it is - this is needed for handling the case where the mdat atom comes before the
  moov atom (and you have poor/non-existent seeking support)
* The stsz atom handling code assumed variable sample sizes, it now also handles fixed
  sample sizes.


Thanks to Denis Tulskiy for the contributions he made to the code.

Please direct any questions or comments to beatofthedrum@gmail.com
