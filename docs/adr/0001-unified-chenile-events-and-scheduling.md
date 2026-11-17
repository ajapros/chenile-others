# ADR 0001: one Chenile event model for scheduling, file watch and pub/sub

Status: Accepted (2026-11-17)
Repositories: chenile-core (new chenile-trigger module), chenile-others (chenile-scheduler, chenile-filewatch, new scheduler-db module),
chenile-process-management (trigger-service, crontab-api, crontab-service, process-ui), chenile-gen
(bp-process-management), chenile-samples (p1)

## Context

Chenile has four ways for something outside a request to start work. Three of them already end in a
**Chenile event**; the scheduler is the exception.

| Source | Today | Ends in |
| --- | --- | --- |
| Pub/sub (chenile-messaging) | `chenile.kafka.consumer.topic-bindings` maps a topic to an event id | an event |
| File watch (chenile-filewatch) | `FileProcessor` loops over records and calls `EventProcessor.handleEvent(fileWatchId, record, headers)` | an event |
| Crontabs (crontab-service, chenile-process-management) | `CrontabScheduler.fire` builds a `TriggerInput` and calls `TriggerService.trigger` | an event, through `TriggerService` |
| chenile-scheduler (chenile-others) | `SchedulerInfo` names a **service and operation**; `ScheduledTaskDispatcher` runs it through a launcher, with a `SchedulerExecutionStore` for concurrency, retries and timeouts | a direct operation call |

The two schedulers also overlap:
- crontab-service depends on chenile-scheduler, shares its Quartz `Scheduler`, and supplies a fallback
  `SchedulerExecutionStore`;
- each has its own Quartz job type, startup code and definition model (`SchedulerInfo` versus `Crontab`);
- making Quartz clustered (November 2026) had to be done twice, once per job type, with the same pattern.

Facts that shape the decision:
- **Kubernetes launcher.** The Kubernetes Job launcher exists only in chenile-scheduler's `src/test`
  (`KubernetesJobTaskLauncher`, `Fabric8KubernetesJobClient`). `kubernetes-client` and `testcontainers-k3s` are
  test-scoped. Production code has only the local launcher.
- **Durability lives in process management.** The durable `TriggerService` (`EventTriggerService` in
  trigger-service) accepts a trigger in the outbox (`DISPATCH_TRIGGER`) and depends on `chenile-outbox`, which is in
  chenile-process-management, after chenile-others in the release order.
- **Few users.** chenile-scheduler and process-management scheduling have few users, so backward compatibility is
  not a constraint.

## Decision

### 1. Everything fires a Chenile event; nothing targets an operation directly

- **The rule:** a schedule, a watched file, a pub/sub message or a REST call **fires event X**. Any operation that
  subscribes to X (`@EventsSubscribedTo("X")` or `eventSubscribedTo` in JSON service definitions) runs.
- **Consequences of the rule:**
  - an operation doesn't know or care what fired the event;
  - the same subscriber can be driven by a cron, a file and a topic;
  - routing is configuration, the same as the rest of Chenile.
- **Event payloads** are converted to the subscriber's declared body type the same way for every source.

### 2. All internal sources fire through `TriggerService`

`TriggerService` becomes the single entry point for firing events from inside a Chenile application.

- **Scheduler:** every fire calls `TriggerService.trigger(TriggerInput)`.
- **File watch:** every record calls `TriggerService.trigger(TriggerInput)`, replacing the direct
  `EventProcessor.handleEvent` call.
- **REST:** `POST /trigger` (`TriggerController`) is unchanged.
- **Pub/sub:** stays as it is, calling `EventProcessor` from the consumer. The broker is already the durable,
  retrying layer (Kafka retry plus `.DLT`), and adding the outbox would duplicate it. Pub/sub adopts the standard
  headers (section 6).

**Two implementations, picked automatically.**

| Implementation | Module | Behaviour | When it's used |
| --- | --- | --- | --- |
| `DirectTriggerService` (new) | `chenile-trigger` (chenile-core) | Raises the event synchronously through `EventProcessor`. Deduplicates by `idempotencyKey` within this node (a bounded, time-limited cache). `status()` reports this node's recent triggers. | default, through `@ConditionalOnMissingBean(TriggerService.class)` |
| `EventTriggerService` (existing) | `trigger-service` (chenile-process-management) | Durable acceptance in the outbox, deduplication across nodes, retry and DEAD handling, `DISPATCH_TRIGGER` | whenever trigger-service is on the class path |

**Consequence for several nodes without process management.** `DirectTriggerService` deduplicates within one node
only. An application that runs several nodes without trigger-service must use clustered Quartz
(`spring.quartz.job-store-type=jdbc`), so that each scheduled fire happens on one node. With trigger-service,
deduplication across nodes works either way.

### 3. `SchedulerDefinition`: one schedule model for JSON, the database and code

`SchedulerDefinition` replaces both `SchedulerInfo` (chenile-scheduler) and `Crontab` (crontab-api). It is a plain
Java class with no JPA:

| Field | Meaning |
| --- | --- |
| `id` | stable identity; the Quartz job and trigger key |
| `name` | unique per tenant; for people and the console |
| `cronExpression` | Quartz syntax, with seconds |
| `timezone` | defaults to `UTC` |
| `eventId` | the event to fire |
| `payload` | JSON for the event body |
| `headers` | extra `x-` headers |
| `tenant` | the tenant the event fires as |
| `enabled` | disabled definitions are kept but not scheduled |
| `description` | free text |

Definitions come from three places, all producing the same model and all held by one class, `ChenileScheduler`
(decided during phase 2: no source interface, registry or separate definition service):

| From | Where | How |
| --- | --- | --- |
| JSON | chenile-scheduler | resources matched by `chenile.scheduler.json.package`, one definition per file, loaded by `ChenileScheduler` at startup. Replaces `ChenileSchedulerInitializer`. |
| Code | chenile-scheduler | `chenileScheduler.schedule(definition)`; `unschedule(id)` removes it |
| Database | `chenile-scheduler-db` (new, chenile-others) | a JPA entity `SchedulerDefinitionEntity extends BaseJpaEntity`, its repository, and the REST API (today's crontab controller, renamed). It saves rows and calls `chenileScheduler.schedule` after commit, and loads all rows at startup. Includes tenant checks, enable/disable, and validation. |

**Rules.**
- **Ids must be unique.** Two JSON files with the same id fail startup; the database module refuses an id that is
  already scheduled from JSON.
- **Changes are scheduled straight away:** a database change is scheduled after the transaction commits, as today.
- **Code definitions in a cluster:** they exist only on the node that created them. Code that schedules must run on
  every node, for example at startup. A fire for a definition this node doesn't know is logged and skipped.

### 4. One engine: chenile-scheduler

- **The job:** one Quartz job type, `SchedulerJob`. Its job data holds only the definition id; it looks up this
  node's `ChenileScheduler` in the Quartz scheduler context.
- **Firing:** `ChenileScheduler` reads the definition and builds the `TriggerInput`:
  - event id, payload, headers and tenant from the definition;
  - source `CRON`;
  - idempotency key `schedule:<id>:<scheduledFireTime>`.

  It then calls `TriggerService`.
- **Startup:** enabled definitions are scheduled with replace-if-exists, safe when nodes start together.
- **Clustering:** as built in November 2026. The scheduler is Spring Boot's (`spring.quartz.*`), in memory by
  default and clustered by configuration. Quartz's `QRTZ_*` tables come from the `chenile-quartz` Liquibase
  changelog.

**Removed**, as they no longer apply under events:
- `ScheduledTaskLauncher`, `LocalChenileTaskLauncher`, `ScheduledTaskDispatcher` and `LaunchResult`;
- `SchedulerExecutionStore` with its JDBC implementation, schema and service. Concurrency policy, retries and
  timeouts belong to subscribers or to the trigger layer (section 7).
- `SchedulerInfo`, `ChenileSchedulerInitializer`, `ScheduledJob` and `SchedulerBuilder`;
- the Kubernetes test support, together with its test dependencies (`kubernetes-client`, `testcontainers-k3s`).

### 5. File watch fires through `TriggerService`

- **Event id:** `FileWatchDefinition` gets an `eventId`, which defaults to `fileWatchId`. Subscribers subscribe to
  that event normally. `FileWatchSubscribersInitializer`, which wired subscribers to a file-watch definition, is
  removed.
- **One trigger per record.** `FileProcessor` calls `TriggerService.trigger` for each record:
  - payload: the record;
  - source: `FILE`;
  - idempotency key: `file:<fileWatchId>:<file name>:<sha256 of the file>:<record index>`. A file reprocessed after
    a crash then doesn't fire its records twice, while a changed file with the same name is new.
- **What "processed" means changes:**
  - **today:** a subscriber failure moves the file to the error folder;
  - **with trigger-service:** a file is processed once all its records are **accepted**. A subscriber failure after
    acceptance is retried, and then marked DEAD, by the trigger layer;
  - **with `DirectTriggerService`:** subscribers run synchronously, so a failure still moves the file to the error
    folder, as today.

### 6. Standard event headers, whatever fired the event

| Header | Set by | Value |
| --- | --- | --- |
| `x-chenile-event-source` | all sources | `CRON`, `FILE`, `PUBSUB` or `API` |
| `x-chenile-trigger-id` | `TriggerService` | the accepted trigger's id |
| `x-chenile-tenant-id` | all sources | the tenant |
| `x-chenile-schedule-id`, `x-chenile-scheduled-fire-time`, `x-chenile-fire-time` | scheduler | the definition, and Quartz's fire times |
| `x-chenile-file-watch-id`, `x-chenile-file-name`, `x-chenile-record-index` | file watch | the file and record |
| `x-chenile-topic` | pub/sub | the topic |

Today's crontab-specific headers (`x-chenile-crontab-id`, `x-chenile-quartz-*`) are replaced by the scheduler
headers above.

### 7. What subscribers take on

- **"Don't overlap with the previous run."** Only the subscriber knows when its work ends, so this belongs there.
  A small helper, `EventLocks` (a lock per event and tenant, in memory or in the database), can
  provide it where needed.
- **Retries and timeouts:** handled by the trigger layer with trigger-service (outbox retry, then DEAD), or by the
  subscriber.
- **Running work elsewhere** (a Kubernetes Job, a batch system) is a subscriber's choice. A future optional module
  can offer an operation that launches a Kubernetes Job when its event fires. It isn't part of this ADR.

### 8. Modules after the change

| Module | Repository | Contents |
| --- | --- | --- |
| `chenile-trigger` (new) | chenile-core | `TriggerService`, `TriggerInput`, `TriggerResult`, `TriggerStatus` and `Trigger` (moved from trigger-api, packages `org.chenile.trigger.*` kept), plus `DirectTriggerService` |
| `chenile-scheduler` | chenile-others | `SchedulerDefinition`, `ChenileScheduler`, `SchedulerJob` and the `chenile-quartz` changelog |
| `chenile-scheduler-db` (new) | chenile-others | the entity, repository and REST API, a Liquibase changelog for the definition table, and Cucumber tests |
| `chenile-filewatch` | chenile-others | fires through `TriggerService` |
| `trigger-service` | chenile-process-management | `EventTriggerService` and `DispatchTriggerCommand` on the outbox; depends on `chenile-trigger` |
| `trigger-api`, `crontab-api`, `crontab-service` | chenile-process-management | **removed** |

Maven coordinates change for anything that moves. The Java packages `org.chenile.trigger` stay, and
`org.chenile.trigger.cron` becomes `org.chenile.scheduler`.

**Effects elsewhere:**
- **process-admin-server and process-ui:** use chenile-scheduler and chenile-scheduler-db. The console's crontab
  screens become "Schedules", against the renamed REST API.
- **bp-process-management and p1:**
  - generated `cron-triggers.json` becomes a scheduler definition file;
  - the bootstrap seeds `SchedulerDefinitionEntity` rows;
  - the `clustered` profile and its README section stay.

## Consequences

- **Gains:**
  - one model for starting work from outside a request: event, subscription, `TriggerService`;
  - one schedule model, one Quartz integration, one clustering story;
  - database-managed schedules for every Chenile application, not just process management;
  - file watch gains deduplication and, with trigger-service, durability.
- **Breaking changes:** coordinates, class names, headers and the REST path change. Users are few and are moved in
  the same releases (process management, the blueprint and p1).
- **Behaviour changes:**
  - scheduled jobs and file records now reach operations only through event subscriptions;
  - a JSON schedule that named an operation must name an event, and the operation must subscribe to it;
  - with trigger-service, file processing becomes "accepted", not "completed".
- **Risk:** firing everything through `TriggerService` makes it a hot path. `DirectTriggerService` is synchronous
  and cheap, and the outbox implementation already batches.

## Alternatives considered

- **Keep both schedulers and only share the Quartz code.** Rejected: two models and two reliability layers remain.
- **Allow both an event and an operation as targets.** Rejected: it keeps a second path, and with it launchers and
  the execution store.
- **Move `trigger-service` and `chenile-outbox` down to chenile-others.** Rejected: too large, and durability belongs
  with process management. The interface moves down; the durable implementation stays.
- **Route pub/sub through `TriggerService` as well.** Deferred: the broker already provides durability and retries.

## Phased plan

| Phase | Repository | Work | Acceptance |
| --- | --- | --- | --- |
| 1. Trigger API | chenile-core | Create `chenile-trigger`: move the trigger model and interface, add `DirectTriggerService`, the deduplication cache and the standard header constants. | Cucumber: a trigger fired from code raises the event; a duplicate key is ignored; status reported; headers set. |
| 2. Scheduler engine | chenile-others | `SchedulerDefinition`, `ChenileScheduler` (JSON and code definitions) and `SchedulerJob` firing through `TriggerService`. Remove launchers, the execution store, `SchedulerInfo` and the Kubernetes test support. | Cucumber: a JSON schedule fires event X and a subscriber runs; code schedule; disabled schedule; duplicate id across sources fails at startup; replace-if-exists on restart; job data holds only the id. |
| 3. Database schedules | chenile-others | `chenile-scheduler-db`: the entity, repository, REST API and Liquibase changelog, ported from crontab-service with tenant checks and validation. | crontab-service's Cucumber feature ported (save, list, disable, delete, tenant isolation, invalid input). |
| 4. File watch | chenile-others | `eventId` on `FileWatchDefinition`; records fired through `TriggerService` with the file-based idempotency key; remove `FileWatchSubscribersInitializer`. | Cucumber: a file fires one event per record; reprocessing the same file fires nothing new; a changed file fires again; subscriber failure handling with `DirectTriggerService`. |
| 5. Process management | chenile-process-management | `trigger-service` depends on `chenile-trigger`; remove `trigger-api`, `crontab-api` and `crontab-service`; process-admin-server uses the scheduler modules; the console's Schedules screens. | The existing trigger and outbox tests; admin-server tests; console tests. |
| 6. Generator and sample | chenile-gen, chenile-samples | bp-process-management generates scheduler definitions and seeds `SchedulerDefinitionEntity`; regenerate p1. | ProcessManagementBlueprintTest; p1 `mvn install`; a two-node clustered p1 run fires each schedule once. |
| 7. Docs | all | chenile.org pages for the event model, scheduling and file watch; process-management docs; the chenile-framework-knowledge and chenile-process-management skills. | – |

Phase 1 is in chenile-core and phases 2–4 in chenile-others; both are released before chenile-process-management, so
they ship first. `chenile-trigger` lives in chenile-core (moved there from chenile-others after phase 1) because it
depends only on chenile-core, and chenile-messaging, which is released before chenile-others, needs it for the
pub/sub source and its headers.
Phases 5 and 6 follow in the same release.

### Progress

- **Phase 1 (done, 2026-11-17).** `chenile-core/chenile-trigger` holds `TriggerService`, the model, `TriggerHeaders`,
  `TriggerSources`, `TriggerRequests` (validation and header rules) and `DirectTriggerService`, which holds its own
  bounded cache and the raise-and-check logic. `DirectTriggerAutoConfiguration` registers it when no other
  `TriggerService` exists. Part of phase 5 was pulled forward to avoid two copies of the same classes:
  process-management's `trigger-api` is now an empty module that depends on `chenile-trigger`, and `EventTriggerService`
  reuses `TriggerRequests`. Durable triggers now also carry
  `x-chenile-event-source`.
- **Phase 2 (done, 2026-11-17).** chenile-scheduler now has three classes: `SchedulerDefinition`, `ChenileScheduler`
  (holds the definitions, loads JSON at startup, `schedule`/`unschedule`, fires through `TriggerService`) and
  `SchedulerJob`. Launchers, the execution store and its schema, `SchedulerInfo`, the initializer, `SchedulerBuilder`,
  `ScheduledJob`, the Kubernetes test support and the old tests are removed. `EventLocks` is **deferred** until a
  subscriber needs it. crontab-service's stub `SchedulerExecutionStore` bean was dropped so process management still
  builds until phase 5. `chenile-samples/scheduler-sample` is built on the removed model and is rewritten in phase 6.
- **Phase 3 (done, 2026-11-17).** New module `chenile-scheduler-db`:
  - `SchedulerDefinitionEntity` (table `chenile_schedule`, headers in `chenile_schedule_header`, name unique per
    tenant), `SchedulerDefinitionRepository`, `ScheduleService` and `ScheduleController` (`POST|GET /schedule`,
    `GET|DELETE /schedule/{id}`), wired by `ChenileSchedulerDbConfiguration`; the host scans the entity and
    repository. Liquibase: `db/changelog/chenile-scheduler-db.changelog.xml`, context `chenile-scheduler-db`.
  - `ScheduleService` validates with `ChenileScheduler.validate` plus a required name, enforces the caller's tenant,
    refuses an id already scheduled from JSON or code, and schedules after commit. At startup it schedules every
    stored row (an id also defined in JSON fails startup).
  - **Added to `ChenileScheduler`:** `lookUpDefinitionsWith(Function)`. A fire reads the database first, so a schedule
    saved on one node fires with its current definition on any node of a clustered Quartz. A disabled definition is
    skipped at fire time. `validate` is now public.
  - Error codes E573 (not found in this tenant) and E574 (tenant mismatch) join E570–E572 in chenile-scheduler.
  - Tests: crontab-service's Cucumber feature ported (save, list, change cron, disable, delete, invalid input, tenant
    isolation), plus JUnit for the startup load and the fire-time lookup.

## Open questions

1. **Deduplication cache in `DirectTriggerService`:** resolved in phase 1 as 24 hours and 100,000 keys, set with
   `chenile.trigger.dedupe.retention` (ISO-8601, default `PT24H`) and `chenile.trigger.dedupe.max-entries`.
2. **`EventLocks` (deferred) in the database:** should the database variant reuse the outbox's claim tables when trigger-service
   is present, or have its own table? Proposed: its own small table in chenile-scheduler-db.
