# chenile-others

The event sources for Chenile, plus a few integrations.

# About chenile

Chenile is an open source framework for creating microservices using Java and Spring Boot.
Please check the details out at https://chenile.org

It provides an interception framework to decouple functional and non-functional requirements.
Chenile avoids the need to write repetitive code. It encourages modular coding best practices.

## Everything but HTTP is an event

HTTP calls a Chenile operation directly. Everything else raises an internal **Chenile event**, and every operation
that subscribes to it (`@EventsSubscribedTo("x")`, or `"eventSubscribedTo": ["x"]` in a JSON service definition)
runs through the normal interceptor pipeline. The modules here map external happenings onto Chenile events:

| Module | External event | Chenile event |
|---|---|---|
| `chenile-scheduler` | a cron schedule becomes due | the schedule's `eventId` |
| `chenile-scheduler-db` | the same, for schedules kept in the database | the schedule's `eventId` |
| `chenile-filewatch` | a file lands in a watched folder | the file watch's `eventId` (defaults to its `fileWatchId`), once per record |

Both raise their events through `TriggerService` (module `chenile-trigger` in chenile-core). Its default,
`DirectTriggerService`, raises the event at once; process management's `trigger-service` replaces it with a durable,
retrying implementation. Messages on a topic (chenile-messaging) raise events too. The design is recorded in
[ADR 0001](docs/adr/0001-unified-chenile-events-and-scheduling.md); the user guide is
https://chenile.org/concepts/17-events/ and https://chenile.org/developer/entry-points/.

## chenile-scheduler

One `SchedulerDefinition` model (id, name, cronExpression with seconds, timezone, eventId, payload as JSON text,
x- headers, tenant, enabled, description), held by `ChenileScheduler`:

- **JSON**, one definition per file, found through `chenile.scheduler.json.package`:

  ```json
  {
    "id": "daily-report",
    "cronExpression": "0 0 2 * * ?",
    "eventId": "dailyReport",
    "payload": "{ \"reportType\": \"daily\" }"
  }
  ```
- **Code**: `chenileScheduler.schedule(definition)` and `unschedule(id)`.

Each fire calls `TriggerService` with source `CRON`, the key `schedule:<id>:<scheduled fire time>` and the headers
`x-chenile-schedule-id`, `x-chenile-scheduled-fire-time` and `x-chenile-fire-time`.

Quartz is Spring Boot's (`spring.quartz.*`): in memory by default, clustered with the JDBC job store
(`spring.quartz.job-store-type=jdbc`, `org.quartz.jobStore.isClustered=true`). The Quartz job holds only the
definition id. `db/changelog/chenile-quartz.changelog.xml` (context `chenile-quartz`) creates the `QRTZ_*` tables
on PostgreSQL.

## chenile-scheduler-db

Schedules kept in the database: `SchedulerDefinitionEntity` (table `chenile_schedule`), `POST|GET /schedule` and
`GET|DELETE /schedule/{id}`, scoped to the caller's tenant and scheduled after commit. At startup every stored
schedule is scheduled, and a fire on any node reads the current definition from the database. The host scans
`org.chenile.scheduler.db` for the entity and repository. Liquibase: `db/changelog/chenile-scheduler-db.changelog.xml`,
context `chenile-scheduler-db`.

## chenile-filewatch

Watches folders for completed files (a `.header` file containing `last.property` marks completion) and raises the
definition's event once per record, with source `FILE`, the key
`file:<fileWatchId>:<file name>:<sha256 of the file>:<record index>` and the headers `x-chenile-file-watch-id`,
`x-chenile-file-name` and `x-chenile-record-index`. The header file's `x-` properties become event headers; the
others go into the trigger context.

## Also here

- `chenile-cache`: caching support for Chenile services.
- `chenile-config-maven-plugin`: see its README.

Chenile ships with its own code generator, JGen. See the
[code generation repository](https://github.com/rajakolluru/chenile-gen).
