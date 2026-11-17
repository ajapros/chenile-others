package org.chenile.filewatch.test.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Subscribes to the file watch's event and records each record it receives. */
public class FooService {
	public record Delivery(FooModel record, Map<String, Object> headers) {}

	public final List<Delivery> deliveries = new CopyOnWriteArrayList<>();

	public void saveFoo(FooModel foo, Map<String, Object> headers) {
		if ("fail".equals(foo.getBar())) throw new IllegalStateException("Cannot save " + foo);
		deliveries.add(new Delivery(foo, new HashMap<>(headers)));
	}
}
