package com.satviklabs.stepDefinitions;

import com.satviklabs.hooks.Hooks;
import com.satviklabs.pages.LoginPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI step definitions for login.
 *
 * These stay declarative and completely locator-free: every selector lives in
 * {@link LoginPage}.
 */
public class LoginSteps {

    private static final Logger log = LoggerFactory.getLogger(LoginSteps.class);

    private final Hooks hooks;
    private LoginPage loginPage;

    public LoginSteps(Hooks hooks) {
        this.hooks = hooks;
    }

    private LoginPage page() {
        if (loginPage == null) {
            loginPage = hooks.loginPage();
        }
        return loginPage;
    }

    // ---------------------------------------------------------------- Given

    @Given("the login page is open")
    public void theLoginPageIsOpen() {
        loginPage = page().open();
        Assertions.assertThat(loginPage.isLoginFormVisible())
                .as("Login form should render with username, password and submit button")
                .isTrue();
        log.info("Login page opened at {}", loginPage.currentUrl());
    }

    // ----------------------------------------------------------------- When

    @When("I login with valid credentials")
    public void iLoginWithValidCredentials() {
        page().loginWithValidCredentials();
    }

    @When("I login with username {string} and password {string}")
    public void iLoginWithUsernameAndPassword(String username, String password) {
        page().login(username, password);
    }

    // ----------------------------------------------------------------- Then

    @Then("I should be logged in successfully")
    public void iShouldBeLoggedInSuccessfully() {
        Assertions.assertThat(page().isLoggedIn())
                .as("A logout link should be present once authenticated")
                .isTrue();
        Assertions.assertThat(page().currentUrl())
                .as("Successful login should redirect to the secure area")
                .contains("/secure");
    }

    @Then("I should remain on the login page")
    public void iShouldRemainOnTheLoginPage() {
        Assertions.assertThat(page().currentUrl())
                .as("Failed login should leave the user on /login")
                .contains("/login");
        Assertions.assertThat(page().isLoggedIn())
                .as("A failed login must not authenticate the user")
                .isFalse();
    }

    @Then("the success message should be displayed")
    public void theSuccessMessageShouldBeDisplayed() {
        String flash = page().flashMessageText();
        log.info("Flash message: {}", flash);
        Assertions.assertThat(flash)
                .as("A success flash message should be shown")
                .isNotEmpty()
                .contains("You logged into a secure area!");
    }

    @Then("the error message should be displayed")
    public void theErrorMessageShouldBeDisplayed() {
        String flash = page().flashMessageText();
        log.info("Flash message: {}", flash);
        Assertions.assertThat(flash)
                .as("An error flash message should be shown")
                .isNotEmpty();
    }

    @Then("the error message should contain {string}")
    public void theErrorMessageShouldContain(String expected) {
        Assertions.assertThat(page().flashMessageText())
                .as("Error message should explain the failure")
                .contains(expected);
    }

    @Then("the login form should be visible")
    public void theLoginFormShouldBeVisible() {
        Assertions.assertThat(page().isLoginFormVisible()).isTrue();
    }

    @Then("the login page heading should be {string}")
    public void theLoginPageHeadingShouldBe(String expected) {
        Assertions.assertThat(page().headingText()).isEqualTo(expected);
    }
}
