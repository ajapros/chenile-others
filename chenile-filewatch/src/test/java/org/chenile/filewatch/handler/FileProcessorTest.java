package org.chenile.filewatch.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.chenile.filewatch.model.FileWatchDefinition;
import org.chenile.filewatch.test.service.FooModel;
import org.chenile.trigger.TriggerHeaders;
import org.chenile.trigger.TriggerService;
import org.chenile.trigger.TriggerSources;
import org.chenile.trigger.model.TriggerInput;
import org.chenile.utils.stream.Looper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

class FileProcessorTest {
	private FileSystem fileSystem;
	private Path watchDir;
	private Path processedDir;
	private Path errorDir;
	private TriggerService triggerService;
	private FileProcessor fileProcessor;
	private FileWatchDefinition definition;

	@BeforeEach
	void setUp() throws Exception {
		fileSystem = Jimfs.newFileSystem(Configuration.unix());
		watchDir = fileSystem.getPath("/watch/foo");
		processedDir = fileSystem.getPath("/processed/foo");
		errorDir = fileSystem.getPath("/error/foo");
		Files.createDirectories(watchDir);
		Files.createDirectories(processedDir);
		Files.createDirectories(errorDir);
		triggerService = mock(TriggerService.class);
		fileProcessor = new FileProcessor(triggerService, mock(FileWatchEventLogger.class), new Looper<>());
		definition = new FileWatchDefinition();
		definition.setFileWatchId("foo");
		definition.setRecordClass(FooModel.class);
	}

	@Test
	void ignoresNonHeaderFiles() {
		assertFalse(fileProcessor.shouldProcess(watchDir.resolve("data.csv")));
		assertTrue(fileProcessor.shouldProcess(watchDir.resolve("data.header")));
	}

	@Test
	void eachRecordIsTriggeredWithFileHeadersAndAFileBasedKey() throws Exception {
		Path actual = watchDir.resolve("data.csv");
		Path header = watchDir.resolve("data.header");
		Files.write(actual, List.of("bar,baz", "bar,baz", "bar1,baz1"), StandardCharsets.UTF_8);
		Files.write(header, List.of(
				"x-tenant=tenant1",
				FileProcessor.ACTUAL_FILE_NAME + "=data.csv",
				FileProcessor.ACTUAL_FILE_ENCODING + "=csv",
				FileProcessor.LAST_PROPERTY + "=done"), StandardCharsets.UTF_8);

		fileProcessor.processFile(definition, header, processedDir, errorDir);

		assertFalse(Files.exists(header));
		assertFalse(Files.exists(actual));
		assertTrue(Files.exists(processedDir.resolve("data.header")));
		assertTrue(Files.exists(processedDir.resolve("data.csv")));
		ArgumentCaptor<TriggerInput> inputs = ArgumentCaptor.forClass(TriggerInput.class);
		verify(triggerService, times(2)).trigger(inputs.capture());
		TriggerInput first = inputs.getAllValues().get(0);
		TriggerInput second = inputs.getAllValues().get(1);
		assertEquals("foo", first.eventId);
		assertEquals(TriggerSources.FILE, first.source);
		assertEquals(new FooModel("bar", "baz"), first.payload);
		assertEquals("tenant1", first.headers.get("x-tenant"));
		assertEquals("foo", first.headers.get(TriggerHeaders.FILE_WATCH_ID));
		assertEquals("data.csv", first.headers.get(TriggerHeaders.FILE_NAME));
		assertEquals("0", first.headers.get(TriggerHeaders.RECORD_INDEX));
		assertEquals("1", second.headers.get(TriggerHeaders.RECORD_INDEX));
		assertEquals("data.csv", first.triggerContext.get(FileProcessor.ACTUAL_FILE_NAME));
		assertFalse(first.headers.containsKey(FileProcessor.ACTUAL_FILE_NAME));
		assertTrue(first.idempotencyKey.matches("file:foo:data\\.csv:[0-9a-f]{64}:0"), first.idempotencyKey);
		assertNotEquals(first.idempotencyKey, second.idempotencyKey);
	}

	@Test
	void theEventIdCanDifferFromTheFileWatchId() throws Exception {
		definition.setEventId("fooArrived");
		Path header = writeCsv("data.csv", "bar1,baz1");
		fileProcessor.processFile(definition, header, processedDir, errorDir);
		ArgumentCaptor<TriggerInput> input = ArgumentCaptor.forClass(TriggerInput.class);
		verify(triggerService).trigger(input.capture());
		assertEquals("fooArrived", input.getValue().eventId);
	}

	@Test
	void aChangedFileGetsNewKeys() throws Exception {
		fileProcessor.processFile(definition, writeCsv("data.csv", "bar1,baz1"), processedDir, errorDir);
		fileProcessor.processFile(definition, writeCsv("data.csv", "bar2,baz2"), processedDir, errorDir);
		ArgumentCaptor<TriggerInput> inputs = ArgumentCaptor.forClass(TriggerInput.class);
		verify(triggerService, times(2)).trigger(inputs.capture());
		assertNotEquals(inputs.getAllValues().get(0).idempotencyKey, inputs.getAllValues().get(1).idempotencyKey);
	}

	@Test
	void aFailedTriggerMovesTheFilesToTheErrorFolder() throws Exception {
		when(triggerService.trigger(any())).thenThrow(new IllegalStateException("subscriber failed"));
		Path header = writeCsv("data.csv", "bar1,baz1");
		fileProcessor.processFile(definition, header, processedDir, errorDir);
		assertTrue(Files.exists(errorDir.resolve("data.header")));
		assertTrue(Files.exists(errorDir.resolve("data.csv")));
	}

	private Path writeCsv(String name, String row) throws Exception {
		Path actual = watchDir.resolve(name);
		Path header = watchDir.resolve("data.header");
		Files.write(actual, List.of("bar,baz", row), StandardCharsets.UTF_8);
		Files.write(header, List.of(
				FileProcessor.ACTUAL_FILE_NAME + "=" + name,
				FileProcessor.ACTUAL_FILE_ENCODING + "=csv",
				FileProcessor.LAST_PROPERTY + "=done"), StandardCharsets.UTF_8);
		return header;
	}

	@Test
	void missingLastPropertyLeavesFilesInPlace() throws Exception {
		Path actual = watchDir.resolve("data.csv");
		Path header = watchDir.resolve("data.header");
		Files.write(actual, List.of("bar,baz", "bar1,baz1"), StandardCharsets.UTF_8);
		Files.write(header, List.of(
				FileProcessor.ACTUAL_FILE_NAME + "=data.csv",
				FileProcessor.ACTUAL_FILE_ENCODING + "=csv"), StandardCharsets.UTF_8);

		fileProcessor.processFile(definition, header, processedDir, errorDir);

		assertTrue(Files.exists(header));
		assertTrue(Files.exists(actual));
		verify(triggerService, never()).trigger(any());
	}

	@Test
	void pathTraversalActualFileMovesHeaderToErrorFolder() throws Exception {
		Path header = watchDir.resolve("data.header");
		Files.write(header, List.of(
				FileProcessor.ACTUAL_FILE_NAME + "=../outside.csv",
				FileProcessor.ACTUAL_FILE_ENCODING + "=csv",
				FileProcessor.LAST_PROPERTY + "=done"), StandardCharsets.UTF_8);

		fileProcessor.processFile(definition, header, processedDir, errorDir);

		assertFalse(Files.exists(header));
		assertTrue(Files.exists(errorDir.resolve("data.header")));
		verify(triggerService, never()).trigger(any());
	}
}
