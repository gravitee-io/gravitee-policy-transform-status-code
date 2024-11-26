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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.context.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.MessageExecutionContext;
import io.gravitee.gateway.reactive.api.context.Request;
import io.gravitee.gateway.reactive.api.context.Response;
import io.gravitee.policy.status.code.configuration.StatusCodePolicyConfiguration;
import io.gravitee.policy.status.code.configuration.StatusMapping;
import io.reactivex.rxjava3.core.Completable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class StatusCodePolicyTest {

    private StatusCodePolicy policy;
    private StatusCodePolicyConfiguration configuration;

    @Mock
    private HttpExecutionContext ctx;

    @Mock
    private Request request;

    @Mock
    private Response response;

    @BeforeEach
    void setUp() {
        configuration = new StatusCodePolicyConfiguration();
        policy = new StatusCodePolicy(configuration);
    }

    @Test
    void shouldNotChangeStatusWhenNoMappings() {
        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(200);

        io.reactivex.rxjava3.core.Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response, never()).status(anyInt());
    }

    @Test
    void shouldChangeStatusWhenMappingExists() {
        StatusMapping mapping = new StatusMapping();
        mapping.setInputStatusCode(200);
        mapping.setOutputStatusCode(201);
        configuration.setStatusMappings(List.of(mapping));

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(200);

        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response).status(201);
    }

    @Test
    void shouldApplyLastMappingWhenMultipleMappingsForSameInput() {
        StatusMapping mapping1 = new StatusMapping();
        mapping1.setInputStatusCode(200);
        mapping1.setOutputStatusCode(201);

        StatusMapping mapping2 = new StatusMapping();
        mapping2.setInputStatusCode(200);
        mapping2.setOutputStatusCode(202);

        configuration.setStatusMappings(List.of(mapping1, mapping2));

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(200);

        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response).status(202);
    }

    @Test
    void shouldChangeStatusInMessageResponse() {
        StatusMapping mapping = new StatusMapping();
        mapping.setInputStatusCode(200);
        mapping.setOutputStatusCode(404);
        configuration.setStatusMappings(List.of(mapping));

        MessageExecutionContext messageContext = Mockito.mock(MessageExecutionContext.class);
        Response response = Mockito.mock(Response.class);
        when(messageContext.response()).thenReturn(response);
        when(response.status()).thenReturn(200);

        Completable completable = policy.onMessageResponse(messageContext);
        completable.test().assertComplete();

        verify(response).status(404);
    }

    @Test
    void shouldNotChangeStatusWhenMappingsListIsEmpty() {
        configuration.setStatusMappings(List.of());

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(200);

        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response, never()).status(anyInt());
    }

    @Test
    void shouldNotChangeStatusWhenNoMatchingInputStatusCode() {
        StatusMapping mapping = new StatusMapping();
        mapping.setInputStatusCode(404);
        mapping.setOutputStatusCode(200);
        configuration.setStatusMappings(List.of(mapping));

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(500);

        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response, never()).status(anyInt());
    }

    @Test
    void shouldNotChangeStatusWhenInputAndOutputStatusCodesAreSame() {
        StatusMapping mapping = new StatusMapping();
        mapping.setInputStatusCode(200);
        mapping.setOutputStatusCode(200);
        configuration.setStatusMappings(List.of(mapping));

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(200);

        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response, never()).status(anyInt());
    }

    @Test
    void shouldChangeStatusForMultipleDifferentMappings() {
        StatusMapping mapping1 = new StatusMapping();
        mapping1.setInputStatusCode(200);
        mapping1.setOutputStatusCode(201);

        StatusMapping mapping2 = new StatusMapping();
        mapping2.setInputStatusCode(404);
        mapping2.setOutputStatusCode(200);

        StatusMapping mapping3 = new StatusMapping();
        mapping3.setInputStatusCode(500);
        mapping3.setOutputStatusCode(503);

        configuration.setStatusMappings(List.of(mapping1, mapping2, mapping3));

        when(ctx.response()).thenReturn(response);

        when(response.status()).thenReturn(200);
        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();
        verify(response).status(201);

        reset(response);
        when(ctx.response()).thenReturn(response);

        when(response.status()).thenReturn(404);
        completable = policy.onResponse(ctx);
        completable.test().assertComplete();
        verify(response).status(200);

        reset(response);
        when(ctx.response()).thenReturn(response);

        when(response.status()).thenReturn(500);
        completable = policy.onResponse(ctx);
        completable.test().assertComplete();
        verify(response).status(503);
    }

    @Test
    void shouldMapMultipleInputStatusesToSameOutputStatus() {
        StatusMapping mapping1 = new StatusMapping();
        mapping1.setInputStatusCode(400);
        mapping1.setOutputStatusCode(200);

        StatusMapping mapping2 = new StatusMapping();
        mapping2.setInputStatusCode(401);
        mapping2.setOutputStatusCode(200);

        StatusMapping mapping3 = new StatusMapping();
        mapping3.setInputStatusCode(403);
        mapping3.setOutputStatusCode(200);

        configuration.setStatusMappings(List.of(mapping1, mapping2, mapping3));

        when(ctx.response()).thenReturn(response);

        when(response.status()).thenReturn(400);
        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();
        verify(response).status(200);

        reset(response);
        when(ctx.response()).thenReturn(response);

        when(response.status()).thenReturn(401);
        completable = policy.onResponse(ctx);
        completable.test().assertComplete();
        verify(response).status(200);

        reset(response);
        when(ctx.response()).thenReturn(response);

        when(response.status()).thenReturn(403);
        completable = policy.onResponse(ctx);
        completable.test().assertComplete();
        verify(response).status(200);
    }

    @Test
    void shouldNotChangeNonStandardStatusCodes() {
        StatusMapping mapping = new StatusMapping();
        mapping.setInputStatusCode(200);
        mapping.setOutputStatusCode(201);
        configuration.setStatusMappings(List.of(mapping));

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(599);

        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response, never()).status(anyInt());
    }

    @Test
    void shouldHandleLargeNumberOfMappings() {
        List<StatusMapping> mappings = new ArrayList<>();
        for (int i = 100; i < 600; i++) {
            StatusMapping mapping = new StatusMapping();
            mapping.setInputStatusCode(i);
            mapping.setOutputStatusCode(200);
            mappings.add(mapping);
        }
        configuration.setStatusMappings(mappings);

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(450);

        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response).status(200);
    }

    @Test
    void shouldHandleExceptionGracefully() {
        StatusMapping mapping = new StatusMapping();
        mapping.setInputStatusCode(200);
        mapping.setOutputStatusCode(201);
        configuration.setStatusMappings(List.of(mapping));

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenThrow(new RuntimeException("Unexpected error"));

        Completable completable = policy.onResponse(ctx);
        completable.test().assertError(RuntimeException.class);
    }

    @Test
    void shouldOnlyChangeStatusCodeNotOtherResponseAttributes() {
        StatusMapping mapping = new StatusMapping();
        mapping.setInputStatusCode(200);
        mapping.setOutputStatusCode(201);
        configuration.setStatusMappings(List.of(mapping));

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.create().add("Content-Type", "application/json"));

        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response).status(201);
        assertEquals("application/json", response.headers().getFirst("Content-Type"));
    }

    @Test
    void shouldHandleMessageResponseWithoutStatusCode() {
        MessageExecutionContext messageContext = Mockito.mock(MessageExecutionContext.class);
        Response response = Mockito.mock(Response.class);
        when(messageContext.response()).thenReturn(response);
        when(response.status()).thenReturn(-1);

        Completable completable = policy.onMessageResponse(messageContext);
        completable.test().assertComplete();

        verify(response, never()).status(anyInt());
    }

    @Test
    void shouldHandleConcurrentExecutions() {
        StatusMapping mapping = new StatusMapping();
        mapping.setInputStatusCode(200);
        mapping.setOutputStatusCode(201);
        configuration.setStatusMappings(List.of(mapping));

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(200);

        int threads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(
                CompletableFuture.runAsync(
                    () -> {
                        Completable completable = policy.onResponse(ctx);
                        completable.blockingAwait();
                    },
                    executorService
                )
            );
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        verify(response, times(threads)).status(201);
    }

    @Test
    void shouldHandleInvalidStatusCodesGracefully() {
        StatusMapping mapping = new StatusMapping();
        mapping.setInputStatusCode(-100);
        mapping.setOutputStatusCode(200);
        configuration.setStatusMappings(List.of(mapping));

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(-100);

        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response).status(200);
    }

    @Test
    void shouldHandleBoundaryStatusCodes() {
        StatusMapping mapping = new StatusMapping();
        mapping.setInputStatusCode(100);
        mapping.setOutputStatusCode(200);
        configuration.setStatusMappings(List.of(mapping));

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(100);

        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response).status(200);
    }

    @Test
    void shouldApplyLastMappingWhenDuplicateMappingsExist() {
        StatusMapping mapping1 = new StatusMapping();
        mapping1.setInputStatusCode(200);
        mapping1.setOutputStatusCode(201);

        StatusMapping mapping2 = new StatusMapping();
        mapping2.setInputStatusCode(200);
        mapping2.setOutputStatusCode(202);

        StatusMapping mapping3 = new StatusMapping();
        mapping3.setInputStatusCode(200);
        mapping3.setOutputStatusCode(203);

        configuration.setStatusMappings(List.of(mapping1, mapping2, mapping3));

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(200);

        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response).status(203);
    }

    @Test
    void shouldHandleNullResponseGracefully() {
        when(ctx.response()).thenReturn(null);

        Completable completable = policy.onResponse(ctx);
        completable.test().assertError(NullPointerException.class);
    }

    @Test
    void shouldHandleImmutableResponse() {
        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(200);
        doThrow(new UnsupportedOperationException()).when(response).status(anyInt());

        StatusMapping mapping = new StatusMapping();
        mapping.setInputStatusCode(200);
        mapping.setOutputStatusCode(201);
        configuration.setStatusMappings(List.of(mapping));

        Completable completable = policy.onResponse(ctx);
        completable.test().assertError(UnsupportedOperationException.class);
    }

    @Test
    void shouldLogStatusCodeChange() {
        StatusMapping mapping = new StatusMapping();
        mapping.setInputStatusCode(200);
        mapping.setOutputStatusCode(201);
        configuration.setStatusMappings(List.of(mapping));

        when(ctx.response()).thenReturn(response);
        when(response.status()).thenReturn(200);

        Logger logger = (Logger) LoggerFactory.getLogger(StatusCodePolicy.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        Completable completable = policy.onResponse(ctx);
        completable.test().assertComplete();

        verify(response).status(201);

        List<ILoggingEvent> logsList = appender.list;
        assertTrue(
            logsList
                .stream()
                .anyMatch(event -> event.getFormattedMessage().contains("StatusCodePolicy: changing status code from 200 to 201"))
        );
    }
}
