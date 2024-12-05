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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import com.graviteesource.entrypoint.http.get.HttpGetEntrypointConnectorFactory;
import com.graviteesource.entrypoint.http.post.HttpPostEntrypointConnectorFactory;
import com.graviteesource.reactor.message.MessageApiReactorFactory;
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.configuration.GatewayConfigurationBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.fakes.MessageStorage;
import io.gravitee.apim.gateway.tests.sdk.policy.PolicyBuilder;
import io.gravitee.apim.gateway.tests.sdk.reactor.ReactorBuilder;
import io.gravitee.apim.plugin.reactor.ReactorPlugin;
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.reactive.reactor.v4.reactor.ReactorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.policy.PolicyPlugin;
import io.gravitee.policy.status.code.configuration.StatusCodePolicyConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@GatewayTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class StatusCodeMessageApiPolicyIntegrationTest extends AbstractPolicyTest<StatusCodePolicy, StatusCodePolicyConfiguration> {

    private MessageStorage messageStorage;

    @Override
    public void configureReactors(Set<ReactorPlugin<? extends ReactorFactory<?>>> reactors) {
        reactors.add(ReactorBuilder.build(MessageApiReactorFactory.class));
    }

    @Override
    public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
        entrypoints.putIfAbsent("http-get", EntrypointBuilder.build("http-get", HttpGetEntrypointConnectorFactory.class));
        entrypoints.putIfAbsent("http-post", EntrypointBuilder.build("http-post", HttpPostEntrypointConnectorFactory.class));
    }

    @Override
    public void configurePolicies(Map<String, PolicyPlugin> policies) {
        super.configurePolicies(policies);
        policies.put("status-code", PolicyBuilder.build("status-code", StatusCodePolicy.class, StatusCodePolicyConfiguration.class));
    }

    @Override
    protected void configureGateway(GatewayConfigurationBuilder gatewayConfigurationBuilder) {
        super.configureGateway(gatewayConfigurationBuilder);
        gatewayConfigurationBuilder.set("http.requestTimeout", "300");
    }

    @BeforeEach
    void setUp() {
        messageStorage = getBean(MessageStorage.class);
    }

    @AfterEach
    void tearDown() {
        messageStorage.reset();
    }

    @Test
    @DeployApi({ "/apis/message-http-get-status-code-policy-mappings.json" })
    void should_receive_messages_limited_by_message_count_limit(HttpClient httpClient) {
        final int messageCount = 12;

        final TestSubscriber<JsonObject> obs = createGetRequest("/status-code-policy", MediaType.APPLICATION_JSON, httpClient)
            .doOnSuccess(response -> assertThat(response.statusCode()).isEqualTo(201))
            .flatMapPublisher(response -> response.rxBody().flatMapPublisher(this::extractMessages))
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertValueCount(messageCount)
            .assertComplete();

        verifyMessagesAreOrdered(messageCount, obs);
    }

    @Test
    @DeployApi({ "/apis/message-http-post-status-code-policy-mappings.json" })
    void should_receive_messages_limited_by_message_count_limit_post(HttpClient httpClient) {
        JsonObject requestBody = new JsonObject();
        requestBody.put("field", "value");

        postMessage(httpClient, "/test", requestBody, Map.of("X-Test-Header", "header-value")).test().awaitDone(30, TimeUnit.SECONDS);

        messageStorage
            .subject()
            .take(1)
            .test()
            .assertValue(message -> {
                assertThat(message.headers().size()).isEqualTo(0);
                assertThat(new JsonObject(message.content().toString())).isEqualTo(requestBody);
                return true;
            });
    }

    private Flowable<JsonObject> extractMessages(Buffer body) {
        final JsonObject jsonResponse = new JsonObject(body.toString());
        final JsonArray items = jsonResponse.getJsonArray("items");
        final List<JsonObject> messages = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            messages.add(items.getJsonObject(i));
        }

        return Flowable.fromIterable(messages);
    }

    private Flowable<JsonObject> extractPlainTextMessages(Buffer body) {
        final List<JsonObject> messages = new ArrayList<>();

        final String[] lines = body.toString().split("\n");

        JsonObject jsonObject = new JsonObject();

        for (String line : lines) {
            if (line.equals("item")) {
                jsonObject = new JsonObject();
                messages.add(jsonObject);
            } else if (line.startsWith("id:")) {
                jsonObject.put("id", Integer.parseInt(line.replace("id: ", "")));
            } else if (line.startsWith("content:")) {
                jsonObject.put("content", line.replace("content: ", ""));
            }
        }

        return Flowable.fromIterable(messages);
    }

    private void verifyMessagesAreOrdered(int messageCount, TestSubscriber<JsonObject> obs) {
        for (int i = 0; i < messageCount; i++) {
            final int counter = i;
            obs.assertValueAt(
                i,
                jsonObject -> {
                    final Integer messageCounter = Integer.parseInt(jsonObject.getString("id"));
                    assertThat(messageCounter).isEqualTo(counter);
                    assertThat(jsonObject.getString("content")).matches("message");

                    return true;
                }
            );
        }
    }

    private Single<HttpClientResponse> createGetRequest(String path, String accept, HttpClient httpClient) {
        return httpClient
            .rxRequest(HttpMethod.GET, path)
            .flatMap(request -> {
                request.putHeader(HttpHeaderNames.ACCEPT.toString(), accept);
                return request.rxSend();
            });
    }

    private Completable postMessage(HttpClient client, String requestURI, JsonObject requestBody, Map<String, String> headers) {
        return client
            .rxRequest(HttpMethod.POST, requestURI)
            .flatMap(request -> {
                headers.forEach(request::putHeader);
                return request.rxSend(requestBody.toString());
            })
            .flatMapCompletable(response -> {
                assertThat(response.statusCode()).isEqualTo(202);
                return response.rxBody().ignoreElement();
            });
    }
}
