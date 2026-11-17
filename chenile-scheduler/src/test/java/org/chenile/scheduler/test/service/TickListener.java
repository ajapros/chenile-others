package org.chenile.scheduler.test.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Subscribes to the tick event and records every delivery. */
public class TickListener {
  public record Delivery(String note, Map<String, Object> headers) {}

  public final List<Delivery> deliveries = new CopyOnWriteArrayList<>();

  public void tick(Tick tick, Map<String, Object> headers) {
    deliveries.add(new Delivery(tick.note, new HashMap<>(headers)));
  }
}
