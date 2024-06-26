/*
 * Copyright (c) 2005 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.alac;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import com.beatofthedrum.alacdecoder.Alac;

import vavi.io.OutputEngine;
import vavi.io.OutputEngineInputStream;


/**
 * Converts an ALAC bitstream into a PCM 16bits/sample audio stream.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 111022 nsano initial version <br>
 */
class Alac2PcmAudioInputStream extends AudioInputStream {

    /** */
    public Alac2PcmAudioInputStream(InputStream in, AudioFormat audioFormat, int length, Alac alac) throws IOException {
        super(new OutputEngineInputStream(new AlacOutputEngine(alac)), audioFormat, length);
    }

    /** */
    private static class AlacOutputEngine implements OutputEngine {

        /** */
        private DataOutputStream out;

        /** */
        private final Alac alac;

        /** */
        public AlacOutputEngine(Alac alac) throws IOException {
            this.alac = alac;
        }

        /** */
        @Override
        public void initialize(OutputStream out) throws IOException {
            if (this.out != null) {
                throw new IOException("Already initialized");
            } else {
                this.out = new DataOutputStream(out);
            }
        }

        /** */
        private byte[] pcmBuffer = new byte[0xffff];

        /** 24kb buffer = 4096 frames = 1 opus sample (we support max 24bps) */
        private int[] pDestBuffer = new int[1024 * 24 * 3];

        /** */
        @Override
        public void execute() throws IOException {
            if (out == null) {
                throw new IOException("Not yet initialized");
            } else {
                int bytesUnpacked = alac.decode(pDestBuffer, pcmBuffer);
                if (bytesUnpacked == -1) {
                    out.close();
                } else {
                    out.write(pcmBuffer, 0, bytesUnpacked);
                }
            }
        }

        /** */
        @Override
        public void finish() throws IOException {
            alac.close();
        }
    }
}
