@api
Feature: Authentication API

  As an API consumer
  I want to authenticate and manage resources over HTTP
  So that I can verify the service without a browser

  @smoke @positive
  Scenario: Successful login returns a token
    When I POST a login request with a valid email and password
    Then the response status should be 200
    And the response body should contain a "token" field

  @regression @positive
  Scenario: Login response echoes no password
    When I POST a login request with a valid email and password
    Then the response status should be 200
    And the response body should not contain a "password" field

  @regression @negative
  Scenario: Login without a password is rejected
    When I POST a login request with only an email
    Then the response status should be 400
    And the response body should contain an "error" field

  @smoke @positive
  Scenario: Listing users returns a paginated collection
    When I GET the "/api/users?page=2" endpoint
    Then the response status should be 200
    And the response should contain a non-empty "data" array

  @regression @positive
  Scenario Outline: Fetching a single user returns that user
    When I GET the "/api/users/<id>" endpoint
    Then the response status should be 200
    And the response body field "data.id" should equal "<id>"

    Examples:
      | id |
      | 2  |
      | 3  |

  @regression @negative
  Scenario: Fetching a non-existent user returns 404
    When I GET the "/api/users/9999" endpoint
    Then the response status should be 404
    And the response body should be empty or an empty JSON object

  @regression @positive
  Scenario: Creating a resource returns the created payload
    When I POST a create request with name "Satvik" and job "Automation Engineer"
    Then the response status should be 201
    And the response body field "name" should equal "Satvik"

  @regression @positive
  Scenario: Updating a resource reflects the new values
    When I PUT an update request for user 2 with name "Satvik" and job "QA Lead"
    Then the response status should be 200
    And the response body field "job" should equal "QA Lead"

  @regression @positive
  Scenario: Deleting a resource returns no content
    When I DELETE the "/api/users/2" endpoint
    Then the response status should be 204

  @regression
  Scenario: The API responds with JSON
    When I GET the "/api/users?page=1" endpoint
    Then the response status should be 200
    And the response content type should be "application/json"
