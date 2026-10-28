# Hubble HTTP schema creation and query timeouts

Hubble API bodies differ from Server API bodies. The Hubble prefix is
`/api/v1.3/graphspaces/{space}/graphs/{graph}`.

## Create a vertex label

After logging in and retaining the Hubble session cookie, submit:

```json
{
  "name": "http_demo_person",
  "id_strategy": "AUTOMATIC",
  "properties": []
}
```

Use `POST {prefix}/schema/vertexlabels`. `style` and `open_label_index` are
optional. Missing or null styles use the standard style displaying `~id`;
missing or null `open_label_index` enables the label index. An explicit `false`
is preserved. The same optional-field defaults apply to edge labels.
Existing explicit styles retain their color and display settings.
Style keys use `display_fields` and `join_symbols`, not camel-case keys.
`PRIMARY_KEY` labels still require existing properties and matching
`primary_keys`; defaults do not relax schema validation.

## Long Gremlin queries

The integration endpoint `POST {prefix}/gremlin` accepts `query`, `username`
and `password`, and requires the authenticated session. The workspace endpoint
`gremlin-query` has its own request model. Use HTTPS when sending credentials
outside a trusted local network.

A transport timeout returns HTTP 504 with a concise timeout message. Detailed
exceptions remain available as the server-side cause. For long queries, submit
`POST /graphspaces/{space}/graphs/{graph}/jobs/gremlin` directly to Server with
`{"gremlin":"g.V().hasLabel(\"person\").count()"}`. Poll the returned task ID at
`GET /graphspaces/{space}/graphs/{graph}/tasks/{task_id}` until terminal status,
then read `task_result`. Hubble transport and Server evaluation timeouts are
independent; a failed synchronous response is not an exact graph count.
