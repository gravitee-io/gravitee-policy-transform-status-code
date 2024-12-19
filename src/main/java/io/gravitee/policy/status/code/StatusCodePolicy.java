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

import io.gravitee.gateway.reactive.api.context.GenericExecutionContext;
import io.gravitee.gateway.reactive.api.context.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.MessageExecutionContext;
import io.gravitee.gateway.reactive.api.policy.Policy;
import io.gravitee.policy.status.code.configuration.StatusCodePolicyConfiguration;
import io.gravitee.policy.status.code.configuration.StatusMapping;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StatusCodePolicy implements Policy {

    private final StatusCodePolicyConfiguration configuration;

    @Override
    public String id() {
        return "status-code";
    }

    @Override
    public Completable onResponse(HttpExecutionContext ctx) {
        return doOnResponse(ctx);
    }

    private Completable doOnResponse(GenericExecutionContext ctx) {
        return Completable.fromRunnable(() -> {
            int originalStatusCode = ctx.response().status();
            int finalStatusCode = originalStatusCode;

            for (StatusMapping mapping : configuration.getStatusMappings()) {
                if (mapping.getInputStatusCode() == originalStatusCode) {
                    finalStatusCode = mapping.getOutputStatusCode();
                    log.debug("StatusCodePolicy: changing status code from {} to {}", originalStatusCode, finalStatusCode);
                    // Do not break; last mapping takes precedence
                }
            }
            if (finalStatusCode != originalStatusCode) {
                ctx.response().status(finalStatusCode);
            }
        });
    }
}
