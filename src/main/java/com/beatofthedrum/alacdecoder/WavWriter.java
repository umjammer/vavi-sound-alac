/*
 * Copyright (c) 2011 Peter McQuillan
 *
 * All Rights Reserved.
 *
 * Distributed under the BSD Software License (see license.txt)
 */

package com.beatofthedrum.alacdecoder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import static java.lang.System.getLogger;


/**
 * WavWriter.
 */
public class WavWriter {

	private static final Logger logger = getLogger(WavWriter.class.getName());

    static void writeUInt32(FileOutputStream f, int v) {
        byte[] outputBytes = new byte[4];

        outputBytes[3] = (byte) (v >>> 24);
        outputBytes[2] = (byte) (v >>> 16);
        outputBytes[1] = (byte) (v >>> 8);
        outputBytes[0] = (byte) (v);
        try {
            f.write(outputBytes, 0, 4);
        } catch (IOException ioe) {
			logger.log(Level.DEBUG, ioe.toString());
		}
    }

    static void writeUInt16(FileOutputStream f, int v) {
        byte[] outputBytes = new byte[2];

        outputBytes[1] = (byte) (v >>> 8);
        outputBytes[0] = (byte) (v);
        try {
            f.write(outputBytes, 0, 2);
        } catch (IOException ioe) {
			logger.log(Level.DEBUG, ioe.toString());
        }
    }

    public static void writeHeaders(FileOutputStream f, int dataSize, int numChannels, int sampleRate, int bytesPerSample, int bitsPerSample) {
        byte[] buffAsBytes = new byte[4];

        // write RIFF header
        buffAsBytes[0] = 82;
        buffAsBytes[1] = 73;
        buffAsBytes[2] = 70;
        buffAsBytes[3] = 70; // "RIFF" ascii values

        try {
            f.write(buffAsBytes, 0, 4);
        } catch (IOException ioe) {
			logger.log(Level.DEBUG, ioe.toString());
        }

        writeUInt32(f, (36 + dataSize));
        buffAsBytes[0] = 87;
        buffAsBytes[1] = 65;
        buffAsBytes[2] = 86;
        buffAsBytes[3] = 69; // "WAVE" ascii values

        try {
            f.write(buffAsBytes, 0, 4);
        } catch (IOException ioe) {
			logger.log(Level.DEBUG, ioe.toString());
        }

        // write fmt header
        buffAsBytes[0] = 102;
        buffAsBytes[1] = 109;
        buffAsBytes[2] = 116;
        buffAsBytes[3] = 32;  // "fmt " ascii values

        try {
            f.write(buffAsBytes, 0, 4);
        } catch (IOException ioe) {
			logger.log(Level.DEBUG, ioe.toString());
        }

        writeUInt32(f, 16);
        writeUInt16(f, 1); // PCM data
        writeUInt16(f, numChannels);
        writeUInt32(f, sampleRate);
        writeUInt32(f, (sampleRate * numChannels * bytesPerSample)); // byterate
        writeUInt16(f, (numChannels * bytesPerSample));
        writeUInt16(f, bitsPerSample);

        /* write data header */
        buffAsBytes[0] = 100;
        buffAsBytes[1] = 97;
        buffAsBytes[2] = 116;
        buffAsBytes[3] = 97;  // "data" ascii values

        try {
            f.write(buffAsBytes, 0, 4);
        } catch (IOException ioe) {
			logger.log(Level.DEBUG, ioe.toString());
        }

        writeUInt32(f, dataSize);
    }
}

