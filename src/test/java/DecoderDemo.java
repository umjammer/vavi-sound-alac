/*
 * Copyright (c) 2011 Peter McQuillan
 *
 * All Rights Reserved.
 *
 * Distributed under the BSD Software License (see license.txt)
 */

import java.io.IOException;
import java.nio.file.Paths;

import com.beatofthedrum.alacdecoder.AlacContext;
import com.beatofthedrum.alacdecoder.WavWriter;


/**
 * DecoderDemo.
 */
class DecoderDemo {

    static java.io.FileOutputStream outputStream;
    static int outputOpened;
    static int writeWavFormat = 1;
    static String inputFileN = "";
    static String outputFileN = "";

    /**
     * Reformat samples from longs in processor's native endian mode to
     * little-endian data with (possibly) less than 3 bytes / sample.
     */
    public static byte[] formatSamples(int bps, int[] src, int samCount) {
        int temp = 0;
        int counter = 0;
        int counter2 = 0;
        byte[] dst = new byte[65536];

        switch (bps) {
        case 1:
            while (samCount > 0) {
                dst[counter] = (byte) (0x00FF & (src[counter] + 128));
                counter++;
                samCount--;
            }
            break;

        case 2:
            while (samCount > 0) {
                temp = src[counter2];
                dst[counter] = (byte) temp;
                counter++;
                dst[counter] = (byte) (temp >>> 8);
                counter++;
                counter2++;
                samCount = samCount - 2;
            }
            break;

        case 3:
            while (samCount > 0) {
                dst[counter] = (byte) src[counter2];
                counter++;
                counter2++;
                samCount--;
            }
            break;
        }

        return dst;
    }

    static void setupEnvironment(int argc, String[] argv) {
        int i = argc;

        int escaped = 0;

        if (argc < 2)
            usage();

        int argIndex = 0;
        // loop through command-line arguments
        while (argIndex < argc) {
            if (argv[argIndex].startsWith("-")) {
                if (argv[argIndex].startsWith("-r") || argv[argIndex].startsWith("-R")) {
                    // raw PCM output
                    writeWavFormat = 0;
                }
            } else if (inputFileN.isEmpty()) {
                inputFileN = argv[argIndex];
            } else if (outputFileN.isEmpty()) {
                outputFileN = argv[argIndex];
            } else {
                System.out.println("extra unknown argument: " + argv[argIndex]);
                usage();
            }
            argIndex++;
        }

        if (inputFileN.isEmpty() || outputFileN.isEmpty())
            usage();

    }

    static void getBuffer(AlacContext ac) throws IOException {
        int destBufferSize = 1024 * 24 * 3; // 24kb buffer = 4096 frames = 1
                                            // alac sample (we support max
                                            // 24bps)
        byte[] pcmBuffer;
        int totalUnpackedBytes = 0;
        int bytesUnpacked;

        int[] destBuffer = new int[destBufferSize];

        int bps = ac.getBytesPerSample();

        while (true) {
            bytesUnpacked = ac.unpackSamples(destBuffer);
            if (bytesUnpacked == -1)
                break;

            totalUnpackedBytes += bytesUnpacked;

            if (bytesUnpacked > 0) {
                pcmBuffer = formatSamples(bps, destBuffer, bytesUnpacked);
                try {
                    outputStream.write(pcmBuffer, 0, bytesUnpacked);
                } catch (java.io.IOException ioe) {
                    System.err.println("Error writing data to output file. Error: " + ioe);
                }
            }
        } // end of while
    }

    static void usage() {
        System.out.println("Usage: alac [options] inputfile outputfile");
        System.out.println("Decompresses the ALAC file specified");
        System.out.println("Options:");
        System.out.println("  -r                write output as raw PCM data. Default");
        System.out.println("                    is in WAV format.");
        System.out.println();
        System.out.println("This port of the code is (c) Peter McQuillan 2011");
        System.out.println("Original software is (c) 2005 David Hammerton");
        System.exit(1);
    }

    public static void main(String[] args) throws IOException {
        AlacContext ac;
        int outputSize;
        int totalSamples;
        int sampleRate;
        int numChannels;
        int bytePS;
        int bitPS;

        outputOpened = 0;

        setupEnvironment(args.length, args); // checks all the parameters
                                              // passed on command line

        try {
            outputStream = new java.io.FileOutputStream(outputFileN);
            outputOpened = 1;
        } catch (java.io.IOException ioe) {
            System.out.println("Cannot open output file: " + outputFileN + " : Error : " + ioe);
            outputOpened = 0;
            System.exit(1);
        }

        ac = AlacContext.openFileInput(Paths.get(inputFileN).toFile());

        numChannels = ac.getNumChannels();

        System.out.println("The Apple Lossless file has " + numChannels + " channels");

        totalSamples = ac.getNumSamples();

        System.out.println("The Apple Lossless file has " + totalSamples + " samples");

        bytePS = ac.getBytesPerSample();

        System.out.println("The Apple Lossless file has " + bytePS + " bytes per sample");

        sampleRate = ac.getSampleRate();

        bitPS = ac.getBitsPerSample();

        // write wav output headers
        if (writeWavFormat != 0) {
            WavWriter.writeHeaders(outputStream,
                    (totalSamples * bytePS * numChannels),
                    numChannels,
                    sampleRate,
                    bytePS,
                    bitPS);
        }

        // will convert the entire buffer
        getBuffer(ac);

        ac.close();

        if (outputOpened != 0) {
            outputStream.close();
        }
    }
}
