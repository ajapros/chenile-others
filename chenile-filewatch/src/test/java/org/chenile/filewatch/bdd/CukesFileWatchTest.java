package org.chenile.filewatch.bdd;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
		features = "src/test/resources/features",
		glue = "org.chenile.filewatch.bdd",
		plugin = "pretty")
public class CukesFileWatchTest {}
