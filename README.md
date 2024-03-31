[![Release](https://jitpack.io/v/umjammer/vavi-sound-alac.svg)](https://jitpack.io/#umjammer/vavi-sound-alac)
[![Java CI](https://github.com/umjammer/vavi-sound-alac/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-sound-alac/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/vavi-sound-alac/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/umjammer/vavi-sound-alac/actions/workflows/codeql-analysis.yml)
![Java](https://img.shields.io/badge/Java-17-b07219)
[![Parent](https://img.shields.io/badge/Parent-vavi--sound--sandbox-pink)](https://github.com/umjammer/vavi-sound-sandbox)

# vavi-sound-alac

<img src="https://user-images.githubusercontent.com/493908/194699473-aaee645a-d178-4d9f-b220-9de335bf4c62.png" width="320" alt="ALAC logo"/><sub>© <a href="https://alac.macosforge.org">Apple</a></sub>

Pure Java Apple Lossless decoder (Java Sound SPI)

it works as `javax.sound.sampled.spi`</br>
this project is a fork of [soiaf/Java-Apple-Lossless-decoder](https://github.com/soiaf/Java-Apple-Lossless-decoder)

## Install

https://jitpack.io/#umjammer/vavi-sound-alac

## Usage

```java
    AudioInputStream ais = AudioSystem.getAudioInputStream(Paths.get(alac).toFile());
    Clip clip = AudioSystem.getClip();
    clip.open(AudioSystem.getAudioInputStream(new AudioFormat(44100, 16, 2, true, false), ais));
    clip.loop(Clip.LOOP_CONTINUOUSLY);
```

## References

 * https://github.com/flacon/alacenc

## TODO

 * play clip w/o format conversion (possible?)

---

[Original](src/main/java/com/beatofthedrum/alacdecoder/readme.md)
