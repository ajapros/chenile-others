package org.chenile.filewatch.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.chenile.filewatch.handler.FileProcessor;
import org.chenile.filewatch.test.FileWatchTestApplication;
import org.chenile.filewatch.test.service.FooService;
import org.chenile.filewatch.test.service.FooService.Delivery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(classes = FileWatchTestApplication.class)
@ActiveProfiles("unittest")
public class FileWatchSteps {
	@Autowired FooService fooService;
	@Autowired FileSystem fileSystem;

	@Before
	public void reset() {
		fooService.deliveries.clear();
	}

	@When("the CSV file {string} arrives with rows")
	public void arrives(String name, DataTable rows) throws Exception {
		Path source = fileSystem.getPath(FileWatchTestApplication.SRC_DIR);
		Files.deleteIfExists(fileSystem.getPath(FileWatchTestApplication.DEST_DIR, name));
		Files.deleteIfExists(fileSystem.getPath(FileWatchTestApplication.ERROR_DIR, name));
		List<String> lines = new ArrayList<>();
		for (List<String> row : rows.asLists()) lines.add(String.join(",", row));
		Files.write(source.resolve(name), lines, StandardCharsets.UTF_8);
		// The header file is written last: its arrival tells the watcher the records file is complete.
		Files.write(source.resolve(name + ".header"), List.of(
				"x-batch=nightly",
				FileProcessor.ACTUAL_FILE_NAME + "=" + name,
				FileProcessor.ACTUAL_FILE_ENCODING + "=csv",
				FileProcessor.LAST_PROPERTY + "=done"), StandardCharsets.UTF_8);
	}

	@Then("within {int} seconds the file {string} is in the processed folder")
	public void processed(int seconds, String name) throws Exception {
		waitUntilMoved(seconds, name, FileWatchTestApplication.DEST_DIR);
	}

	@Then("within {int} seconds the file {string} is in the error folder")
	public void failed(int seconds, String name) throws Exception {
		waitUntilMoved(seconds, name, FileWatchTestApplication.ERROR_DIR);
	}

	@Then("the subscriber received {int} record(s) with bar {string}")
	public void received(int count, String bar) {
		assertEquals(count, deliveriesWithBar(bar).size());
	}

	@Then("the record with bar {string} has header {string} equal to {string}")
	public void header(String bar, String header, String value) {
		List<Delivery> deliveries = deliveriesWithBar(bar);
		assertEquals(1, deliveries.size());
		assertEquals(value, deliveries.get(0).headers().get(header));
	}

	private void waitUntilMoved(int seconds, String name, String folder) throws Exception {
		Path source = fileSystem.getPath(FileWatchTestApplication.SRC_DIR, name);
		Path target = fileSystem.getPath(folder, name);
		long deadline = System.currentTimeMillis() + seconds * 1000L;
		while ((Files.exists(source) || !Files.exists(target)) && System.currentTimeMillis() < deadline)
			Thread.sleep(50);
		assertTrue(Files.exists(target) && !Files.exists(source), name + " was not moved to " + folder);
	}

	private List<Delivery> deliveriesWithBar(String bar) {
		return fooService.deliveries.stream().filter(d -> bar.equals(d.record().getBar())).toList();
	}
}
