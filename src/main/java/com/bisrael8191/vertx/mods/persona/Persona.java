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

package com.bisrael8191.vertx.mods.persona;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Verify a Persona assertion with the Mozilla Persona server.
 * <p/>
 * This module only handles the communication to the verifier
 * and does not do any caching or session Id generation. This
 * allows the module to be used as part of a larger authentication
 * system.
 * <p/>
 * API: <a href="https://developer.mozilla.org/en-US/docs/Mozilla/Persona/Remote_Verification_API>Remote Verification API</a>
 * <p/>
 * Thanks to <a href="https://github.com/pmlopes/yoke/blob/master/examples/mozilla-persona/src/main/java/com/jetdrone/vertx/persona/Persona.java">Yoke's Persona Example</a>
 * for showing how to format the Persona request properly.
 *
 * @author Brad Israel
 */
public class Persona extends BusModBase {

    private Handler<Message<JsonObject>> verifyHandler;

    private static final String DEFAULT_VERIFY_ADDRESS = "persona.verify";
    private static final String DEFAULT_AUDIENCE = "http://localhost:8080";
    private static final String PERSONA_VERIFIER = "verifier.login.persona.org";

    // Configuration values
    private String verifyAddress;
    private String audience;

    /**
     * Start the persona busmod
     */
    public void start() {
        super.start();

        // Get the event bus address to listen for verification requests
        this.verifyAddress = getOptionalStringConfig("address", DEFAULT_VERIFY_ADDRESS);

        // Get the defined audience string
        this.audience = getOptionalStringConfig("audience", DEFAULT_AUDIENCE);

        // Register on the event bus
        eb.registerHandler(verifyAddress, new Handler<Message<JsonObject>>() {

            @Override
            public void handle(Message<JsonObject> message) {
                verify(message);
            }
        });
    }

    /**
     * Verify a client's assertion with the Persona verifier server
     *
     * @param verifyMessage
     */
    private void verify(final Message<JsonObject> verifyMessage) {
        // Message must contain an assertion from the client
        String assertion = getMandatoryString("assertion", verifyMessage);
        if(assertion == null) {
            return;
        }

        // Generate the data to send to the verifier
        String data;
        try {
            data = "assertion=" + URLEncoder.encode(assertion, "UTF-8") +
                    "&audience=" + URLEncoder.encode(this.audience, "UTF-8");
        } catch(UnsupportedEncodingException e) {
            sendError(verifyMessage, "Assertion encoding error", e);
            return;
        }

        // Create an SSL connection to Persona's REST api
        HttpClient personaClient =
                getVertx().createHttpClient().setSSL(true).setVerifyHost(true).setHost(PERSONA_VERIFIER).setPort(443);

        // Make sure host verification is turned on
        if(!personaClient.isVerifyHost()) {
            sendStatus(
                    "error"
                    , verifyMessage
                    , new JsonObject().putString("message", "Host verification turned off for " + PERSONA_VERIFIER));
            return;
        }

        // Create a handler for the response from the /verify call
        HttpClientRequest personaRequest = personaClient.post("/verify", new Handler<HttpClientResponse>() {

            @Override
            public void handle(HttpClientResponse response) {
                // Process the response body
                response.bodyHandler(new Handler<Buffer>() {

                    @Override
                    public void handle(Buffer buffer) {
                        try {
                            // Convert buffer to JSON
                            JsonObject verification = new JsonObject(buffer.toString());

                            // Check if the assertion was verified
                            boolean valid = "okay".equals(verification.getString("status"));

                            // Remove the Persona status field so that the
                            // message can follow the busmod standard reply
                            verification.removeField("status");

                            if(valid) {
                                // Send the verification back with an 'ok' status
                                sendOK(verifyMessage, verification);
                            } else {
                                // Send the verification back with an 'error' status
                                sendStatus("error", verifyMessage, new JsonObject().putString("message", verification.getString("reason")));
                            }
                        } catch(DecodeException e) {
                            sendError(verifyMessage, "Received an invalid response from the Persona server", e);
                        }
                    }
                });

                // Catch any exceptions and reply to the main message
                response.exceptionHandler(new Handler<Throwable>() {

                    @Override
                    public void handle(Throwable err) {
                        sendError(verifyMessage, "Error communicating with the Persona server: " + err.getMessage());
                    }
                });
            }
        });

        // Post the data as a form attribute
        personaRequest.putHeader("content-type", "application/x-www-form-urlencoded");
        personaRequest.putHeader("content-length", Integer.toString(data.length()));
        personaRequest.end(data);
    }
}
