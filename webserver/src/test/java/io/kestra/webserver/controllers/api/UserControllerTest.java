package io.kestra.webserver.controllers.api;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.utils.TestsUtils;
import io.kestra.webserver.KestraTest;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
@KestraTest
class UserControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    SettingRepositoryInterface settingRepository;

    @Test
    void getCurrentUser() {
        // This test works with basic auth in OSS mode
        // For OAuth, authentication is handled by the security filter
        var request = HttpRequest.GET("/api/v1/auth/me");
        
        var response = client.toBlocking().exchange(request, UserController.UserProfile.class);
        
        assertThat(response.getStatus(), is(HttpStatus.OK));
        
        UserController.UserProfile profile = response.body();
        assertThat(profile, notNullValue());
        assertThat(profile.getUsername(), notNullValue());
        assertThat(profile.getRoles(), notNullValue());
    }

    @Test
    void userProfileContainsRoles() {
        var request = HttpRequest.GET("/api/v1/auth/me");
        
        var response = client.toBlocking().exchange(request, UserController.UserProfile.class);
        
        UserController.UserProfile profile = response.body();
        assertThat(profile.getRoles(), is(instanceOf(List.class)));
        // In basic auth mode, roles list may be empty, but should not be null
        assertThat(profile.getRoles(), notNullValue());
    }
}
