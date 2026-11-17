Feature: Fire Chenile events on a schedule
  A schedule fires an event; every operation that subscribes to the event runs.

  Scenario: A JSON schedule fires its event with the standard headers
    Then within 5 seconds the listener receives a tick with note "from-json"
    And that tick has header "x-chenile-event-source" equal to "CRON"
    And that tick has header "x-chenile-schedule-id" equal to "ticker"
    And that tick has header "x-chenile-tenant-id" equal to "t1"
    And that tick has header "x-custom" equal to "c"
    And that tick has headers "x-chenile-scheduled-fire-time,x-chenile-fire-time,x-chenile-trigger-id"

  Scenario: A disabled schedule is kept but not scheduled
    Then the scheduler knows schedule "paused"
    And Quartz has no job for schedule "paused"

  Scenario: A schedule added in code fires
    When code schedules "coded" with cron "* * * * * ?" and note "from-code"
    Then within 5 seconds the listener receives a tick with note "from-code"
    And Quartz stores only the schedule id in the job data of "coded"

  Scenario: Scheduling the same id again replaces the schedule
    When code schedules "nightly" with cron "0 0 2 * * ?" and note "night"
    And code schedules "nightly" with cron "0 0 3 * * ?" and note "night"
    Then Quartz has one trigger for schedule "nightly" with cron "0 0 3 * * ?"

  Scenario: Disabling a schedule removes it from Quartz
    When code schedules "coded" with cron "0 0 2 * * ?" and note "from-code"
    And code disables schedule "coded"
    Then Quartz has no job for schedule "coded"
    And the scheduler knows schedule "coded"

  Scenario: An invalid schedule is refused
    When code schedules "bad" with cron "not a cron" and note "never"
    Then the schedule is refused with "invalid cron expression"
