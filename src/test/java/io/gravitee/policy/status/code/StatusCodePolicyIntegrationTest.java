/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.status.code;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import io.gravitee.policy.status.code.configuration.StatusCodePolicyConfiguration;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@GatewayTest
@DeployApi({ "/apis/status-code-policy-mappings.json" })
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class StatusCodePolicyIntegrationTest extends AbstractPolicyTest<StatusCodePolicy, StatusCodePolicyConfiguration> {

    @Override
    public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
        entrypoints.putIfAbsent("http-proxy", EntrypointBuilder.build("http-proxy", HttpProxyEntrypointConnectorFactory.class));
    }

    @Override
    public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
        endpoints.putIfAbsent("http-proxy", EndpointBuilder.build("http-proxy", HttpProxyEndpointConnectorFactory.class));
    }

    @Test
    void should_transform_status_code_201_to_200_for_get_request(HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(201).withBody("Response body")));
        int expectedStatusCode = 200;

        client
            .rxRequest(HttpMethod.GET, "/status-code-policy")
            .flatMap(HttpClientRequest::send)
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(expectedStatusCode);
                return response.body();
            })
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> true);
    }

    @Test
    void should_transform_status_code_201_to_200_for_post_request(HttpClient client) {
        wiremock.stubFor(post("/endpoint").willReturn(aResponse().withStatus(201).withBody("Response body")));
        int expectedStatusCode = 200;

        client
            .rxRequest(HttpMethod.POST, "/status-code-policy")
            .flatMap(httpClientRequest -> httpClientRequest.send("some-key"))
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(expectedStatusCode);
                return response.body();
            })
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> true);
    }

    @Test
    void should_transform_status_code_419_to_401(HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(419).withBody("Custom error")));
        int expectedStatusCode = 401;

        client
            .rxRequest(HttpMethod.GET, "/status-code-policy")
            .flatMap(HttpClientRequest::send)
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(expectedStatusCode);
                return response.body();
            })
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> true);
    }

    @Test
    void should_transform_status_code_404_to_200(HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(404).withBody("Custom error")));
        int expectedStatusCode = 200;

        client
            .rxRequest(HttpMethod.GET, "/status-code-policy")
            .flatMap(HttpClientRequest::send)
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(expectedStatusCode);
                return response.body();
            })
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> true);
    }

    @Test
    void should_transform_status_code_515_to_505(HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(515).withBody("Custom error")));
        int expectedStatusCode = 505;

        client
            .rxRequest(HttpMethod.GET, "/status-code-policy")
            .flatMap(HttpClientRequest::send)
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(expectedStatusCode);
                return response.body();
            })
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> true);
    }

    @Test
    void should_not_transform_status_code_when_no_mapping(HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(aResponse().withStatus(620).withBody("Body")));
        int expectedStatusCode = 620;

        client
            .rxRequest(HttpMethod.GET, "/status-code-policy")
            .flatMap(HttpClientRequest::send)
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(expectedStatusCode);
                return response.body();
            })
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> true);
    }
}
