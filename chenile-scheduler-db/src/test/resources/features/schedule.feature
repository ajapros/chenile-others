Feature: Manage database schedules through the Chenile service controller

  Background:
    Given I construct a REST request with header "x-chenile-tenant-id" and value "alpha"
    And I construct a REST request with header "x-chenile-auth-user" and value "scheduler-admin"

  Scenario: Save, retrieve, list, change, disable and delete a schedule
    When I POST a REST request to URL "/schedule" with payload
      """
      {"name":"nightly","cronExpression":"0 0 0 1 1 ? 2099","eventId":"customerEvent",
       "payload":"{\"note\":\"batch\"}","headers":{"x-request-id":"batch-1"}}
      """
    Then success is true
    And the http status code is 200
    And store "$.payload.id" from response to "id"
    And the REST response key "tenant" is "alpha"
    And the REST response key "createdBy" is "scheduler-admin"
    And the REST response key "timezone" is "UTC"
    And Quartz has scheduled the saved schedule with cron "0 0 0 1 1 ? 2099"
    And Quartz stores only the schedule id in the job data
    And the scheduler fires the saved schedule as tenant "alpha"
    When I GET a REST request to URL "/schedule/${id}"
    Then success is true
    And the REST response key "id" is "${id}"
    When I GET a REST request to URL "/schedule"
    Then the REST expression "payload" size is 1
    When I POST a REST request to URL "/schedule" with payload
      """
      {"id":"${id}","name":"nightly","cronExpression":"0 0 1 1 1 ? 2099","eventId":"customerEvent"}
      """
    Then success is true
    And store "$.payload.version" from response to "version"
    And Quartz has scheduled the saved schedule with cron "0 0 1 1 1 ? 2099"
    When I POST a REST request to URL "/schedule" with payload
      """
      {"id":"${id}","version":${version},"name":"nightly","cronExpression":"0 0 1 1 1 ? 2099","eventId":"customerEvent","enabled":false}
      """
    Then success is true
    And the value of "$.payload.enabled" is "false"
    And Quartz has removed the saved schedule
    When I DELETE a REST request to URL "/schedule/${id}" with payload
      """
      {}
      """
    Then success is true
    And the scheduler has forgotten the saved schedule
    When I GET a REST request to URL "/schedule/${id}"
    Then the http status code is 404
    And success is false
    When I GET a REST request to URL "/schedule"
    Then the REST response is empty

  Scenario Outline: Invalid schedules return Chenile errors and are never stored
    When I POST a REST request to URL "/schedule" with payload
      """
      {"name":"<name>","cronExpression":"<expression>","timezone":"<timezone>","eventId":"<event>","headers":<headers>}
      """
    Then the http status code is 400
    And success is false
    When I GET a REST request to URL "/schedule"
    Then the REST response is empty
    And Quartz has no schedules
    Examples:
      | name    | expression       | timezone  | event         | headers              |
      | invalid | invalid          | UTC       | customerEvent | {}                   |
      | invalid | 0 0 0 1 1 ? 2099 | Mars/Base | customerEvent | {}                   |
      | invalid | 0 0 0 1 1 ? 2099 | UTC       | customerEvent | {"custom":"value"}   |
      | invalid | 0 0 0 1 1 ? 2099 | UTC       |               | {}                   |
      |         | 0 0 0 1 1 ? 2099 | UTC       | customerEvent | {}                   |

  Scenario: Other tenants cannot read, overwrite or delete a schedule
    When I POST a REST request to URL "/schedule" with payload
      """
      {"name":"private","cronExpression":"0 0 0 1 1 ? 2099","eventId":"customerEvent"}
      """
    Then success is true
    And store "$.payload.id" from response to "id"
    When I construct a REST request with header "x-chenile-tenant-id" and value "beta"
    And I GET a REST request to URL "/schedule"
    Then the REST response is empty
    When I GET a REST request to URL "/schedule/${id}"
    Then the http status code is 404
    When I POST a REST request to URL "/schedule" with payload
      """
      {"id":"${id}","name":"private","cronExpression":"0 0 0 1 1 ? 2099","eventId":"customerEvent"}
      """
    Then the http status code is 404
    When I DELETE a REST request to URL "/schedule/${id}" with payload
      """
      {}
      """
    Then the http status code is 404
    When I POST a REST request to URL "/schedule" with payload
      """
      {"name":"spoofed","tenant":"alpha","cronExpression":"0 0 0 1 1 ? 2099","eventId":"customerEvent"}
      """
    Then the http status code is 400
    When I construct a REST request with header "x-chenile-tenant-id" and value "alpha"
    And I GET a REST request to URL "/schedule/${id}"
    Then success is true
    And the REST response key "tenant" is "alpha"
