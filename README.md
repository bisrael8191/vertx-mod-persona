# Module Name

This is the default test module from the Vert.x template.

## Dependencies

None.

## Name

The module name is `my-module`.

## Configuration

The Persona module requires the following configuration:

    {
        "address": <string>
    }

Let's take a look at each field in turn:

* `address` The main address for the verticle. Defaults to `ping-address`.

For example:

    {
        "address": "different-ping-address"
    }

## Operations

### Ping
Send any string to the module's address and receive a `pong!` string back.