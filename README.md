[![Release](https://jitpack.io/v/umjammer/osxmidi4j.svg)](https://jitpack.io/#umjammer/osxmidi4j)
[![Java CI](https://github.com/umjammer/osxmidi4j/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/osxmidi4j/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/osxmidi4j/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/umjammer/osxmidi4j/actions/workflows/codeql-analysis.yml)
![Java](https://img.shields.io/badge/Java-17-b07219)

# osxmidi4j

Java MIDI SPI over CoreMIDI Framework.

<img alt="midi logo" src="https://github.com/umjammer/osxmidi4j/assets/493908/7803dfc0-6bed-40d5-9e71-1d885de3ff97" width="160" />

osxmidi4j will register itself automatically to the Java runtime as a MIDI device provider.<br/>
Call the standard Java MIDI API All osxmidi4j devices will be prefixed with "CoreMidi - "

this library adds loopback midi device also.  

tested with MacOS 14.3.1

## Install

* [Maven](https://jitpack.io/#umjammer/osxmidi4j)

## References

 * https://github.com/DerekCook/CoreMidi4J

## TODO

 * move CoreMIDI part into rococoa?
