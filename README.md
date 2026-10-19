# Messaging Telemetry Spring Boot Starter

Biblioteca Java de **tracing distribuído para mensageria** construída sobre o **OpenTelemetry**.

- **Agnóstica ao protocolo**: o mesmo modelo serve para **Kafka, AWS SQS, RabbitMQ, Google Pub/Sub** (e qualquer outro broker, via extensão).
- **Agnóstica ao fornecedor**: exporta via OTLP para **New Relic, Dynatrace, Elastic (Kibana)** e qualquer receptor open source (**OpenTelemetry Collector, Jaeger, Grafana Tempo, SigNoz...**).
- **Uso declarativo no Spring**: `@EnableMessagingTelemetry` + `@TracedProducer` / `@TracedConsumer` + uma classe de configuração.
- **Controllers HTTP rastreados automaticamente** (Spring MVC): span SERVER por requisição, continuando o trace de quem chamou e servindo de pai para as mensagens publicadas.
- **Clean Architecture + DDD**: domínio puro, sem dependência de OpenTelemetry, Spring ou brokers.
- **Feign** como cliente HTTP do exportador OTLP (protobuf + gzip + retry).

O produtor injeta o contexto W3C (`traceparent`, `tracestate`, `baggage`) nos headers da mensagem e o consumidor o extrai. Assim, **produtor e consumidor aparecem no mesmo trace**, mesmo em serviços, linguagens e brokers diferentes.

```
 orders-api                      Kafka / SQS / RabbitMQ / Pub/Sub                 payments-worker
┌─────────────────────┐        ┌──────────────────────────────────┐        ┌───────────────────────┐
│ @TracedProducer     │──────▶ │ headers: traceparent=00-4bf9...  │ ─────▶ │ @TracedConsumer       │
│ span PRODUCER       │        └──────────────────────────────────┘        │ span CONSUMER (filho) │
└─────────┬───────────┘                                                    └──────────┬────────────┘
          │  OTLP/HTTP (Feign)                                                         │
          └──────────────────▶  New Relic | Dynatrace | Elastic/Kibana | Collector ◀───┘
```

---

## Sumário

1. [Instalação](#1-instalação)
2. [Início rápido](#2-início-rápido)
3. [Exemplos por broker](#3-exemplos-por-broker)
4. [Configurando o backend (New Relic, Dynatrace, Elastic/Kibana, OTLP)](#4-configurando-o-backend)
5. [Classe de configuração (`MessagingTelemetryConfigurer`)](#5-classe-de-configuração)
6. [Referência das anotações](#6-referência-das-anotações)
7. [Uso programático (sem anotações)](#7-uso-programático-sem-anotações)
8. [Referência de propriedades](#8-referência-de-propriedades)
9. [Arquitetura](#9-arquitetura)
10. [Estendendo a biblioteca](#10-estendendo-a-biblioteca)
11. [Boas práticas, limitações e FAQ](#11-boas-práticas-limitações-e-faq)

---

## 1. Instalação

Requisitos: **Java 17+** e **Spring Boot 3.x**.

Cada merge na `main` publica uma nova versão no GitHub Packages (veja [Publicação e versionamento](#publicação-e-versionamento-github-actions)). Para usar localmente sem publicar:

```bash
mvn clean install
```

Adicione a dependência:

```xml
<dependency>
    <groupId>com.bribeiro.telemetry</groupId>
    <artifactId>messaging-telemetry-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Os clients dos brokers são **opcionais**: a biblioteca só ativa o adaptador do Kafka se `kafka-clients` estiver no classpath, o do SQS se o AWS SDK v2 estiver, e assim por diante. Nada é imposto ao seu projeto.

---

## 2. Início rápido

### 2.1 Habilite com a anotação

```java
@Configuration
@EnableMessagingTelemetry
public class TelemetryConfig {
}
```

Sem `@EnableMessagingTelemetry` nada é ativado (opt-in explícito).

### 2.2 Configure o backend no `application.yml`

```yaml
spring:
  application:
    name: orders-api          # vira o service.name no APM

messaging:
  telemetry:
    environment: prod
    exporter:
      backend: NEW_RELIC
      api-key: ${NEW_RELIC_LICENSE_KEY}
```

### 2.3 Anote quem publica e quem consome

```java
@Service
public class OrderPublisher {

    private final KafkaTemplate<String, String> kafka;

    @TracedProducer(messageId = "#order.id", attributes = "order.channel=#order.channel")
    public void publish(ProducerRecord<String, String> record, Order order) {
        kafka.send(record);
    }
}

@Component
public class OrderListener {

    @TracedConsumer
    @KafkaListener(topics = "orders-created")
    public void onOrder(ConsumerRecord<String, String> record) {
        // o span CONSUMER já é filho do span PRODUCER do outro serviço
    }
}
```

Pronto: o trace `send orders-created → process orders-created` aparece no New Relic com os atributos semânticos de mensageria.

### 2.4 Controllers HTTP (automático)

Com `@EnableMessagingTelemetry` em uma aplicação **Spring MVC**, toda requisição atendida por um controller ganha um span **SERVER**, sem nenhuma anotação extra:

```java
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderPublisher publisher;

    @PostMapping("/orders")
    public ResponseEntity<Void> create(@RequestBody Order order) {
        publisher.publish(new ProducerRecord<>("orders-created", order.id(), json(order)));  // @TracedProducer
        return ResponseEntity.accepted().build();
    }
}
```

O trace completo fica assim, inclusive vindo de outro serviço que mandou o header `traceparent`:

```
gateway ──HTTP──▶ POST /orders (SERVER) ──▶ send orders-created (PRODUCER) ──Kafka──▶ process orders-created (CONSUMER)
```

O que é registrado:

| Atributo | Exemplo |
|---|---|
| nome do span | `GET /orders/{id}` (usa a rota do `@GetMapping`, nunca o path com ids, para não explodir a cardinalidade) |
| `http.request.method` | `GET` |
| `http.route` | `/orders/{id}` |
| `url.path` | `/orders/42` (a query string **não** é capturada) |
| `http.response.status_code` | `200` |
| `url.scheme`, `server.address`, `server.port` | `https`, `api.empresa.com`, `443` |
| `client.address` | primeiro IP do `X-Forwarded-For`, ou o IP da conexão |
| `user_agent.original` | `Mozilla/5.0 ...` |

Regras de erro (convenções semânticas do OpenTelemetry):

- **5xx** marca o span como `ERROR`. **4xx não**, porque é erro do cliente e não do serviço.
- Exceções tratadas por `@ExceptionHandler` / `@ControllerAdvice` que resultam em 5xx entram no span com o evento `exception` e `error.type` = classe da exceção.
- Requisições assíncronas (`CompletableFuture`, `DeferredResult`, `Callable`) só fecham o span quando a resposta é escrita.
- O filtro roda antes do Spring Security, então falhas de autenticação (401/403) também aparecem.

Configuração:

```yaml
messaging:
  telemetry:
    http:
      enabled: true                  # false desliga só o rastreamento HTTP
      exclude-paths:                 # padrão: /actuator/**, /favicon.ico
        - /actuator/**
        - /health
        - /swagger-ui/**
```

---

## 3. Exemplos por broker

A regra é sempre a mesma: **o método anotado precisa receber o objeto da mensagem** (ou um `Map` de headers) para que a biblioteca consiga injetar/extrair o contexto. O primeiro argumento reconhecido é usado.

| Broker | Tipos reconhecidos | Injeta no envio? | Extrai no consumo? |
|---|---|---|---|
| Kafka | `ProducerRecord`, `ConsumerRecord`, `Headers` | ✅ | ✅ |
| RabbitMQ | `org.springframework.amqp.core.Message` | ✅ | ✅ |
| AWS SQS (SDK v2) | `software.amazon.awssdk.services.sqs.model.Message` | ⚠️ imutável → `SqsTraceAttributes` | ✅ |
| Google Pub/Sub | `com.google.pubsub.v1.PubsubMessage` | ⚠️ imutável → `currentPropagationHeaders()` | ✅ |
| Qualquer listener Spring | `org.springframework.messaging.Message<?>` (Spring Kafka, Spring AMQP, Spring Cloud AWS, Spring Cloud GCP) | ❌ imutável | ✅ (detecta o broker pelos headers) |
| Genérico | `Map<String, ?>` mutável | ✅ | ✅ |

### 3.1 Kafka

```java
@TracedProducer                        // destino vem de record.topic()
public void publish(ProducerRecord<String, OrderEvent> record) {
    kafkaTemplate.send(record);
}

@TracedConsumer                        // destino e messageId vêm do ConsumerRecord
@KafkaListener(topics = "orders-created", groupId = "payments")
public void consume(ConsumerRecord<String, OrderEvent> record) { ... }

// também funciona com Message<?> do spring-messaging
@TracedConsumer
@KafkaListener(topics = "orders-created")
public void consume(Message<OrderEvent> message) { ... }
```

> Se o listener recebe apenas o payload (`String`, DTO), não há headers para extrair. Receba `ConsumerRecord` ou `Message<?>`.

### 3.2 RabbitMQ

```java
@TracedProducer(destination = "${app.rabbit.exchange}")
public void publish(Message message) {
    rabbitTemplate.send(exchange, "order.created", message);
}

@TracedConsumer
@RabbitListener(queues = "payments.orders")
public void consume(Message message) { ... }  // destino = consumerQueue
```

Usando `convertAndSend` com objeto, propague via `MessagePostProcessor`:

```java
@TracedProducer(destination = "orders")
public void publish(OrderEvent event) {
    Map<String, String> trace = telemetry.currentPropagationHeaders();
    rabbitTemplate.convertAndSend("orders", "order.created", event, msg -> {
        trace.forEach(msg.getMessageProperties()::setHeader);
        return msg;
    });
}
```

### 3.3 AWS SQS

Objetos do AWS SDK v2 são imutáveis, então no envio use `currentPropagationHeaders()` **dentro** do método anotado:

```java
@TracedProducer(destination = "orders.fifo", system = MessagingSystem.AWS_SQS)
public void publish(OrderEvent event) {
    sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(json(event))
            .messageAttributes(SqsTraceAttributes.from(telemetry.currentPropagationHeaders()))
            .build());
}
```

No consumo, peça os atributos no `receive`:

```java
List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageAttributeNames("All")        // obrigatório para receber o traceparent
        .build()).messages();

messages.forEach(processor::process);

@TracedConsumer(destination = "orders.fifo")
public void process(Message message) { ... }
```

Com **Spring Cloud AWS** (`@SqsListener`), receba `Message<?>` do spring-messaging:

```java
@TracedConsumer
@SqsListener("orders")
public void consume(org.springframework.messaging.Message<OrderEvent> message) { ... }
```

> O SQS aceita no máximo **10 message attributes** por mensagem. O W3C usa até 3 (`traceparent`, `tracestate`, `baggage`).

### 3.4 Google Pub/Sub

```java
@TracedProducer(destination = "projects/my-project/topics/orders", system = MessagingSystem.GCP_PUBSUB)
public void publish(OrderEvent event) {
    PubsubMessage message = PubsubMessage.newBuilder()
            .setData(ByteString.copyFromUtf8(json(event)))
            .putAllAttributes(telemetry.currentPropagationHeaders())
            .build();
    publisher.publish(message);
}

@TracedConsumer(destination = "payments-sub")
public void receive(PubsubMessage message, AckReplyConsumer consumer) {
    ...
    consumer.ack();
}
```

### 3.5 Qualquer outro protocolo (NATS, ActiveMQ, HTTP webhook...)

Use um `Map` mutável de headers:

```java
@TracedProducer(destination = "order.events", system = MessagingSystem.GENERIC)
public void publish(Map<String, String> headers, byte[] body) {
    natsConnection.publish("order.events", toNatsHeaders(headers), body);
}
```

Ou crie um adaptador próprio ([seção 10](#10-estendendo-a-biblioteca)).

---

## 4. Configurando o backend

A troca de fornecedor é **só configuração**. Todos usam OTLP/HTTP em protobuf, enviado por um cliente **Feign** com gzip, timeouts e retry em `429/502/503/504`.

### 4.1 New Relic

```yaml
messaging:
  telemetry:
    exporter:
      backend: NEW_RELIC
      api-key: ${NEW_RELIC_LICENSE_KEY}   # Ingest - License key
      region: US                          # US (padrão), EU ou FEDRAMP
```

Endpoint resolvido: `https://otlp.nr-data.net/v1/traces` (EU: `https://otlp.eu01.nr-data.net`). Header: `api-key`.
Visualização: *APM & Services → orders-api → Distributed tracing*.

### 4.2 Dynatrace

```yaml
messaging:
  telemetry:
    exporter:
      backend: DYNATRACE
      endpoint: https://abc12345.live.dynatrace.com    # ou URL do ActiveGate
      api-key: ${DT_API_TOKEN}                         # escopo: openTelemetryTrace.ingest
```

Endpoint resolvido: `https://abc12345.live.dynatrace.com/api/v2/otlp/v1/traces`. Header: `Authorization: Api-Token <token>`.
A Dynatrace aceita **apenas protobuf binário**, que é o formato desta biblioteca.

### 4.3 Elastic Observability / Kibana

**Elastic Cloud ou APM Server**

```yaml
messaging:
  telemetry:
    exporter:
      backend: ELASTIC
      endpoint: https://my-deployment.apm.us-east-1.aws.cloud.es.io:443
      api-key: ${ELASTIC_APM_API_KEY}     # vira "Authorization: ApiKey ..."
      # para secret token use:  api-key: "Bearer ${ELASTIC_APM_SECRET_TOKEN}"
```

Visualização no **Kibana**: *Observability → APM → Services / Traces*.

**Stack self-hosted (Elasticsearch + Kibana + APM Server)**: aponte `endpoint` para o APM Server (`http://apm-server:8200`). Sem autenticação, omita `api-key`.

**Stack 100% open source via OpenTelemetry Collector** (sem APM Server):

```yaml
messaging:
  telemetry:
    exporter:
      backend: OTLP
      endpoint: http://otel-collector:4318
```

```yaml
# otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      http:
        endpoint: 0.0.0.0:4318
exporters:
  elasticsearch:
    endpoints: [ "http://elasticsearch:9200" ]
    traces_index: messaging-traces
service:
  pipelines:
    traces:
      receivers: [ otlp ]
      exporters: [ elasticsearch ]
```

No Kibana, crie um *Data View* para `messaging-traces*` e explore por `trace_id`.

### 4.4 OTLP genérico (Collector, Jaeger, Grafana Tempo, SigNoz...)

```yaml
messaging:
  telemetry:
    exporter:
      backend: OTLP
      endpoint: http://localhost:4318      # padrão
      api-key: ${TOKEN}                    # opcional → Authorization: Bearer ...
      headers:
        X-Scope-OrgID: payments-team     # ex.: multi-tenant do Tempo
```

Jaeger local para testar:

```bash
docker run --rm -p 16686:16686 -p 4318:4318 jaegertracing/all-in-one:latest
```

Abra `http://localhost:16686`.

### 4.5 Desenvolvimento e testes

```yaml
messaging.telemetry.exporter.backend: LOGGING   # padrão: escreve os spans no log
messaging.telemetry.exporter.backend: NONE      # propaga contexto, mas não exporta
```

### 4.6 Recomendação para produção

Em produção, prefira **aplicação → OpenTelemetry Collector → fornecedor**. O Collector faz buffer, retry, amostragem por cauda e permite trocar de fornecedor sem novo deploy. A biblioteca suporta os dois modelos: direto no fornecedor ou via Collector.

---

## 5. Classe de configuração

Para o que não cabe no YAML, implemente `MessagingTelemetryConfigurer` na sua classe de configuração. Todos os métodos são opcionais e você pode ter mais de um configurer.

```java
@Configuration
@EnableMessagingTelemetry
public class TelemetryConfig implements MessagingTelemetryConfigurer {

    @Override
    public void configureResource(ResourceBuilder resource) {
        resource.put("team", "checkout")
                .put("cloud.region", System.getenv().getOrDefault("AWS_REGION", "local"));
    }

    @Override
    public void configureTracerProvider(SdkTracerProviderBuilder tracerProvider) {
        // ex.: enviar para dois backends ao mesmo tempo durante uma migração
        tracerProvider.addSpanProcessor(BatchSpanProcessor.builder(anotherExporter()).build());
    }

    @Override
    public TextMapPropagator configurePropagator(TextMapPropagator defaultPropagator) {
        // ex.: compatibilidade com serviços legados que usam B3 (dependência opentelemetry-extension-trace-propagators)
        return TextMapPropagator.composite(defaultPropagator, B3Propagator.injectingMultiHeaders());
    }

    @Override
    public void addCarrierFactories(List<MessageCarrierFactory> factories) {
        factories.add(new CorporateCorporateEnvelopeCarrierFactory());
    }

    @Override
    public void addBackendProfiles(List<BackendProfile> profiles) {
        profiles.add(new CorporateOtlpGatewayProfile());
    }
}
```

---

## 6. Referência das anotações

### `@EnableMessagingTelemetry`
Em uma classe `@Configuration`. Ativa a auto-configuração.

### `@TracedProducer` e `@TracedConsumer`

| Atributo | Padrão | Descrição |
|---|---|---|
| `destination` | inferido do argumento | Tópico, fila, exchange ou subscription |
| `system` | `AUTO` | `KAFKA`, `AWS_SQS`, `RABBITMQ`, `GCP_PUBSUB`, `GENERIC`. `AUTO` detecta pelo tipo do argumento |
| `spanName` | `send <destino>` / `process <destino>` | Nome do span |
| `messageId` | inferido do argumento | Id da mensagem |
| `conversationId` | — | Id de correlação (ex.: número do pedido) |
| `attributes` | — | Atributos extras `"chave=valor"` |

**Resolução de valores** (todos os atributos de texto):

1. Placeholders do Spring: `destination = "${app.topics.orders}"`
2. SpEL quando o valor começa com `#`, com acesso aos parâmetros pelo nome: `messageId = "#event.id"`, `attributes = "customer.type=#customer.segment"`
3. Caso contrário, literal.

Atributos gerados em todo span (convenções semânticas OpenTelemetry):

| Atributo | Exemplo |
|---|---|
| `messaging.system` | `kafka`, `aws_sqs`, `rabbitmq`, `gcp_pubsub` |
| `messaging.operation.type` | `publish` / `process` |
| `messaging.operation.name` | `send` / `process` |
| `messaging.destination.name` | `orders-created` |
| `messaging.message.id` | `orders-created-0@1042` |
| `messaging.message.conversation_id` | `ORD-2026-001` |
| `code.function`, `code.namespace` | método e classe anotados |
| `error.type` + evento `exception` | quando o método lança exceção (status `ERROR`) |

---

## 7. Uso programático (sem anotações)

Injete o bean `MessagingTelemetry`:

```java
@Service
public class OrderPublisher {

    private final MessagingTelemetry telemetry;
    private final KafkaTemplate<String, String> kafka;

    public void publish(ProducerRecord<String, String> record) {
        MessageMetadata metadata = MessageMetadata.builder(MessagingSystem.KAFKA, MessagingOperation.PUBLISH)
                .destination(record.topic())
                .messageId(record.key())
                .attribute("order.source", "app")
                .build();

        telemetry.tracePublish(metadata, record, () -> kafka.send(record));
    }
}
```

Outros recursos:

```java
telemetry.currentPropagationHeaders();   // Map com traceparent/tracestate/baggage do contexto atual
telemetry.currentTrace()                 // Optional<TraceIdentity> → traceId e spanId para logs
        .ifPresent(t -> MDC.put("trace_id", t.traceId()));
```

---

## 8. Referência de propriedades

| Propriedade | Padrão | Descrição |
|---|---|---|
| `messaging.telemetry.enabled` | `true` | Liga/desliga sem remover anotações |
| `messaging.telemetry.service-name` | `spring.application.name` | `service.name` |
| `messaging.telemetry.service-version` | — | `service.version` |
| `messaging.telemetry.environment` | — | `deployment.environment.name` |
| `messaging.telemetry.resource-attributes.*` | — | Atributos extras do resource |
| `messaging.telemetry.sampling-ratio` | `1.0` | Amostragem (respeita a decisão do produtor quando há pai) |
| `messaging.telemetry.exporter.backend` | `LOGGING` | `NEW_RELIC`, `DYNATRACE`, `ELASTIC`, `OTLP`, `LOGGING`, `NONE` |
| `messaging.telemetry.exporter.endpoint` | depende do backend | URL base OTLP/HTTP (com ou sem `/v1/traces`) |
| `messaging.telemetry.exporter.api-key` | — | Credencial do fornecedor |
| `messaging.telemetry.exporter.region` | `US` | Região (New Relic) |
| `messaging.telemetry.exporter.headers.*` | — | Headers extras (sobrescrevem os do perfil) |
| `messaging.telemetry.exporter.compression` | `GZIP` | `GZIP` ou `NONE` |
| `messaging.telemetry.exporter.connect-timeout` | `5s` | Timeout de conexão do Feign |
| `messaging.telemetry.exporter.read-timeout` | `10s` | Timeout de leitura do Feign |
| `messaging.telemetry.batch.schedule-delay` | `5s` | Intervalo entre exportações |
| `messaging.telemetry.batch.max-queue-size` | `2048` | Spans em memória (excedente é descartado) |
| `messaging.telemetry.batch.max-export-batch-size` | `512` | Spans por requisição |
| `messaging.telemetry.batch.export-timeout` | `30s` | Timeout de cada exportação |
| `messaging.telemetry.http.enabled` | `true` | Span SERVER para cada requisição dos controllers |
| `messaging.telemetry.http.exclude-paths` | `/actuator/**`, `/favicon.ico` | Paths (padrão Ant) que não são rastreados |

> Nunca coloque a credencial no YAML versionado. Use variável de ambiente ou secret manager: `api-key: ${NEW_RELIC_LICENSE_KEY}`.

---

## 9. Arquitetura

```
com.bribeiro.telemetry.messaging
├── domain                       ← regras e linguagem do domínio (Java puro)
│   ├── model                    MessageMetadata, Destination, MessagingSystem, MessagingOperation, TraceIdentity
│   ├── carrier                  MessageCarrier (porta), MessageCarrierFactory, MessageCarrierResolver
│   ├── tracing                  MessagingTracer, MessagingSpan (portas de saída)
│   ├── backend                  TelemetryBackend, BackendConnection, ExporterEndpoint, BackendProfile
│   ├── http                     HttpRequestMetadata, HttpOutcome, HttpServerTracer, HttpServerSpan (portas)
│   └── exception
├── application                  ← casos de uso
│   ├── usecase                  TraceMessagePublicationUseCase, TraceMessageConsumptionUseCase, TraceHttpRequestUseCase
│   └── MessagingTelemetry       fachada pública
├── infrastructure               ← adaptadores
│   ├── opentelemetry            OpenTelemetryMessagingTracer (implementa MessagingTracer), OpenTelemetryHttpServerTracer
│   ├── carrier                  kafka | rabbitmq | sqs | pubsub | springmessaging | generic
│   └── exporter
│       ├── backend              NewRelic | Dynatrace | Elastic | Otlp BackendProfile
│       ├── feign                OtlpTraceClient (Feign), factory, error decoder
│       ├── otlp                 OtlpProtobufMapper (SpanData → protobuf OTLP)
│       └── FeignOtlpSpanExporter, LoggingSpanExporter, SpanExporterFactory
└── spring                       ← entrada/framework
    ├── annotation               @EnableMessagingTelemetry, @TracedProducer, @TracedConsumer
    ├── aop                      MessagingTelemetryAspect
    ├── web                      HttpServerTracingFilter (span SERVER dos controllers)
    └── config                   AutoConfiguration, Properties, MessagingTelemetryConfigurer
```

**Regra de dependência**: `spring → application → domain ← infrastructure`. O domínio não conhece OpenTelemetry, Spring, Feign nem nenhum broker.

**Conceitos do domínio (DDD)**

- **`MessageCarrier`**: o conceito central que torna a biblioteca agnóstica. Para o domínio, Kafka `Headers`, SQS `MessageAttributes`, AMQP `MessageProperties` e Pub/Sub `attributes` são todos "um lugar chave/valor onde o contexto viaja".
- **`MessageMetadata`**: value object imutável e auto-validado que descreve a mensagem rastreada.
- **`Destination`**: value object que não aceita nome vazio.
- **`BackendProfile`**: estratégia com o conhecimento de cada fornecedor (URL padrão, formato da credencial).

**Fluxo de um `@TracedProducer`**

1. `MessagingTelemetryAspect` intercepta o método.
2. `MessageCarrierResolver` encontra o argumento adaptável (ex.: `ProducerRecord` → `KafkaHeadersCarrier`).
3. Monta o `MessageMetadata` (anotação + SpEL + dados inferidos do carrier).
4. `TraceMessagePublicationUseCase` abre o span via porta `MessagingTracer`.
5. `OpenTelemetryMessagingTracer` cria o span PRODUCER e injeta `traceparent` no carrier.
6. O método executa; exceções são registradas no span e relançadas.
7. O `BatchSpanProcessor` entrega o lote ao `FeignOtlpSpanExporter`, que serializa em protobuf e envia via Feign.

---

## 10. Estendendo a biblioteca

### Novo broker / tipo de mensagem

```java
public class CorporateEnvelopeCarrierFactory implements MessageCarrierFactory {

    @Override
    public boolean supports(Object candidate) {
        return candidate instanceof CorporateEnvelope;
    }

    @Override
    public MessageCarrier create(Object candidate) {
        CorporateEnvelope envelope = (CorporateEnvelope) candidate;
        return new MessageCarrier() {
            public Iterable<String> keys() { return envelope.getHeaders().keySet(); }
            public Optional<String> get(String key) { return Optional.ofNullable(envelope.getHeaders().get(key)); }
            public void put(String key, String value) { envelope.getHeaders().put(key, value); }
            public Optional<Destination> destination() { return Optional.of(Destination.of(envelope.getChannel())); }
        };
    }
}
```

Registre como `@Bean` ou via `MessagingTelemetryConfigurer#addCarrierFactories`.

### Novo backend

```java
public class CorporateOtlpGatewayProfile implements BackendProfile {

    public TelemetryBackend backend() { return TelemetryBackend.OTLP; }   // substitui o perfil OTLP padrão

    public ExporterEndpoint resolve(BackendConnection connection) {
        return new ExporterEndpoint("https://otlp.mycompany.com",
                Map.of("X-Client-Id", connection.apiKeyValue().orElseThrow()));
    }
}
```

### Trocar a implementação de tracing

Declare seu próprio bean `MessagingTracer`; todos os beans da auto-configuração usam `@ConditionalOnMissingBean`.

---

## 11. Boas práticas, limitações e FAQ

**Já uso o Java agent do OpenTelemetry ou o starter oficial. Conflita?**
Se já existir um bean `OpenTelemetry`, a biblioteca o reaproveita e não cria outro SDK (as propriedades `exporter.*` passam a ser ignoradas). Com o Java agent, exponha o global:

```java
@Bean
OpenTelemetry openTelemetry() {
    return GlobalOpenTelemetry.get();
}
```

**Auto-invocação**: como todo aspecto Spring, a anotação não funciona quando o método é chamado de dentro da mesma classe (`this.publish(...)`). Chame através de outro bean.

**Processamento assíncrono**: o contexto vale para a thread do método anotado. Se você dispara outra thread lá dentro, use `Context.current().wrap(runnable)` do OpenTelemetry.

**Mensagens imutáveis**: `PubsubMessage`, `Message` do AWS SDK e `Message<?>` do spring-messaging não recebem headers no envio. Use `telemetry.currentPropagationHeaders()` dentro do método `@TracedProducer`.

**A telemetria pode derrubar minha aplicação?** Não. Falhas de exportação são logadas e o lote é descartado; erros na configuração da anotação (ex.: SpEL inválido) geram um `WARN` e o método executa normalmente sem span.

**Consumo em lote** (`List<ConsumerRecord>`): anote o método que processa cada mensagem individualmente, para que cada uma continue o trace do seu produtor.

**WebFlux**: o rastreamento automático de controllers cobre Spring MVC (servlet). Aplicações reativas (WebFlux) ainda não recebem o span SERVER; as anotações de mensageria continuam funcionando.

**Chamadas HTTP de saída** (RestTemplate, RestClient, WebClient, Feign da aplicação): a biblioteca não injeta o `traceparent` nelas. Para propagar o trace para outro serviço via HTTP, adicione `telemetry.currentPropagationHeaders()` aos headers da requisição.

**Interoperabilidade**: os headers seguem o padrão W3C Trace Context, então funcionam com produtores/consumidores em outras linguagens instrumentados com OpenTelemetry.

---

## Build e testes

```bash
mvn clean verify
```

## Publicação e versionamento (GitHub Actions)

O workflow [`.github/workflows/release.yml`](.github/workflows/release.yml) roda a cada push e pull request:

| Evento | O que acontece |
|---|---|
| Pull request para `main` | Build + testes |
| Push na `main` (merge) | Build + testes → publica nova versão no **GitHub Packages** → cria tag `vX.Y.N` e **GitHub Release** com os jars |
| Push só de `*.md` / `docs/` | Nada é publicado |
| Execução manual (*Run workflow*) | Mesmo fluxo do push na `main` |

**Versão**: `<major>.<minor>.<número da execução>`, por exemplo `1.0.57`. O major/minor vem da propriedade `revision` do `pom.xml`:

```xml
<revision>1.0.0-SNAPSHOT</revision>   <!-- troque para 1.1.0-SNAPSHOT ou 2.0.0-SNAPSHOT para mudar de linha -->
```

Localmente a versão continua `1.0.0-SNAPSHOT`. Para simular a versão do pipeline:

```bash
mvn clean package -Drevision=1.0.99
```

**Configuração necessária no repositório GitHub**: nenhum secret. O workflow usa o `GITHUB_TOKEN` com as permissões `contents: write` e `packages: write` declaradas no próprio arquivo. Se a organização restringir permissões, habilite *Settings → Actions → General → Workflow permissions → Read and write permissions*.

### Consumindo a versão publicada

O GitHub Packages exige autenticação até para leitura. No projeto consumidor:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/OWNER/otel-messaging-telemetry</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.bribeiro.telemetry</groupId>
    <artifactId>messaging-telemetry-spring-boot-starter</artifactId>
    <version>1.0.57</version>
</dependency>
```

E no `~/.m2/settings.xml` (ou no CI do consumidor), um token com escopo `read:packages`:

```xml
<servers>
    <server>
        <id>github</id>
        <username>SEU_USUARIO_GITHUB</username>
        <password>${env.GITHUB_TOKEN}</password>
    </server>
</servers>
```

> Para publicar no **Maven Central** no lugar do GitHub Packages, é preciso uma conta no Sonatype Central Portal, chave GPG para assinar os artefatos e os jars de sources/javadoc. A estrutura do pipeline continua a mesma; muda o `distributionManagement` e entram os secrets de assinatura.

Os testes cobrem regras do domínio, perfis de cada fornecedor, propagação produtor → consumidor, mapeamento OTLP protobuf, exportação Feign contra um servidor HTTP local (headers, gzip, retry) e a integração Spring completa (anotações, SpEL, placeholders, configurer e ativação condicional).
