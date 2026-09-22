@api
Feature: REST API behaviour
  As an API consumer
  I want to exercise real HTTP endpoints
  So that I can verify the service without a browser
  Default target: https://jsonplaceholder.typicode.com (no key, no quota)
  To point this at another service:
    mvn test -DAPI_BASE_URL=https://my-api.example.com -Dcucumber.filter.tags="@api"

  # ---------------------------------------------------------------- GET list
  @smoke @positive
  Scenario: Listing resources returns a collection
    When I GET the "/posts" endpoint
    Then the response status should be 200
    # jsonplaceholder returns a bare array at the root rather than {"data":[...]}
    And the response should be a non-empty JSON array
  @regression @positive
  Scenario: Listing resources filtered by an owner
    When I GET the "/posts?userId=1" endpoint
    Then the response status should be 200
    And the response should be a non-empty JSON array
  # ----------------------------------------------------------------- GET one
  @smoke @positive
  Scenario: Fetching a known resource returns it
    When I GET the "/posts/1" endpoint
    Then the response status should be 200
    And the response body should contain a "title" field
    And the response body field "id" should equal "1"

  @regression @positive
  Scenario Outline: Fetching resources by id returns the matching record
    When I GET the "/posts/<id>" endpoint
    Then the response status should be 200
    And the response body field "id" should equal "<id>"

    Examples:
      | id |
      | 1  |
      | 5  |
      | 20 |

  @regression @positive
  Scenario: Fetching a user returns their email
    When I GET the "/users/1" endpoint
    Then the response status should be 200
    And the response body should contain an "email" field
  # ----------------------------------------------------------------- writing
  @regression @positive
  Scenario: Creating a resource is accepted
    When I POST to the "/posts" endpoint with name "Satvik" and job "Automation Engineer"
    Then the response status should be 201
    And the response body should contain an "id" field

  @regression @positive
  Scenario: Updating a resource reflects the new values
    When I PUT to the "/posts/1" endpoint with name "Satvik" and job "QA Lead"
    Then the response status should be 200
    And the response body field "job" should equal "QA Lead"

  @regression @positive
  Scenario: Patching a resource reflects the new value
    When I PATCH the "/posts/1" endpoint with name "Satvik"
    Then the response status should be 200
    And the response body field "name" should equal "Satvik"

  @regression @positive
  Scenario: Deleting a resource is accepted
    When I DELETE the "/posts/1" endpoint
    Then the response status should be 200
  # ------------------------------------------------------------------ errors
  @regression @negative
  Scenario: An unknown path is not found
    When I GET the "/this-does-not-exist" endpoint
    Then the response status should be 404
    And the response body should be empty
  # ----------------------------------------------------------------- headers
  @regression
  Scenario: The API responds with JSON
    When I GET the "/posts/1" endpoint
    Then the response status should be 200
    And the response content type should be "application/json"
