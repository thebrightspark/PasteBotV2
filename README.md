# PasteBotV2

A Discord bot to automatically upload text files to any of the following sites:

- https://mclo.gs/
- https://paste.gg/
- https://pastebin.com/

## Running

To host the bot for yourself, you simply need Java 17+ and to set the following environment variables:

- `PASTEBOTV2_BOT_TOKEN`
  - The Discord bot token
- `PASTEBOTV2_PASTEBIN_KEY`
  - The Pastebin API key

Grab the JAR file from the [latest release](https://github.com/thebrightspark/PasteBotV2/releases/latest).

The application can then be simply run using:

```bash
java -jar PasteBotV2-1.2.1.jar
```

By default, Logback's file logging is disabled in the [provided config file](src/main/resources/logback.xml), but a
custom one can be supplied (even if just the same file but with the file appender line uncommented) using the following
JVM argument:

```bash
-Dlogback.configurationFile=/path/to/file/logback.xml
```
