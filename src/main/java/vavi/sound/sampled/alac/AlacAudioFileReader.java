/*
 * Copyright (c) 2005 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.alac;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.HashMap;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;

import com.beatofthedrum.alacdecoder.Alac;

import static java.lang.System.getLogger;


/**
 * Provider for ALAC audio file reading services. This implementation can parse
 * the format information from ALAC audio file, and can produce audio input
 * streams from files of this type.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 111022 nsano initial version <br>
 */
public class AlacAudioFileReader extends AudioFileReader {

    private static final Logger logger = getLogger(AlacAudioFileReader.class.getName());

    @Override
    public AudioFileFormat getAudioFileFormat(File file) throws UnsupportedAudioFileException, IOException {
        //noinspection IOStreamConstructor
        try (InputStream inputStream = new FileInputStream(file)) { // must be FileInputStream because Alac accept it to use channel in it
            return getAudioFileFormat(inputStream, (int) file.length());
        }
    }

    @Override
    public AudioFileFormat getAudioFileFormat(URL url) throws UnsupportedAudioFileException, IOException {
        try (InputStream inputStream = url.openStream()) {
            return getAudioFileFormat(inputStream instanceof BufferedInputStream ? inputStream : new BufferedInputStream(inputStream, Integer.MAX_VALUE - 8));
        }
    }

    /** @param stream mark must be supported and mark size must be larger than the alac data size */
    @Override
    public AudioFileFormat getAudioFileFormat(InputStream stream) throws UnsupportedAudioFileException, IOException {
        return getAudioFileFormat(stream, AudioSystem.NOT_SPECIFIED);
    }

    /**
     * Return the AudioFileFormat from the given InputStream. Implementation.
     *
     * @param bitStream input to decode
     * @param mediaLength unused
     * @return an AudioInputStream object based on the audio file data contained
     *         in the input stream.
     *         mark must be supported and mark size must be larger than the alac data size
     * @exception UnsupportedAudioFileException if the File does not point to a
     *                valid audio file data recognized by the system.
     * @exception IOException if an I/O exception occurs.
     */
    protected AudioFileFormat getAudioFileFormat(InputStream bitStream, int mediaLength) throws UnsupportedAudioFileException, IOException {
logger.log(Level.DEBUG, "enter available: " + bitStream.available());
        Alac alac;
        try {
            alac = new Alac(bitStream);
        } catch (Exception e) {
logger.log(Level.TRACE, "error exit available: " + bitStream.available());
logger.log(Level.TRACE, e.getMessage(), e);
            throw (UnsupportedAudioFileException) new UnsupportedAudioFileException(e.getMessage()).initCause(e);
        }
        // TODO AudioSystem.NOT_SPECIFIED cause IllegalArgumentException at Clip#open()
        AudioFormat format = new AudioFormat(AlacEncoding.ALAC, alac.getSampleRate(), alac.getSampleSizeInBits(), alac.getChannels(), AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, true, new HashMap<>() {{
            put("alac", alac);
        }});
        return new AudioFileFormat(AlacFileFormatType.ALAC, format, AudioSystem.NOT_SPECIFIED);
    }

    @Override
    public AudioInputStream getAudioInputStream(File file) throws UnsupportedAudioFileException, IOException {
        //noinspection IOStreamConstructor
        InputStream inputStream = new FileInputStream(file); // must be FileInputStream because Alac accept it to use channel in it
        try {
            return getAudioInputStream(inputStream, (int) file.length());
        } catch (UnsupportedAudioFileException | IOException e) {
            inputStream.close();
            throw e;
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(URL url) throws UnsupportedAudioFileException, IOException {
        InputStream inputStream = url.openStream();
        try {
            return getAudioInputStream(inputStream instanceof BufferedInputStream ? inputStream : new BufferedInputStream(inputStream, Integer.MAX_VALUE - 8));
        } catch (UnsupportedAudioFileException | IOException e) {
            inputStream.close();
            throw e;
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(InputStream stream) throws UnsupportedAudioFileException, IOException {
        return getAudioInputStream(stream, AudioSystem.NOT_SPECIFIED);
    }

    /**
     * Obtains an audio input stream from the input stream provided. The stream
     * must point to valid audio file data.
     *
     * @param inputStream the input stream from which the AudioInputStream
     *            should be constructed.
     * @param mediaLength unused
     * @return an AudioInputStream object based on the audio file data contained
     *         in the input stream.
     * @exception UnsupportedAudioFileException if the File does not point to a
     *                valid audio file data recognized by the system.
     * @exception IOException if an I/O exception occurs.
     */
    protected AudioInputStream getAudioInputStream(InputStream inputStream, int mediaLength) throws UnsupportedAudioFileException, IOException {
        AudioFileFormat audioFileFormat = getAudioFileFormat(inputStream, mediaLength);
        return new AudioInputStream(inputStream, audioFileFormat.getFormat(), audioFileFormat.getFrameLength());
    }
}
