package tdl.record.sourcecode.record;

import lombok.extern.slf4j.Slf4j;
import tdl.record.sourcecode.content.SourceCodeProvider;
import tdl.record.sourcecode.snapshot.file.Writer;
import tdl.record.sourcecode.time.SystemMonotonicTimeSource;
import tdl.record.sourcecode.time.TimeSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardOpenOption.CREATE;
import tdl.record.sourcecode.snapshot.SnapshotRecorderException;

@Slf4j
public class SourceCodeRecorder {

    private final SourceCodeProvider sourceCodeProvider;
    private final Path outputRecordingFilePath;
    private final TimeSource timeSource;
    private final long snapshotIntervalMillis;
    private final long recordingStartTimestamp;
    private final int keySnapshotSpacing;
    private final AtomicBoolean shouldStopJob;
    private Queue<String> tagQueue;

    SourceCodeRecorder(SourceCodeProvider sourceCodeProvider,
            Path outputRecordingFilePath,
            TimeSource timeSource,
            long recordedTimestamp,
            long snapshotIntervalMillis,
            int keySnapshotSpacing) {
        this.sourceCodeProvider = sourceCodeProvider;
        this.outputRecordingFilePath = outputRecordingFilePath;
        this.timeSource = timeSource;
        this.recordingStartTimestamp = recordedTimestamp;
        this.snapshotIntervalMillis = snapshotIntervalMillis;
        this.keySnapshotSpacing = keySnapshotSpacing;
        shouldStopJob = new AtomicBoolean(false);
        tagQueue = new ConcurrentLinkedQueue<>();
    }

    @SuppressWarnings("SameParameterValue")
    public static class Builder {

        private final SourceCodeProvider bSourceCodeProvider;
        private final Path bOutputRecordingFilePath;
        private TimeSource bTimeSource;
        private long bSnapshotIntervalMillis;
        private long bRecordingStartTimestampSec;
        private int bKeySnapshotSpacing;

        public Builder(SourceCodeProvider sourceCodeProvider, Path outputRecordingFilePath) {
            bSourceCodeProvider = sourceCodeProvider;
            bOutputRecordingFilePath = outputRecordingFilePath;
            bRecordingStartTimestampSec = System.currentTimeMillis() / 1000;
            bTimeSource = new SystemMonotonicTimeSource();
            bSnapshotIntervalMillis = TimeUnit.MINUTES.toMillis(5);
            bKeySnapshotSpacing = 5;
        }

        public Builder withRecordingStartTimestampSec(long recordingStartTimestampSec) {
            this.bRecordingStartTimestampSec = recordingStartTimestampSec;
            return this;
        }

        public Builder withTimeSource(TimeSource timeSource) {
            this.bTimeSource = timeSource;
            return this;
        }

        public Builder withSnapshotEvery(int interval, TimeUnit timeUnit) {
            this.bSnapshotIntervalMillis = timeUnit.toMillis(interval);
            return this;
        }

        public Builder withKeySnapshotSpacing(int keySnapshotSpacing) {
            this.bKeySnapshotSpacing = keySnapshotSpacing;
            return this;
        }

        public SourceCodeRecorder build() {
            return new SourceCodeRecorder(
                    bSourceCodeProvider,
                    bOutputRecordingFilePath,
                    bTimeSource,
                    bRecordingStartTimestampSec,
                    bSnapshotIntervalMillis,
                    bKeySnapshotSpacing
            );
        }
    }

    public void start(Duration recordingDuration) throws SourceCodeRecorderException {
        Writer writer;
        Path lockFilePath = Paths.get(outputRecordingFilePath + ".lock");
        try {
            Files.write(lockFilePath, new byte[0], CREATE);
            //TODO Initialise inside constructor once SnapshotsFileWriter::new is free from exceptions
            writer = new Writer(
                    outputRecordingFilePath,
                    sourceCodeProvider,
                    timeSource,
                    recordingStartTimestamp,
                    keySnapshotSpacing,
                    false
            );
        } catch (IOException | SnapshotRecorderException e) {
            throw new SourceCodeRecorderException("Failed to open destination", e);
        }

        doRecord(writer, recordingDuration);
    }

    public void tagCurrentState(String tag) throws SourceCodeRecorderException {
        log.info("Tag state with: " + tag);
        tagQueue.offer(tag);
        timeSource.wakeUpNow();
    }

    private void doRecord(Writer writer, Duration recordingDuration) {
        while (timeSource.currentTimeNano() < recordingDuration.toNanos()) {
            long timestampBeforeProcessing = timeSource.currentTimeNano();
            log.info("Snap!");

            String tag = tagQueue.poll();
            writer.takeSnapshotWithTag(tag);

            long nextTimestamp = timestampBeforeProcessing + TimeUnit.MILLISECONDS.toNanos(snapshotIntervalMillis);
            try {
                timeSource.wakeUpAt(nextTimestamp, TimeUnit.NANOSECONDS);
            } catch (InterruptedException | BrokenBarrierException e) {
                log.debug("Interrupted while sleeping", e);
            }

            // Allow a different thread to stop the recording
            if (shouldStopJob.get()) {
                break;
            }
        }
    }

    public void stop() {
        if (!shouldStopJob.get()) {
            log.info("Stopping recording");
            shouldStopJob.set(true);
            timeSource.wakeUpNow();
        } else {
            log.info("Recording already stopping");
        }
    }

    public void close() {
        try {
            log.info("Closing the source code stream");
            //TODO Ensure the file is finalised correctly

            //delete lock file after closing writing
            Path lockFilePath = Paths.get(outputRecordingFilePath + ".lock");
            Files.delete(lockFilePath);
        } catch (IOException e) {
            throw new RuntimeException("Can't delete *.lock file.", e);
        }
    }
}
