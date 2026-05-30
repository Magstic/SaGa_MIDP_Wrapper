# Makai Toushi Sa·Ga Wrapper

A MIDP wrapper for the DoJa platform game **魔界塔士サ・ガ**.

## How to Use?

1. Put the DoJa version JAR/SP/JAM into `game/`;
2. Run `ant`;
3. The MIDP version of Sa·Ga will be output to `dist/` :)

You need to configure [Apache Ant](https://ant.apache.org/bindownload.cgi) and the [J2ME SDK](https://www.oracle.com/java/technologies/javame-sdk-downloads.html) first, and place [antenna-bin-1.2.1-beta.jar](https://sourceforge.net/projects/antenna/files/antenna/Antenna%201.2.1-beta/) under `lib` :)

## What devices can play?

JVM Heap ≥ 6144 KB... I guess...

## The Code Is Messy!

Yes, I used ChatGPT 5.4.

I sincerely apologize for the code cleanliness.

## Other?

- KEmulator: Basically perfect, but unable to customize SF2(
- KEmulator nnmod: Basically perfect.
- FreeJ2ME-Plus: The emulator treats all nttdocomo imports as DoJa programs, so you need to manually modify the launch interception code;
- J2ME-Loader: Basically perfect.
- Real device: Basically perfect, but if you play this game without any speed-up, it will take at least 50 hours.
- Common: J2ME emulators using the native JRE are very prone to screen freezing due to frequent wav triggering.

## Thanks!

[Keitai Wiki](https://keitaiwiki.com/wiki/KeitaiWiki)

[Fusion Pixel Font](https://github.com/TakWolf/fusion-pixel-font)