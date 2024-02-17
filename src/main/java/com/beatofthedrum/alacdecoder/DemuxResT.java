/*
 * Copyright (c) 2011 Peter McQuillan
 *
 * All Rights Reserved.
 *
 * Distributed under the BSD Software License (see license.txt)
 */

package com.beatofthedrum.alacdecoder;

import java.io.IOException;


/**
 * DemuxResT.
 */
class DemuxResT {

    /**
     * SampleInfo.
     */
    static class SampleInfo {
        int sampleCount = 0;
        int sampleDuration = 0;
    }

    /**
     * @author Denis Tulskiy
     * @version 4/9/11
     */
    static class ChunkInfo {
        int firstChunk;
        int samplesPerChunk;
        int sampleDescIndex;
    }

    /** */
    static class SampleDuration {
        int sampleByteSize = 0;
        int sampleDuration = 0;
    }

    int formatRead;

    int numChannels;
    int sampleSize;
    int sampleRate;
    int format;
    private int[] buf = new int[1024 * 80];

    SampleInfo[] timeToSample = new SampleInfo[16];
    int numTimeToSamples;

    int[] sampleByteSize;

    int codecDataLen;

    int[] codecData = new int[1024];

    int[] stco;
    ChunkInfo[] stsc;

    int mdatLen;

    DemuxResT() {
        // not sure how many of these I need, so make 16
        for (int i = 0; i < 16; i++) {
            timeToSample[i] = new SampleInfo();
        }
    }

    void getSampleInfo(int sampleNum, SampleDuration sampleInfo) throws IOException {
        int durationIndexAccum = 0;
        int durationCurIndex = 0;

        if (sampleNum >= this.sampleByteSize.length) {
            throw new IOException("sample " + sampleNum + " does not exist ");
        }

        if (this.numTimeToSamples == 0) { // was null
            throw new IOException("no time to samples");
        }
        while ((this.timeToSample[durationCurIndex].sampleCount + durationIndexAccum) <= sampleNum) {
            durationIndexAccum += this.timeToSample[durationCurIndex].sampleCount;
            durationCurIndex++;
            if (durationCurIndex >= this.numTimeToSamples) {
                throw new IOException("sample " + sampleNum + " does not have a duration");
            }
        }

        sampleInfo.sampleDuration = this.timeToSample[durationCurIndex].sampleDuration;
        sampleInfo.sampleByteSize = this.sampleByteSize[sampleNum];
    }
}
