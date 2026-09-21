@ui @login
Feature: User login

  As a registered user
  I want to sign in with my credentials
  So that I can access the secure area

  Background:
    Given the login page is open

  @smoke @positive
  Scenario: Successful login with valid credentials
    When I login with valid credentials
    Then I should be logged in successfully
    And the success message should be displayed

  @regression @positive
  Scenario: Successful login with explicitly supplied credentials
    When I login with username "tomsmith" and password "SuperSecretPassword!"
    Then I should be logged in successfully
    And the success message should be displayed

  @regression @negative
  Scenario Outline: Login is rejected for invalid credentials
    When I login with username "<username>" and password "<password>"
    Then I should remain on the login page
    And the error message should be displayed

    Examples:
      | username           | password             |
      | tomsmith           | WrongPassword!       |
      | wronguser          | SuperSecretPassword! |
      |                    | SuperSecretPassword! |
      | tomsmith           |                      |
      | wronguser          | WrongPassword!       |

  @regression @negative
  Scenario: Error message describes the failure
    When I login with username "wronguser" and password "WrongPassword!"
    Then the error message should contain "Your username is invalid!"

  @regression
  Scenario: The login page renders its form
    Then the login form should be visible
    And the login page heading should be "Login Page"
