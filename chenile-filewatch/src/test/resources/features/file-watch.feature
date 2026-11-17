Feature: A watched file fires one Chenile event per record
  Each record is raised through the TriggerService as the file watch's event (here "foo").

  Scenario: A file fires one event per record with the file headers
    When the CSV file "orders.csv" arrives with rows
      | bar | baz |
      | a   | 1   |
      | b   | 2   |
    Then within 10 seconds the file "orders.csv" is in the processed folder
    And the subscriber received 1 record with bar "a"
    And the subscriber received 1 record with bar "b"
    And the record with bar "a" has header "x-chenile-event-source" equal to "FILE"
    And the record with bar "a" has header "x-chenile-file-watch-id" equal to "foo"
    And the record with bar "a" has header "x-chenile-file-name" equal to "orders.csv"
    And the record with bar "a" has header "x-chenile-record-index" equal to "0"
    And the record with bar "b" has header "x-chenile-record-index" equal to "1"
    And the record with bar "a" has header "x-batch" equal to "nightly"

  Scenario: The same file processed again fires nothing new
    When the CSV file "same.csv" arrives with rows
      | bar | baz |
      | c   | 3   |
    Then within 10 seconds the file "same.csv" is in the processed folder
    When the CSV file "same.csv" arrives with rows
      | bar | baz |
      | c   | 3   |
    Then within 10 seconds the file "same.csv" is in the processed folder
    And the subscriber received 1 record with bar "c"

  Scenario: A changed file with the same name fires again
    When the CSV file "changed.csv" arrives with rows
      | bar | baz |
      | d   | 4   |
    Then within 10 seconds the file "changed.csv" is in the processed folder
    When the CSV file "changed.csv" arrives with rows
      | bar | baz |
      | e   | 5   |
    Then within 10 seconds the file "changed.csv" is in the processed folder
    And the subscriber received 1 record with bar "d"
    And the subscriber received 1 record with bar "e"

  Scenario: A subscriber failure moves the file to the error folder
    When the CSV file "bad.csv" arrives with rows
      | bar  | baz |
      | fail | 6   |
    Then within 10 seconds the file "bad.csv" is in the error folder
    And the subscriber received 0 records with bar "fail"
