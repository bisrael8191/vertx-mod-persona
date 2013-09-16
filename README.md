# Persona Verification Module

This worker module can verify a user's login information through the Mozilla Persona protocol.

## Dependencies

This module requires an internet connection so that it can connect to the Mozilla Persona verifier at `https://verifier.login.persona.org`.

## Name

The module name is `vertx-mod-persona`.

## Configuration

The Persona module requires the following configuration:

    {
        "address": <address>,
        "audience": <your server hostname>
    }

Let's take a look at each field in turn:

* `address` The main address for the busmod. Every busmod has a main address. Defaults to `persona.verify`.
* `audience` Host name of your server. Defaults to `http://localhost:8080`.

For example, to verify logins to your server:

    {
        "audience": "https://www.myawesomesite.com"
    }

## Operations

### Verify
Verify a user's login information using the verifier API.

Useful links:

* [Persona Quick Setup] (https://developer.mozilla.org/en-US/docs/Mozilla/Persona/Quick_Setup)
* [Remote Verification API] (https://developer.mozilla.org/en-US/docs/Mozilla/Persona/Remote_Verification_API)

In Step 3 of the Quick Setup, the site will generate an 'assertion' and POST it to the REST endpoint `/auth/login`. This endpoint needs to be caught and routed to a handler that can pull the assertion from the 'form-attributes' data. Then a JSON message can be sent over the event bus to the module address (`persona.verify`) to perform Step 4. 

The JSON message has the following structure:

    {
        "assertion": <string>
    }

If the assertion was verified successfully, the following reply will be returned:

    {
        "status": "ok",
        "audience": <your server hostname>,
        "expires": <time that the token expires>,
        "issuer": "login.persona.org",
        "email": <user's verified email address>
    }

An example response (from the integration test):

    {
        "status": "ok",
        "audience": "http://localhost:8080",
        "expires": 1379313971000,
        "issuer": "login.persona.org",
        "email": "montenegro108360@personatestuser.org"
    }

Where:

* `status` The verification status. Can be either `ok` or `error`.
* `audience` The configured audience
* `expires` The date when the token should be refreshed (about a couple minutes). Until then it can be cached and assumed valid.
* `issuer` The entity that verified the email address
* `email` The user's verified email address. This may be stored in the application side as the user id.

If the assertion could not be verified, the following reply will be returned:

    {
        "status": "error",
        "message": <string>
    }

If the user was previously logged in, but fails verification on a refresh you must have a mechanism to log the user out and have them log back in through Persona.
