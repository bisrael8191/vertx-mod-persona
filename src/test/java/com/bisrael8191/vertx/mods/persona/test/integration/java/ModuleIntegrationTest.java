/*
 * Copyright 2013 Brad Israel
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

package com.bisrael8191.vertx.mods.persona.test.integration.java;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;

import static org.vertx.testtools.VertxAssert.*;

/**
 * Test the Persona module using the <a href="http://personatestuser.org">Persona Test User site</a>
 */
public class ModuleIntegrationTest extends TestVerticle {

    private static final String PTU_HOST = "personatestuser.org";
    private static final String LOCAL_AUDIENCE = "http://localhost:8080";

    /**
     * Test case: Error returned if no assertion is specified
     */
    @Test
    public void testNullAssertion() {
        JsonObject invalid = new JsonObject();

        vertx.eventBus().send("persona.verify", invalid, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> reply) {
                container.logger().info("Persona response: " + reply.body().toString());
                assertEquals("error", reply.body().getString("status"));
                assertEquals("assertion must be specified", reply.body().getString("message"));

                testComplete();
            }
        });
    }

    /**
     * Test case: Error returned if the assertion cannot be verified
     */
    @Test
    public void testInvalidAssertion() {
        JsonObject invalid = new JsonObject().putString("assertion", "42");

        vertx.eventBus().send("persona.verify", invalid, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> reply) {
                container.logger().info("Persona response: " + reply.body().toString());
                assertEquals("error", reply.body().getString("status"));
                assertNotNull(reply.body().getString("message"));

                testComplete();
            }
        });
    }

    /**
     * Test case: Error returned if the audience domain doesn't match the configured domain
     */
    @Test
    public void testInvalidAudience() {
        String restAddress = GenerateTestUserPath(PersonaTestUserApi.EMAIL_AND_ASSERTION, "http://fakesite.com:8080", null, null);
        assertNotNull(restAddress);

        vertx.createHttpClient().setHost(PTU_HOST).getNow(restAddress, new Handler<HttpClientResponse>() {

            @Override
            public void handle(HttpClientResponse response) {
                response.bodyHandler(new Handler<Buffer>() {

                    @Override
                    public void handle(Buffer buffer) {
                        JsonObject respJson = new JsonObject(buffer.toString());
                        final String testEmail = respJson.getString("email");
                        final String testAudience = respJson.getString("audience");

                        JsonObject testUser = new JsonObject().putString("assertion", respJson.getString("assertion"));
                        container.logger().info("Assertion = " + testUser.getString("assertion"));

                        vertx.eventBus().send("persona.verify", testUser, new Handler<Message<JsonObject>>() {
                            @Override
                            public void handle(Message<JsonObject> reply) {
                                container.logger().info("Persona response: " + reply.body().toString());
                                assertEquals("error", reply.body().getString("status"));
                                assertNotNull(reply.body().getString("message"));

                                testComplete();
                            }
                        });
                    }
                });
            }
        });

    }

    /**
     * Test case: OK returned if assertion can be properly verified by the Mozilla Persona verifier
     */
    @Test
    public void testValidAssertion() {
        String restAddress = GenerateTestUserPath(PersonaTestUserApi.EMAIL_AND_ASSERTION, LOCAL_AUDIENCE, null, null);
        assertNotNull(restAddress);

        vertx.createHttpClient().setHost(PTU_HOST).getNow(restAddress, new Handler<HttpClientResponse>() {

            @Override
            public void handle(HttpClientResponse response) {
                response.bodyHandler(new Handler<Buffer>() {

                    @Override
                    public void handle(Buffer buffer) {
                        JsonObject respJson = new JsonObject(buffer.toString());
                        final String testEmail = respJson.getString("email");
                        final String testAudience = respJson.getString("audience");

                        JsonObject testUser = new JsonObject().putString("assertion", respJson.getString("assertion"));
                        container.logger().info("Assertion = " + testUser.getString("assertion"));

                        vertx.eventBus().send("persona.verify", testUser, new Handler<Message<JsonObject>>() {
                            @Override
                            public void handle(Message<JsonObject> reply) {
                                container.logger().info("Persona response: " + reply.body().toString());
                                assertEquals("ok", reply.body().getString("status"));
                                assertEquals(testEmail, reply.body().getString("email"));
                                assertEquals(testAudience, reply.body().getString("audience"));

                                Date expires = new Date(reply.body().getLong("expires"));
                                assertTrue("Expiration must be greater than the current time", expires.compareTo(new Date()) > 0);

                                testComplete();
                            }
                        });
                    }
                });
            }
        });

    }

    /**
     * Paths allowed by the Persona Test User site
     */
    public enum PersonaTestUserApi {
        VERIFIED_EMAIL("/email"),
        UNVERIFIED_EMAIL("/unverified_email"),
        EMAIL_AND_ASSERTION("/email_with_assertion"),
        ASSERTION("/assertion");

        private final String restPath;

        private PersonaTestUserApi(String restPath) {
            this.restPath = restPath;
        }

        public String GetRestPath() {
            return this.restPath;
        }
    }

    /**
     * Helper function to generate the REST API string
     *
     * @param api Type of API call
     * @param audience Audience to use
     * @param email Email to use
     * @param password Password to use
     * @return API string
     */
    private String GenerateTestUserPath(PersonaTestUserApi api, String audience, String email, String password) {
        String restAddress = null;

        try {
            switch(api) {
                case VERIFIED_EMAIL:
                case UNVERIFIED_EMAIL:
                    restAddress = api.GetRestPath();
                    break;
                case EMAIL_AND_ASSERTION:
                    assertNotNull(audience);
                    restAddress = api.GetRestPath() + "/" + URLEncoder.encode(audience, "UTF-8");
                    break;
                case ASSERTION:
                    break;
            }
        } catch(UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return restAddress;
    }

    @Override
    public void start() {
        // Make sure we call initialize() - this sets up the assert stuff so assert functionality works correctly
        initialize();
        // Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
        // don't have to hard code it in your tests
        container.deployModule(System.getProperty("vertx.modulename"), new AsyncResultHandler<String>() {
            @Override
            public void handle(AsyncResult<String> asyncResult) {
                // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
                if(asyncResult.failed()) {
                    container.logger().error(asyncResult.cause());
                }
                assertTrue(asyncResult.succeeded());
                assertNotNull("deploymentID should not be null", asyncResult.result());
                // If deployed correctly then start the tests!
                startTests();
            }
        });
    }

}
