package org.chenile.filewatch.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.chenile.filewatch.errorcodes.ErrorCodes;
import org.chenile.filewatch.model.FileWatchDefinition;
import org.chenile.trigger.TriggerHeaders;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.TriggerSources;
import org.chenile.trigger.model.TriggerInput;
import org.chenile.utils.stream.Looper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes a completed header file and its records file. Each record is raised as the definition's
 * event through {@link TriggerService}, with source FILE and the idempotency key {@code
 * file:<fileWatchId>:<file name>:<sha256 of the file>:<record index>}: a file processed again after
 * a crash does not fire its records twice, while a changed file with the same name is new.
 *
 * <p>The header file's {@code x-} properties become event headers; the others go into the trigger
 * context. With DirectTriggerService a subscriber failure moves the files to the error folder; with
 * process management's durable trigger service the files are processed once every record is
 * accepted, and failures are retried by the trigger layer.
 */
public class FileProcessor {
	private static final Logger logger = LoggerFactory.getLogger(FileProcessor.class);

	public static final String ACTUAL_FILE_NAME = "actual.file";
	public static final String ACTUAL_FILE_ENCODING = "actual.file.encoding";
	public static final String HEADER_EXTENSION = ".header";
	public static final String LAST_PROPERTY = "last.property";

	private final TriggerService triggerService;
	private final FileWatchEventLogger eventLogger;
	private final Looper looper;

	public FileProcessor(TriggerService triggerService, FileWatchEventLogger eventLogger, Looper looper) {
		this.triggerService = triggerService;
		this.eventLogger = eventLogger;
		this.looper = looper;
	}

	public boolean shouldProcess(Path fileDiscovered) {
		return fileDiscovered != null && fileDiscovered.getFileName() != null
				&& fileDiscovered.getFileName().toString().endsWith(HEADER_EXTENSION);
	}

	public void processFile(FileWatchDefinition fileWatchDefinition, Path fileDiscovered, Path processedDir) {
		processFile(fileWatchDefinition, fileDiscovered, processedDir, null);
	}

	public void processFile(FileWatchDefinition fileWatchDefinition, Path fileDiscovered, Path processedDir,
			Path errorDir) {
		if (!shouldProcess(fileDiscovered)) {
			return;
		}
		if (!Files.isRegularFile(fileDiscovered)) {
			return;
		}

		Properties headers = extractHeaders(fileDiscovered);
		if (headers == null) {
			return;
		}
		if (!headers.containsKey(ACTUAL_FILE_NAME) || !headers.containsKey(ACTUAL_FILE_ENCODING)) {
			eventLogger.logError(null, ErrorCodes.MISSING_HEADER_PROPERTIES.getSubError(),
					"Header file " + fileDiscovered + " does not contain required headers "
							+ ACTUAL_FILE_NAME + " or " + ACTUAL_FILE_ENCODING);
			moveFilesToError(fileDiscovered, null, errorDir);
			return;
		}

		Path recordsFile;
		try {
			recordsFile = resolveActualFile(fileDiscovered, headers.getProperty(ACTUAL_FILE_NAME));
		} catch (IllegalArgumentException e) {
			eventLogger.logError(null, ErrorCodes.CANNOT_PROCESS_FILE.getSubError(), e.getMessage(), e);
			moveFilesToError(fileDiscovered, null, errorDir);
			return;
		}

		try {
			String fileHash = sha256(recordsFile);
			try (InputStream inputStream = Files.newInputStream(recordsFile)) {
				AtomicInteger recordIndex = new AtomicInteger();
				looper.loop(inputStream, headers.getProperty(ACTUAL_FILE_ENCODING), headers,
						fileWatchDefinition.getRecordClass(),
						record -> triggerService.trigger(triggerInput(fileWatchDefinition, recordsFile, fileHash,
								recordIndex.getAndIncrement(), headers, record)));
			}
			moveFilesToProcessed(fileDiscovered, recordsFile, processedDir);
		} catch (Exception e) {
			eventLogger.logError(null, ErrorCodes.CANNOT_PROCESS_FILE.getSubError(),
					"Unable to process file " + recordsFile, e);
			moveFilesToError(fileDiscovered, recordsFile, errorDir);
		}
	}

	private Path resolveActualFile(Path headerFile, String actualFileName) {
		Path watchDir = headerFile.getParent();
		Path recordsFile = watchDir.resolve(actualFileName).normalize();
		if (!recordsFile.startsWith(watchDir.normalize())) {
			throw new IllegalArgumentException("Header file " + headerFile + " refers to file outside watch directory: "
					+ actualFileName);
		}
		if (!Files.isRegularFile(recordsFile)) {
			throw new IllegalArgumentException("Header file " + headerFile + " refers to missing or non-regular file: "
					+ recordsFile);
		}
		return recordsFile;
	}

	private TriggerInput triggerInput(FileWatchDefinition definition, Path recordsFile, String fileHash,
			int recordIndex, Properties headerFile, Object record) {
		String fileName = recordsFile.getFileName().toString();
		TriggerInput input = new TriggerInput();
		input.eventId = definition.getEventId();
		input.source = TriggerSources.FILE;
		input.payload = record;
		input.idempotencyKey = "file:" + definition.getFileWatchId() + ":" + fileName + ":" + fileHash + ":"
				+ recordIndex;
		Map<String, Object> context = new LinkedHashMap<>();
		headerFile.forEach((key, value) -> {
			String name = String.valueOf(key);
			if (name.startsWith("x-")) input.headers.put(name, String.valueOf(value));
			else context.put(name, String.valueOf(value));
		});
		input.headers.put(TriggerHeaders.FILE_WATCH_ID, definition.getFileWatchId());
		input.headers.put(TriggerHeaders.FILE_NAME, fileName);
		input.headers.put(TriggerHeaders.RECORD_INDEX, String.valueOf(recordIndex));
		input.triggerContext.putAll(context);
		return input;
	}

	private String sha256(Path file) throws IOException {
		try (DigestInputStream in = new DigestInputStream(Files.newInputStream(file),
				MessageDigest.getInstance("SHA-256"))) {
			in.transferTo(OutputStream.nullOutputStream());
			return HexFormat.of().formatHex(in.getMessageDigest().digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	private void moveFilesToProcessed(Path headerFilename, Path recordsFile, Path processedDir) {
		move(headerFilename, processedDir, ErrorCodes.CANNOT_MOVE_TO_PROCESSED.getSubError());
		move(recordsFile, processedDir, ErrorCodes.CANNOT_MOVE_TO_PROCESSED.getSubError());
	}

	private void moveFilesToError(Path headerFilename, Path recordsFile, Path errorDir) {
		if (errorDir == null) {
			return;
		}
		move(headerFilename, errorDir, ErrorCodes.CANNOT_MOVE_TO_ERROR.getSubError());
		if (recordsFile != null) {
			move(recordsFile, errorDir, ErrorCodes.CANNOT_MOVE_TO_ERROR.getSubError());
		}
	}

	private void move(Path file, Path targetDir, String errorCode) {
		try {
			if (file == null || !Files.exists(file)) {
				return;
			}
			Files.createDirectories(targetDir);
			Path target = targetDir.resolve(file.getFileName());
			Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			eventLogger.logError(null, errorCode,
					"Cannot move the file " + file + " to directory " + targetDir, e);
			logger.error("Cannot move file {} to {}", file, targetDir, e);
		}
	}

	private Properties extractHeaders(Path file) {
		try (InputStream input = Files.newInputStream(file)) {
			Properties props = new Properties();
			props.load(input);
			if (props.containsKey(LAST_PROPERTY)) {
				props.remove(LAST_PROPERTY);
			} else {
				eventLogger.logWarning(ErrorCodes.MISSING_HEADER_PROPERTIES.getSubError(),
						"Header file " + file + " does not contain the expected property "
								+ LAST_PROPERTY + " Ignoring it");
				return null;
			}
			return props;
		} catch (IOException ex) {
			eventLogger.logError(null, ErrorCodes.CANNOT_PROCESS_FILE.getSubError(),
					"Cannot read Header file " + file, ex);
			return null;
		}
	}
}
