package org.chenile.filewatch.test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import org.chenile.filewatch.test.service.FooService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

/** Watches an in-memory file system (the "unittest" profile replaces the default one). */
@SpringBootApplication(scanBasePackages = "org.chenile.configuration")
@PropertySource("classpath:org/chenile/filewatch/test/TestFileWatch.properties")
public class FileWatchTestApplication {
	public static final String SRC_DIR = "/tmp/src/foo";
	public static final String DEST_DIR = "/tmp/dest/foo";
	public static final String ERROR_DIR = "/tmp/error/foo";

	@Bean
	public FooService fooService() {
		return new FooService();
	}

	@Bean
	FileSystem fileWatcherFileSystem() throws Exception {
		FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
		Files.createDirectories(fileSystem.getPath(SRC_DIR));
		Files.createDirectories(fileSystem.getPath(DEST_DIR));
		Files.createDirectories(fileSystem.getPath(ERROR_DIR));
		return fileSystem;
	}
}
