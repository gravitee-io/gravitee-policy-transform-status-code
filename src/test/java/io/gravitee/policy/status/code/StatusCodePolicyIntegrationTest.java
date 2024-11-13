package io.gravitee.policy.status.code;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.vertx.core.http.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.gravitee.apim.gateway.tests.sdk.AbstractGatewayTest;
import io.gravitee.apim.gateway.tests.sdk.configuration.GatewayConfigurationBuilder;
import io.gravitee.definition.model.Api;
import io.gravitee.definition.model.flow.Flow;
import io.gravitee.definition.model.flow.Operator;
import io.gravitee.definition.model.flow.PathOperator;
import io.gravitee.definition.model.flow.Step;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class StatusCodePolicyIntegrationTest extends AbstractGatewayTest {

    private static final int WIREMOCK_PORT = 8089;

    @Override
    @BeforeEach
    public void setUp(Vertx vertx) throws Exception {
        // Initialize the application context
        this.applicationContext = new AnnotationConfigApplicationContext();
        ((AnnotationConfigApplicationContext) applicationContext).refresh(); // Refresh the context to initialize it

        // Proceed with the rest of the setup
        super.setUp(vertx);

        // Now wiremock is initialized, set up stubs
        wiremock.stubFor(get(urlEqualTo("/test")).willReturn(aResponse().withStatus(202).withBody("Accepted")));
    }

    @Override
    protected void configureGateway(GatewayConfigurationBuilder gatewayConfigurationBuilder) {
        gatewayConfigurationBuilder.set("http.port", gatewayPort());
    }

    @Override
    protected void configureWireMock(WireMockConfiguration configuration) {
        configuration.port(WIREMOCK_PORT);
    }

    @Test
    void shouldTransformStatusCode() {
        // Create an HttpClient using Vertx
        HttpClientOptions options = new HttpClientOptions();
        this.configureHttpClient(options);
        HttpClient client = vertx.createHttpClient(options);

        // Send a request to the gateway
        var response = client
                .request(GET, gatewayPort(), "localhost", "/test")
                .flatMap(HttpClientRequest::send)
                .flatMap(resp -> resp.body().map(body -> java.util.Map.entry(resp, body)))
                .blockingGet();

        // Then
        assertThat(response.getKey().statusCode()).isEqualTo(200); // Verify the status code is transformed to 200
        String body = response.getValue().toString();
        assertThat(body).isEqualTo("Accepted"); // Verify the response body is unchanged
    }

    @Override
    public void configureApi(Api api) {
        // Set the API's backend to point to the mock backend using the fixed port
        api.getProxy().getGroups().iterator().next().getEndpoints().iterator().next().setTarget("http://localhost:" + WIREMOCK_PORT);

        // Configure the API with a flow that applies the StatusCodePolicy on the RESPONSE phase
        Flow flow = new Flow();
        flow.setName("Transform status code");
        flow.setEnabled(true);

        // Set the path operator
        PathOperator pathOperator = new PathOperator();
        pathOperator.setPath("/");
        pathOperator.setOperator(Operator.STARTS_WITH);
        flow.setPathOperator(pathOperator);

        // Set the methods
        flow.setMethods(Set.of(io.gravitee.common.http.HttpMethod.GET));

        // Create the step for the policy
        Step step = new Step();
        step.setName("status-code");
        step.setPolicy("status-code-policy");
        // Configure the policy with mappings to transform 202 to 200
        step.setConfiguration(
                """
                    {
                      "statusMappings": [
                        {
                          "inputStatusCode": 202,
                          "outputStatusCode": 200
                        }
                      ]
                    }"""
        );
        step.setEnabled(true);

        // Since the policy needs to run on the RESPONSE phase, we add it to the 'post' steps
        flow.setPost(List.of(step));

        api.setFlows(List.of(flow));
    }
}
