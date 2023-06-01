/*
 * Copyright (c) 2006 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.alac;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import com.beatofthedrum.alacdecoder.Alac;
import com.beatofthedrum.alacdecoder.AlacContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import static vavi.sound.SoundUtil.volume;
import static vavix.util.DelayedWorker.later;


/**
 * Test001.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 111022 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class Test001 {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
    }

    static long time;

    static {
        time = System.getProperty("vavi.test", "").equals("ide") ? 1000 * 1000 : 10 * 1000;
    }

    byte[] format_samples(int bps, int[] src, int samcnt) {
        int temp = 0;
        int counter = 0;
        int counter2 = 0;
        byte[] dst = new byte[65536];

        switch (bps) {
        case 1:
            while (samcnt > 0) {
                dst[counter] = (byte) (0x00FF & (src[counter] + 128));
                counter++;
                samcnt--;
            }
            break;
        case 2:
            while (samcnt > 0) {
                temp = src[counter2];
                dst[counter] = (byte) temp;
                counter++;
                dst[counter] = (byte) (temp >>> 8);
                counter++;
                counter2++;
                samcnt = samcnt - 2;
            }
            break;
        case 3:
            while (samcnt > 0) {
                dst[counter] = (byte) src[counter2];
                counter++;
                counter2++;
                samcnt--;
            }
            break;
        }

        return dst;
    }

    @Property
    String alac = "src/test/resources/alac.m4a";

    @Test
    @DisplayName("proto 1")
    void test1() throws Exception {

        AlacContext ac = AlacContext.openFileInput(Paths.get(alac).toFile());
        int num_channels = ac.getNumChannels();
        int total_samples = ac.getNumSamples();
        int byteps = ac.getBytesPerSample();
        int sample_rate = ac.getSampleRate();
        int bitps = ac.getBitsPerSample();
Debug.println("num_channels: " + num_channels +
                   ", total_samples: " + total_samples +
                   ", byteps: " + byteps +
                   ", sample_rate: " + sample_rate +
                   ", bitps: " + bitps);

        AudioFormat audioFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sample_rate,
            bitps,
            num_channels,
            byteps * num_channels,
            sample_rate,
            false);
Debug.println(audioFormat);

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(audioFormat);
        line.addLineListener(ev -> System.err.println(ev.getType()));
        line.start();

        volume(line, .1d);

        byte[] pcmBuffer = null;
        int[] pDestBuffer = new int[1024 * 24 * 3]; // 24kb buffer = 4096 frames = 1 opus sample (we support max 24bps)
        int bps = ac.getBytesPerSample();
        while (!later(time).come()) {
            int bytes_unpacked = ac.unpackSamples(pDestBuffer);
            if (bytes_unpacked == -1) {
                break;
            }
            if (bytes_unpacked > 0) {
                pcmBuffer = format_samples(bps, pDestBuffer, bytes_unpacked);
            }

            line.write(pcmBuffer, 0, bytes_unpacked);
        }
        line.drain();
        line.stop();
        line.close();

        ac.close();
    }

    @Test
    @DisplayName("proto 2")
    void test2() throws Exception {
        InputStream is = Files.newInputStream(Paths.get(alac));

        Alac decoder = new Alac(is);
        int num_channels = decoder.getNumChannels();
        int total_samples = decoder.getNumSamples();
        int byteps = decoder.getBytesPerSample();
        int sample_rate = decoder.getSampleRate();
        int bitps = decoder.getBitsPerSample();
Debug.println("num_channels: " + num_channels +
                   ", total_samples: " + total_samples +
                   ", byteps: " + byteps +
                   ", sample_rate: " + sample_rate +
                   ", bitps: " + bitps);

        AudioFormat audioFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sample_rate,
            bitps,
            num_channels,
            byteps * num_channels,
            sample_rate,
            false);
Debug.println(audioFormat);

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(audioFormat);
        line.addLineListener(ev -> System.err.println(ev.getType()));
        line.start();

        volume(line, .1d);

        byte[] pcmBuffer = new byte[0xffff];
        int[] pDestBuffer = new int[1024 * 24 * 3]; // 24kb buffer = 4096 frames = 1 opus sample (we support max 24bps)
        while (!later(time).come()) {
            int bytes_unpacked = decoder.decode(pDestBuffer, pcmBuffer);
            if (bytes_unpacked == -1) {
                break;
            }
//Debug.println("bytes_unpacked: " + bytes_unpacked + "\n" + StringUtil.getDump(pcmBuffer, 64));
            line.write(pcmBuffer, 0, bytes_unpacked);
        }
        line.drain();
        line.stop();
        line.close();

        is.close();
    }
}

/* */
