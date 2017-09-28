[![Java Version](http://img.shields.io/badge/Java-1.8-blue.svg)](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
[![Download](https://api.bintray.com/packages/julianghionoiu/maven/dev-sourcecode-record/images/download.svg)](https://bintray.com/julianghionoiu/maven/dev-sourcecode-record/_latestVersion)
[![Codeship Status for julianghionoiu/dev-sourcecode-record](https://img.shields.io/codeship/0d0facf0-757b-0135-e8f7-4a0a8123458a/master.svg)](https://codeship.com/projects/244257)
[![Coverage Status](https://img.shields.io/codecov/c/github/julianghionoiu/dev-sourcecode-record.svg)](https://codecov.io/gh/julianghionoiu/dev-sourcecode-record)

Library designed for recording the source code history of a programming sessions.
The file generated is a SRCS file enabled for streaming. (Fragmented Snapshots)

## To use as a library

### Add as Maven dependency

Add a dependency to `tdl:dev-sourcecode-record` in `compile` scope. See `bintray` shield for latest release number.
```xml
<dependency>
  <groupId>ro.ghionoiu</groupId>
  <artifactId>dev-sourcecode-record</artifactId>
  <version>X.Y.Z</version>
</dependency>
```

### Usage

The following example records a folder for 1 hour with 1 snapshot every minute. 
There will be one key snapshot (full dir copy) to 9 patch snapshots.

```java
        CopyFromDirectorySourceCodeProvider sourceCodeProvider = new CopyFromDirectorySourceCodeProvider(
                Paths.get("./sourceCodeDir"));
        String destinationPath = Paths.get("./sourcecode.srcs");
        SourceCodeRecorder sourceCodeRecorder = new SourceCodeRecorder.Builder(sourceCodeProvider, destinationPath)
                .withTimeSource(new SystemMonotonicTimeSource())
                .withSnapshotEvery(1, TimeUnit.MINUTES)
                .withKeySnapshotSpacing(10)
                .build();


        sourceCodeRecorder.start(Duration.of(1, ChronoUnit.HOURS));
        sourceCodeRecorder.close();
```

To monitor the **recording progress** you register a `SourceCodeRecordingListener`. 
The following example displays the metrics to the screen every 1 minute:

```java
        SourceCodeMetricsCollector sourceCodeMetricsCollector = new SourceCodeMetricsCollector();
        SourceCodeRecorder sourceCodeRecorder = new SourceCodeRecorder.Builder(sourceCodeProvider, destinationPath)
                .withRecordingListener(sourceCodeMetricsCollector)
                .build();
        
        //Issue performance updates
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Recorded "+sourceCodeMetricsCollector.getTotalSnapshots() + " snapshots"+
                    ", last snapshot processed in "+ sourceCodeRecordingListener.getLastSnapshotProcessingTimeNano() + " nanos");
            }
        }, 0, 60000);
```

To **gracefully stop the recording** you must ensure that you call the `stop()` on the recording.
You do this by registering `shutdownHook`:
```java
        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sourceCodeRecorder.stop();
            try {
                mainThread.join();
            } catch (InterruptedException e) {
                log.warn("Could not join main thread", e);
            }
        }));
```

## Working with the sourcecode stream (SRCS)

List the contents of a SRCS file
```
java -jar build/libs/dev-sourcecode-record-0.0.2-SNAPSHOT-all.jar list \
    --input snapshot.srcs
```

Export one individual snapshot
```
java -jar build/libs/dev-sourcecode-record-0.0.2-SNAPSHOT-all.jar export \
    --input snapshot.srcs --time 0 --output ./xyz/frames
```

Export entire file to Git
```
java -jar build/libs/dev-sourcecode-record-0.0.2-SNAPSHOT-all.jar convert-to-git \
    --input snapshot.srcs --output ./xyz/to_git
```


## The SRCS file format

The SRCS file format is divided into two parts.

1. Header

    The header contains 14 bytes: 6 for magic bytes `SRCSTM` and 8 byte to store
    UNIX timestamp when the snapshot was first recorded. The timestamp is stored
    in Little Endian format.

2. Segments

    After the header, the body will contains several segments that contains
    snapshot of the working directory. The content of the segments are

    a. Magic bytes, 6-bytes string.
        
        This contains either `SRCKEY` or `SRCPTC` magic bytes. The first one
        means that the segment is Key Snapshot while the latter means Patch
        Snapshot.

        Key Snapshot is snapshot that contains the whole files in the working
        directory at the time snapshot was taken. While Patch Snapshot contains
        only the difference between the files in the directory and the files
        taken the previous snapshots. There can be more than one Patch Snapshot
        between two Key Snapshots.

    b. Timestamp, 8-bytes long integer in Little Endian format.

        This timestamp stores the number of seconds since the first snapshot was
        taken. Naturally the timestamp of the first snapshot is zero.

    c. Size, 8-bytes long integer in Little Endian format.

        This contains the size of the payload stored in the end of the segment.

    d. Checksum, 20-bytes string.

        This contains MD5 hash of the payload data for consistency checking.

    e. Tag, 256-bytes string.

        This contains the tag name of the current snapshot. When the file is
        being exported to git repository, the tag will be used to make git tag
        of the snapshot's git commit.

    f. Payload.

        For Key Snapshot, the payload contains Zip data containing the snapshot.
        As for Patch Snapshot, the payload contains patch in Diff format. The
        diff data is compressed using Gzip.

## Development

### Build and run as command-line app

This will create a runnable fat jar:
```
./gradlew build shadowJar -i
```

You can use the app to record changes in a folder
```
java -jar build/libs/dev-sourcecode-record-0.0.2-SNAPSHOT-all.jar record \
    --source xyz/a_source --output snapshot.srcs
```

### Install to mavenLocal

If you want to build the SNAPSHOT version locally you can install to the local Maven cache
```
./gradlew -x test clean install
```

### Release to jcenter and mavenCentral

The CI server is configured to pushs release branches to Bintray.
You trigger the process by running the `release` command locally. 

The command will increment the release number and create and annotated tag:
```bash
./gradlew release
git push --tags
```