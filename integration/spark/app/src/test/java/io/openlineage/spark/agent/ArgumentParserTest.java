/*
/* Copyright 2018-2024 contributors to the OpenLineage project
/* SPDX-License-Identifier: Apache-2.0
*/

package io.openlineage.spark.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openlineage.client.OpenLineageYaml;
import io.openlineage.client.circuitBreaker.StaticCircuitBreakerConfig;
import io.openlineage.client.transports.ApiKeyTokenProvider;
import io.openlineage.client.transports.ConsoleConfig;
import io.openlineage.client.transports.HttpConfig;
import io.openlineage.client.transports.KafkaConfig;
import io.openlineage.client.transports.KinesisConfig;
import org.apache.spark.SparkConf;
import org.junit.jupiter.api.Test;

class ArgumentParserTest {

  private static final String NS_NAME = "ns_name";
  private static final String JOB_NAMESPACE = "job_namespace";
  private static final String JOB_NAME = "job_name";
  private static final String URL = "http://localhost:5000";
  private static final String RUN_ID = "ea445b5c-22eb-457a-8007-01c7c52b6e54";
  private static final String APP_NAME = "test";
  private static final String DISABLED_FACETS = "[facet1;facet2]";
  private static final String ENDPOINT = "api/v1/lineage";
  private static final String AUTH_TYPE = "api_key";
  private static final String API_KEY = "random_token";

  @Test
  void testDefaults() {
    ArgumentParser argumentParser = ArgumentParser.parse(new SparkConf());
    assertEquals(
        ArgumentParser.DEFAULT_DISABLED_FACETS.substring(
                1, ArgumentParser.DEFAULT_DISABLED_FACETS.length() - 1)
            .split(";")[0],
        argumentParser.getOpenLineageYaml().getFacetsConfig().getDisabledFacets()[0]);
    assert (argumentParser.getOpenLineageYaml().getTransportConfig() instanceof ConsoleConfig);
  }

  @Test
  void testTransportTypes() {
    ArgumentParser argumentParserConsole =
        ArgumentParser.parse(
            new SparkConf().set(ArgumentParser.SPARK_CONF_TRANSPORT_TYPE, "console"));
    ArgumentParser argumentParserHttp =
        ArgumentParser.parse(new SparkConf().set(ArgumentParser.SPARK_CONF_TRANSPORT_TYPE, "http"));
    ArgumentParser argumentParserKafka =
        ArgumentParser.parse(
            new SparkConf().set(ArgumentParser.SPARK_CONF_TRANSPORT_TYPE, "kafka"));
    ArgumentParser argumentParserKinesis =
        ArgumentParser.parse(
            new SparkConf().set(ArgumentParser.SPARK_CONF_TRANSPORT_TYPE, "kinesis"));

    assert (argumentParserConsole.getOpenLineageYaml().getTransportConfig()
        instanceof ConsoleConfig);
    assert (argumentParserHttp.getOpenLineageYaml().getTransportConfig() instanceof HttpConfig);
    assert (argumentParserKafka.getOpenLineageYaml().getTransportConfig() instanceof KafkaConfig);
    assert (argumentParserKinesis.getOpenLineageYaml().getTransportConfig()
        instanceof KinesisConfig);
  }

  @Test
  void testLoadingSparkConfig() {
    SparkConf sparkConf =
        new SparkConf()
            .set(ArgumentParser.SPARK_CONF_NAMESPACE, NS_NAME)
            .set(ArgumentParser.SPARK_CONF_PARENT_JOB_NAMESPACE, JOB_NAMESPACE)
            .set(ArgumentParser.SPARK_CONF_PARENT_JOB_NAME, JOB_NAME)
            .set(ArgumentParser.SPARK_CONF_PARENT_RUN_ID, RUN_ID)
            .set(ArgumentParser.SPARK_CONF_APP_NAME, APP_NAME);

    ArgumentParser argumentParser = ArgumentParser.parse(sparkConf);
    assertEquals(NS_NAME, argumentParser.getNamespace());
    assertEquals(JOB_NAMESPACE, argumentParser.getParentJobNamespace());
    assertEquals(JOB_NAME, argumentParser.getParentJobName());
    assertEquals(RUN_ID, argumentParser.getParentRunId());
    assertEquals(APP_NAME, argumentParser.getOverriddenAppName());
  }

  @Test
  void testConfToHttpConfig() {
    SparkConf sparkConf =
        new SparkConf()
            .set("spark.openlineage.transport.type", "http")
            .set("spark.openlineage.transport.url", URL)
            .set("spark.openlineage.transport.endpoint", ENDPOINT)
            .set("spark.openlineage.transport.auth.type", AUTH_TYPE)
            .set("spark.openlineage.transport.auth.apiKey", API_KEY)
            .set("spark.openlineage.transport.timeout", "5000")
            .set("spark.openlineage.facets.disabled", DISABLED_FACETS)
            .set("spark.openlineage.transport.urlParams.test1", "test1")
            .set("spark.openlineage.transport.urlParams.test2", "test2")
            .set("spark.openlineage.transport.headers.testHeader1", "test1")
            .set("spark.openlineage.transport.headers.testHeader2", "test2");

    OpenLineageYaml openLineageYaml = ArgumentParser.extractOpenlineageConfFromSparkConf(sparkConf);
    HttpConfig transportConfig = (HttpConfig) openLineageYaml.getTransportConfig();
    assertEquals(URL, transportConfig.getUrl().toString());
    assertEquals(ENDPOINT, transportConfig.getEndpoint());
    assert (transportConfig.getAuth() != null);
    assert (transportConfig.getAuth() instanceof ApiKeyTokenProvider);
    assertEquals("Bearer random_token", transportConfig.getAuth().getToken());
    assertEquals(5000, transportConfig.getTimeout());
    assertEquals("test1", transportConfig.getHeaders().get("testHeader1"));
    assertEquals("test2", transportConfig.getHeaders().get("testHeader2"));
  }

  @Test
  void testConfToKafkaConfig() {
    SparkConf sparkConf =
        new SparkConf()
            .set("spark.openlineage.transport.type", "kafka")
            .set("spark.openlineage.transport.topicName", "test")
            .set("spark.openlineage.transport.messageKey", "explicit-key")
            .set("spark.openlineage.transport.properties.test1", "test1")
            .set("spark.openlineage.transport.properties.test2", "test2");
    OpenLineageYaml openLineageYaml = ArgumentParser.extractOpenlineageConfFromSparkConf(sparkConf);
    KafkaConfig transportConfig = (KafkaConfig) openLineageYaml.getTransportConfig();
    assertEquals("test", transportConfig.getTopicName());
    assertEquals("explicit-key", transportConfig.getMessageKey());
    assertEquals("test1", transportConfig.getProperties().get("test1"));
    assertEquals("test2", transportConfig.getProperties().get("test2"));
  }

  @Test
  void testConfToKafkaConfigLegacyLocalServerId() {
    SparkConf sparkConf =
        new SparkConf()
            .set("spark.openlineage.transport.type", "kafka")
            .set("spark.openlineage.transport.topicName", "test")
            .set("spark.openlineage.transport.localServerId", "explicit-key")
            .set("spark.openlineage.transport.properties.test1", "test1")
            .set("spark.openlineage.transport.properties.test2", "test2");
    OpenLineageYaml openLineageYaml = ArgumentParser.extractOpenlineageConfFromSparkConf(sparkConf);
    KafkaConfig transportConfig = (KafkaConfig) openLineageYaml.getTransportConfig();
    assertEquals("test", transportConfig.getTopicName());
    // Legacy LocalServerId should be mapped to messageKey
    assertEquals("explicit-key", transportConfig.getMessageKey());
    assertEquals("test1", transportConfig.getProperties().get("test1"));
    assertEquals("test2", transportConfig.getProperties().get("test2"));
  }

  @Test
  void testConfToKinesisConfig() {
    SparkConf sparkConf =
        new SparkConf()
            .set("spark.openlineage.transport.type", "kinesis")
            .set("spark.openlineage.transport.streamName", "test")
            .set("spark.openlineage.transport.region", "test")
            .set("spark.openlineage.transport.roleArn", "test")
            .set("spark.openlineage.transport.properties.test1", "test1")
            .set("spark.openlineage.transport.properties.test2", "test2");
    OpenLineageYaml openLineageYaml = ArgumentParser.extractOpenlineageConfFromSparkConf(sparkConf);
    KinesisConfig transportConfig = (KinesisConfig) openLineageYaml.getTransportConfig();
    assertEquals("test", transportConfig.getStreamName());
    assertEquals("test", transportConfig.getRegion());
    assertEquals("test", transportConfig.getRoleArn());
    assertEquals("test1", transportConfig.getProperties().get("test1"));
    assertEquals("test2", transportConfig.getProperties().get("test2"));
  }

  @Test
  void testCircuitBreakerConfig() {
    SparkConf sparkConf =
        new SparkConf()
            .set("spark.openlineage.circuitBreaker.type", "static")
            .set("spark.openlineage.circuitBreaker.valuesReturned", "false,true");

    OpenLineageYaml openLineageYaml = ArgumentParser.extractOpenlineageConfFromSparkConf(sparkConf);
    assertThat(openLineageYaml.getCircuitBreaker()).isInstanceOf(StaticCircuitBreakerConfig.class);
    assertThat(
            ((StaticCircuitBreakerConfig) openLineageYaml.getCircuitBreaker()).getValuesReturned())
        .isEqualTo("false,true");
  }
}
