@api @api-smoke
Feature: API layer smoke check

  Proves the API layer itself works (real HTTP, status codes, JSON parsing)
  independently of reqres.in's daily rate limit.

  Target: https://jsonplaceholder.typicode.com
  Run:    mvn test -Dcucumber.filter.tags="@api-smoke" -DAPI_BASE_URL=https://jsonplaceholder.typicode.com

  @smoke @positive
  Scenario: Listing posts returns a bare JSON array
    When I GET the "/posts" endpoint
    Then the response status should be 200
    And the response should be a non-empty JSON array
  @smoke @positive
  Scenario: Fetching a single post returns a JSON object
    When I GET the "/posts/1" endpoint
    Then the response status should be 200
    And the response body should contain a "title" field
    And the response body field "id" should equal "1"

  @regression @positive
  Scenario: Creating a post is accepted
    When I POST to the "/posts" endpoint with name "Satvik" and job "Automation Engineer"
    Then the response status should be 201
    And the response body should contain a "id" field

  @regression @negative
  Scenario: An unknown path is not found
    When I GET the "/this-does-not-exist" endpoint
    Then the response status should be 404
