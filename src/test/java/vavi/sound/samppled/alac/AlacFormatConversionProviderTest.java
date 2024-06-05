/*
 * Copyright (c) 2022 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.samppled.alac;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;
import javax.sound.sampled.spi.FormatConversionProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vavi.sound.SoundUtil;
import vavi.sound.sampled.alac.AlacAudioFileReader;
import vavi.sound.sampled.alac.AlacFormatConversionProvider;
import vavi.util.Debug;
import vavi.util.StringUtil;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vavi.sound.SoundUtil.volume;
import static vavix.util.DelayedWorker.later;


/**
 * AlacFormatConversionProviderTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2022/02/20 umjammer initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class AlacFormatConversionProviderTest {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
    }

    static final double volume = Double.parseDouble(System.getProperty("vavi.test.volume",  "0.2"));

    static long time;

    static {
        System.setProperty("vavi.util.logging.VaviFormatter.extraClassMethod", "org\\.tritonus\\.share\\.TDebug#out");

        time = System.getProperty("vavi.test", "").equals("ide") ? 1000 * 1000 : 9 * 1000;
    }

    @Property
    String alac = "src/test/resources/alac.m4a";

    @Test
    void testX() throws Exception {
        ServiceLoader<AudioFileReader> loader = ServiceLoader.load(AudioFileReader.class);
        AtomicBoolean result = new AtomicBoolean();
        loader.forEach(spi -> {
System.err.println(spi);
            if (spi.getClass().getName().contains("alac")) {
                result.set(true);
            }
        });
        assertTrue(result.get());

        ServiceLoader<FormatConversionProvider> loader2 = ServiceLoader.load(FormatConversionProvider.class);
        AtomicBoolean result2 = new AtomicBoolean();
        loader2.forEach(spi -> {
System.err.println(spi);
            if (spi.getClass().getName().contains("alac")) {
                result2.set(true);
            }
        });
        assertTrue(result2.get());
    }

    // @see BufferedInputStream
    static final int BUF_MAX = Integer.MAX_VALUE - 8;

    @Test
    @DisplayName("directly")
    void test0() throws Exception {

        Path path = Paths.get(alac);
        AudioInputStream sourceAis = new AlacAudioFileReader().getAudioInputStream(new BufferedInputStream(Files.newInputStream(path), BUF_MAX));

        AudioFormat inAudioFormat = sourceAis.getFormat();
Debug.println("IN: " + inAudioFormat);
        AudioFormat outAudioFormat = new AudioFormat(
            44100,
            16,
            2,
            true,
            false);
Debug.println("OUT: " + outAudioFormat);

        assertTrue(AudioSystem.isConversionSupported(outAudioFormat, inAudioFormat));

        AudioInputStream pcmAis = new AlacFormatConversionProvider().getAudioInputStream(outAudioFormat, sourceAis);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmAis.getFormat());
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(pcmAis.getFormat());
        line.addLineListener(ev -> Debug.println(ev.getType()));
        line.start();

        volume(line, volume);

        byte[] buf = new byte[1024];
        while (!later(time).come()) {
            int r = pcmAis.read(buf, 0, 1024);
            if (r < 0) {
                break;
            }
            line.write(buf, 0, r);
        }
        line.drain();
        line.stop();
        line.close();
    }

    @Test
    @DisplayName("as spi")
    void test1() throws Exception {

        Path path = Paths.get(alac);
        AudioInputStream sourceAis = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path), BUF_MAX));

        AudioFormat inAudioFormat = sourceAis.getFormat();
Debug.println("IN: " + inAudioFormat);
        AudioFormat outAudioFormat = new AudioFormat(
            44100,
            16,
            2,
            true,
            false);
Debug.println("OUT: " + outAudioFormat);

        assertTrue(AudioSystem.isConversionSupported(outAudioFormat, inAudioFormat));

        AudioInputStream pcmAis = AudioSystem.getAudioInputStream(outAudioFormat, sourceAis);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmAis.getFormat());
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(pcmAis.getFormat());
        line.addLineListener(ev -> Debug.println(ev.getType()));
        line.start();

        volume(line, volume);

        byte[] buf = new byte[1024];
        while (!later(time).come()) {
            int r = pcmAis.read(buf, 0, 1024);
            if (r < 0) {
                break;
            }
            line.write(buf, 0, r);
        }
        line.drain();
        line.stop();
        line.close();
    }

    @Test
    @DisplayName("three input types")
    void test4() throws Exception {
        Path path = Paths.get(alac);
        AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path), BUF_MAX));
        assertNotNull(ais.getFormat().properties().get("alac"));
        ais = AudioSystem.getAudioInputStream(path.toFile());
        assertNotNull(ais.getFormat().properties().get("alac"));
        ais = AudioSystem.getAudioInputStream(path.toUri().toURL());
        assertNotNull(ais.getFormat().properties().get("alac"));
    }

    @Test
    @DisplayName("when unsupported file coming")
    void test5() throws Exception {
        InputStream is = AlacFormatConversionProviderTest.class.getResourceAsStream("/test.caf");
        int available = is.available();
        assertThrows(UnsupportedAudioFileException.class, () -> {
Debug.println(StringUtil.paramString(is));
            AudioInputStream ais = AudioSystem.getAudioInputStream(is);
Debug.println(ais.getFormat());
        });
        assertEquals(available, is.available()); // spi must not consume input stream even one byte
    }

    @Test
    @DisplayName("clip")
    void test3() throws Exception {

        AudioInputStream ais = AudioSystem.getAudioInputStream(Paths.get(alac).toFile());
Debug.println(ais.getFormat());

        Clip clip = AudioSystem.getClip();
CountDownLatch cdl = new CountDownLatch(1);
clip.addLineListener(ev -> {
 Debug.println(ev.getType());
 if (ev.getType() == LineEvent.Type.STOP)
  cdl.countDown();
});
        clip.open(AudioSystem.getAudioInputStream(new AudioFormat(44100, 16, 2, true, false), ais));
SoundUtil.volume(clip, volume);
        clip.start();
if (!System.getProperty("vavi.test", "").equals("ide")) {
 Thread.sleep(10 * 1000);
 clip.stop();
 Debug.println("not on ide");
} else {
 cdl.await();
}
        clip.drain();
        clip.stop();
        clip.close();
    }
}
