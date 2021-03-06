/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.metrics.collector;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.dataflow.metrics.collector.endpoint.MetricsCollectorEndpoint;
import org.springframework.cloud.dataflow.metrics.collector.model.Application;
import org.springframework.cloud.dataflow.metrics.collector.model.ApplicationMetrics;
import org.springframework.cloud.dataflow.metrics.collector.model.Instance;
import org.springframework.cloud.dataflow.metrics.collector.model.Metric;
import org.springframework.cloud.dataflow.metrics.collector.model.MicrometerMetric;
import org.springframework.cloud.dataflow.metrics.collector.model.StreamMetrics;
import org.springframework.cloud.dataflow.metrics.collector.services.ApplicationMetricsService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @author Vinicius Carvalho
 * @author Christian Tzolov
 */
public class MetricsAggregatorTests extends BaseCacheTests {

	@Before
	public void setup() {
		HttpServletRequest mockRequest = new MockHttpServletRequest();
		ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(mockRequest);
		RequestContextHolder.setRequestAttributes(servletRequestAttributes);
	}

	@After
	public void teardown() {
		RequestContextHolder.resetRequestAttributes();
	}

	private static final ObjectMapper mapper = new ObjectMapper();

	@Test
	public void includeOneMetric() throws JsonProcessingException {
		Long now = System.currentTimeMillis();
		Metric<Double> inputSendCount = new Metric<>("integration.channel.input.sendCount", 10.0, new Date(now));
		ApplicationMetrics<Metric<Double>> app = createApplicationMetrics("httpIngest", "http", "foo", 0);
		app.getMetrics().add(inputSendCount);
		Cache<String, LinkedList<ApplicationMetrics<Metric<Double>>>> rawCache = Caffeine.newBuilder().build();
		ApplicationMetricsService service = new ApplicationMetricsService(rawCache);
		MetricsAggregator aggregator = new MetricsAggregator(service);
		MetricsCollectorEndpoint endpoint = new MetricsCollectorEndpoint(service);

		aggregator.receive(mapper.writeValueAsString(app));

		Assert.assertEquals(1, rawCache.estimatedSize());
		StreamMetrics streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		Application application = streamMetrics.getApplications().get(0);
		Assert.assertNotNull(streamMetrics);
		Assert.assertEquals("http", application.getName());
		Instance instance = application.getInstances().get(0);
		Assert.assertEquals(app.getName(), instance.getKey());
		Assert.assertEquals("foo", instance.getGuid());
		Metric<Double> computed = instance.getMetrics().stream().filter(metric -> metric.getName().equals("integration.channel.input.send.mean")).findFirst().get();
		Assert.assertEquals(0, computed.getValue(), 0.0);
	}

	@Test
	public void incrementMetric() throws Exception {
		Long now = System.currentTimeMillis();
		Metric<Double> inputSendCount = new Metric<Double>("integration.channel.input.sendCount", 10.0, new Date(now));
		ApplicationMetrics<Metric<Double>> app = createApplicationMetrics("httpIngest", "http", "foo", 0);
		app.getMetrics().add(inputSendCount);
		Cache<String, LinkedList<ApplicationMetrics<Metric<Double>>>> rawCache = Caffeine.newBuilder().build();
		ApplicationMetricsService service = new ApplicationMetricsService(rawCache);
		MetricsAggregator aggregator = new MetricsAggregator(service);
		MetricsCollectorEndpoint endpoint = new MetricsCollectorEndpoint(service);

		aggregator.receive(mapper.writeValueAsString(app));

		Assert.assertEquals(1, rawCache.estimatedSize());
		StreamMetrics streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		Application application = streamMetrics.getApplications().get(0);
		Assert.assertNotNull(streamMetrics);
		Assert.assertEquals("http", application.getName());
		Instance instance = application.getInstances().get(0);
		Assert.assertEquals("foo", instance.getGuid());

		Metric<Double> inputSendCount2 = new Metric<>("integration.channel.input.sendCount", 110.0, new Date(now + 5000));
		ApplicationMetrics<Metric<Double>> app2 = createApplicationMetrics("httpIngest", "http", "foo", 0);
		app2.getMetrics().add(inputSendCount2);
		aggregator.receive(mapper.writeValueAsString(app2));

		Assert.assertEquals(1, rawCache.estimatedSize());
		streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		application = streamMetrics.getApplications().get(0);
		Assert.assertNotNull(streamMetrics);
		Assert.assertEquals("http", application.getName());
		instance = application.getInstances().get(0);
		Assert.assertEquals("foo", instance.getGuid());
		Metric<Double> computed = instance.getMetrics().stream().filter(metric -> metric.getName().equals("integration.channel.input.send.mean")).findFirst().get();
		Assert.assertEquals(20.0, computed.getValue(), 0.0);
	}

	@Test
	public void addInstance() throws Exception {
		ApplicationMetrics<Metric<Double>> app = createApplicationMetrics("httpIngest", "http", "foo", 0);
		Cache<String, LinkedList<ApplicationMetrics<Metric<Double>>>> rawCache = Caffeine.newBuilder().build();
		ApplicationMetricsService service = new ApplicationMetricsService(rawCache);
		MetricsAggregator aggregator = new MetricsAggregator(service);
		MetricsCollectorEndpoint endpoint = new MetricsCollectorEndpoint(service);

		aggregator.receive(mapper.writeValueAsString(app));

		Assert.assertEquals(1, rawCache.estimatedSize());
		StreamMetrics streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		Application application = streamMetrics.getApplications().get(0);
		Assert.assertNotNull(streamMetrics);
		Assert.assertEquals("http", application.getName());
		Instance instance = application.getInstances().get(0);
		Assert.assertEquals("foo", instance.getGuid());
		ApplicationMetrics<Metric<Double>> app2 = createApplicationMetrics("httpIngest", "http", "bar", 1);
		aggregator.receive(mapper.writeValueAsString(app2));

		Assert.assertEquals(2, rawCache.estimatedSize());
		streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		application = streamMetrics.getApplications().get(0);
		Assert.assertNotNull(streamMetrics);
		Assert.assertEquals("http", application.getName());
		Assert.assertEquals(1, endpoint.fetchMetrics("").getBody().getContent().size());
		Assert.assertEquals(2, application.getInstances().size());
		Instance i1 = application.getInstances().get(0);
		Assert.assertNotNull(i1);
		Instance i2 = application.getInstances().get(1);
		Assert.assertNotNull(i2);
		Assert.assertNotEquals(i1.getIndex(), i2.getIndex());
	}

	@Test
	public void removeInstance() throws Exception {
		ApplicationMetrics app = createApplicationMetrics("httpIngest", "http", "foo", 0);
		ApplicationMetrics app2 = createApplicationMetrics("httpIngest", "http", "bar", 1);
		Cache<String, LinkedList<ApplicationMetrics<Metric<Double>>>> rawCache = Caffeine.newBuilder().build();
		ApplicationMetricsService service = new ApplicationMetricsService(rawCache);
		MetricsAggregator aggregator = new MetricsAggregator(service);
		MetricsCollectorEndpoint endpoint = new MetricsCollectorEndpoint(service);

		aggregator.receive(mapper.writeValueAsString(app));
		aggregator.receive(mapper.writeValueAsString(app2));

		StreamMetrics streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		Application application = streamMetrics.getApplications().get(0);
		Instance instance = application.getInstances().get(0);

		Assert.assertEquals(2, rawCache.estimatedSize());
		streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		application = streamMetrics.getApplications().get(0);
		Assert.assertNotNull(streamMetrics);
		Assert.assertEquals("http", application.getName());
		Assert.assertEquals(2, application.getInstances().size());
		rawCache.invalidate("httpIngest.http.bar");
		Thread.sleep(1000);
		Assert.assertEquals(1, rawCache.estimatedSize());
		streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		application = streamMetrics.getApplications().get(0);

		Assert.assertEquals(1, application.getInstances().size());
	}

	@Test
	public void addApplication() throws Exception {
		Cache<String, LinkedList<ApplicationMetrics<Metric<Double>>>> rawCache = Caffeine.newBuilder().build();
		ApplicationMetricsService service = new ApplicationMetricsService(rawCache);
		MetricsAggregator aggregator = new MetricsAggregator(service);
		MetricsCollectorEndpoint endpoint = new MetricsCollectorEndpoint(service);


		ApplicationMetrics app = createApplicationMetrics("httpIngest", "http", "foo", 0);
		ApplicationMetrics app2 = createApplicationMetrics("httpIngest", "log", "bar", 0);

		aggregator.receive(mapper.writeValueAsString(app));
		aggregator.receive(mapper.writeValueAsString(app2));

		Assert.assertEquals(2, rawCache.estimatedSize());
		StreamMetrics streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		Assert.assertEquals(2, streamMetrics.getApplications().size());
	}

	@Test
	public void addStream() throws Exception {
		Cache<String, LinkedList<ApplicationMetrics<Metric<Double>>>> rawCache = Caffeine.newBuilder().build();
		ApplicationMetricsService service = new ApplicationMetricsService(rawCache);
		MetricsAggregator aggregator = new MetricsAggregator(service);
		MetricsCollectorEndpoint endpoint = new MetricsCollectorEndpoint(service);


		ApplicationMetrics app = createApplicationMetrics("httpIngest", "http", "foo", 0);
		ApplicationMetrics app2 = createApplicationMetrics("woodchuck", "time", "bar", 0);

		aggregator.receive(mapper.writeValueAsString(app));
		aggregator.receive(mapper.writeValueAsString(app2));

		Assert.assertEquals(2, endpoint.fetchMetrics("").getBody().getContent().size());
	}

	@Test
	public void filterByStream() throws Exception {
		Cache<String, LinkedList<ApplicationMetrics<Metric<Double>>>> rawCache = Caffeine.newBuilder().build();
		ApplicationMetricsService service = new ApplicationMetricsService(rawCache);
		MetricsAggregator aggregator = new MetricsAggregator(service);
		MetricsCollectorEndpoint endpoint = new MetricsCollectorEndpoint(service);

		ApplicationMetrics app = createApplicationMetrics("httpIngest", "http", "foo", 0);
		ApplicationMetrics app1 = createApplicationMetrics("httpIngest", "http", "foobar", 1);
		ApplicationMetrics app2 = createApplicationMetrics("woodchuck", "time", "bar", 0);
		ApplicationMetrics app3 = createApplicationMetrics("twitter", "twitterstream", "bar", 0);

		aggregator.receive(mapper.writeValueAsString(app));
		aggregator.receive(mapper.writeValueAsString(app1));
		aggregator.receive(mapper.writeValueAsString(app2));
		aggregator.receive(mapper.writeValueAsString(app3));

		Assert.assertEquals(2, endpoint.fetchMetrics("httpIngest,woodchuck").getBody().getContent().size());
	}

	@Test
	public void filterUsingInvalidDelimiter() throws Exception {
		Cache<String, LinkedList<ApplicationMetrics<Metric<Double>>>> rawCache = Caffeine.newBuilder().build();
		ApplicationMetricsService service = new ApplicationMetricsService(rawCache);
		MetricsAggregator aggregator = new MetricsAggregator(service);
		MetricsCollectorEndpoint endpoint = new MetricsCollectorEndpoint(service);

		ApplicationMetrics app = createApplicationMetrics("httpIngest", "http", "foo", 0);
		ApplicationMetrics app2 = createApplicationMetrics("woodchuck", "time", "bar", 0);
		ApplicationMetrics app3 = createApplicationMetrics("twitter", "twitterstream", "bar", 0);

		aggregator.receive(mapper.writeValueAsString(app));
		aggregator.receive(mapper.writeValueAsString(app2));
		aggregator.receive(mapper.writeValueAsString(app3));

		Assert.assertEquals(0, endpoint.fetchMetrics("httpIngest;woodchuck").getBody().getContent().size());
	}

	@Test
	public void aggregateMetricsTest() throws Exception {
		Cache<String, LinkedList<ApplicationMetrics<Metric<Double>>>> rawCache = Caffeine.newBuilder().build();
		ApplicationMetricsService service = new ApplicationMetricsService(rawCache);
		MetricsAggregator aggregator = new MetricsAggregator(service);
		MetricsCollectorEndpoint endpoint = new MetricsCollectorEndpoint(service);

		Long now = System.currentTimeMillis();
		Metric<Double> inputSendCount = new Metric<>("integration.channel.input.sendCount", 0.0, new Date(now));
		ApplicationMetrics app = createApplicationMetrics("httpIngest", "http", "foo", 0);
		app.getMetrics().add(inputSendCount);

		ApplicationMetrics app2 = createApplicationMetrics("httpIngest", "http", "bar", 1);
		app2.getMetrics().add(inputSendCount);

		aggregator.receive(mapper.writeValueAsString(app));
		aggregator.receive(mapper.writeValueAsString(app2));

		Metric<Double> inputSendCount2 = new Metric<>("integration.channel.input.sendCount", 10.0, new Date(now + 5000));

		ApplicationMetrics app3 = createApplicationMetrics("httpIngest", "http", "foo", 0);
		app3.getMetrics().add(inputSendCount2);

		ApplicationMetrics app4 = createApplicationMetrics("httpIngest", "http", "bar", 1);
		app4.getMetrics().add(inputSendCount2);

		aggregator.receive(mapper.writeValueAsString(app3));
		aggregator.receive(mapper.writeValueAsString(app4));

		StreamMetrics streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		Metric<Double> aggregate = streamMetrics.getApplications().get(0).getAggregateMetrics().iterator().next();
		Assert.assertEquals(4.0, aggregate.getValue(), 0.0);
	}

	@Test
	public void poisonMetricTest() throws Exception {
		Cache<String, LinkedList<ApplicationMetrics<Metric<Double>>>> rawCache = Caffeine.newBuilder().build();
		ApplicationMetricsService service = new ApplicationMetricsService(rawCache);
		MetricsAggregator aggregator = new MetricsAggregator(service);
		MetricsCollectorEndpoint endpoint = new MetricsCollectorEndpoint(service);

		ApplicationMetrics app = createApplicationMetrics("httpIngest", "http", "foo", 0);
		aggregator.receive(mapper.writeValueAsString(app));
		StreamMetrics streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();

		Application application = streamMetrics.getApplications().get(0);
		Assert.assertNotNull(streamMetrics);
		Assert.assertEquals("http", application.getName());

		ApplicationMetrics app2 = createApplicationMetrics("httpIngest", "log", "foo", 0);
		app2.setProperties(new HashMap<>());

		aggregator.receive(mapper.writeValueAsString(app2));
		streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		Assert.assertNotNull(streamMetrics);
	}

	@Test
	public void addMetric2() throws JsonProcessingException {
		Long now = System.currentTimeMillis();
		MicrometerMetric<Number> inputSendCount = createMetric2("spring.integration.send", "input", 10.0, new Date(now));
		ApplicationMetrics<MicrometerMetric<Number>> app = createApplicationMetrics2("httpIngest", "http", "foo", 0);
		app.getMetrics().add(inputSendCount);
		Cache<String, LinkedList<ApplicationMetrics<Metric<Double>>>> rawCache = Caffeine.newBuilder().build();
		ApplicationMetricsService service = new ApplicationMetricsService(rawCache);
		MetricsAggregator aggregator = new MetricsAggregator(service);
		MetricsCollectorEndpoint endpoint = new MetricsCollectorEndpoint(service);

		aggregator.receive(mapper.writeValueAsString(app));

		Assert.assertEquals(1, rawCache.estimatedSize());
		StreamMetrics streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		Application application = streamMetrics.getApplications().get(0);
		Assert.assertNotNull(streamMetrics);
		Assert.assertEquals("http", application.getName());
		Instance instance = application.getInstances().get(0);
		Assert.assertEquals(app.getName(), instance.getKey());
		Assert.assertEquals("foo", instance.getGuid());
		Metric<Double> computed = instance.getMetrics().stream().filter(metric -> metric.getName().equals("integration.channel.input.send.mean")).findFirst().get();
		Assert.assertEquals(0, computed.getValue(), 10.0);
	}

	@Test
	public void aggregateMixedMetricsTest() throws Exception {
		Cache<String, LinkedList<ApplicationMetrics<Metric<Double>>>> rawCache = Caffeine.newBuilder().build();
		ApplicationMetricsService service = new ApplicationMetricsService(rawCache);
		MetricsAggregator aggregator = new MetricsAggregator(service);
		MetricsCollectorEndpoint endpoint = new MetricsCollectorEndpoint(service);

		Long now = System.currentTimeMillis();
		Metric<Double> inputSendCount = new Metric<>("integration.channel.input.sendCount", 0.0, new Date(now));
		ApplicationMetrics app = createApplicationMetrics("httpIngest", "time", "foo", 0);
		app.getMetrics().add(inputSendCount);

		ApplicationMetrics app2 = createApplicationMetrics("httpIngest", "time", "bar", 1);
		app2.getMetrics().add(inputSendCount);

		aggregator.receive(mapper.writeValueAsString(app));
		aggregator.receive(mapper.writeValueAsString(app2));


		ApplicationMetrics app3 = createApplicationMetrics("httpIngest", "time", "foo", 0);
		app3.getMetrics().add(new Metric<>("integration.channel.input.sendCount", 10.0, new Date(now + 5000)));

		ApplicationMetrics app4 = createApplicationMetrics2("httpIngest", "log2", "bar", 0);
		app4.getMetrics().add(createMetric2("spring.integration.send", "output", 10.0, new Date(now + 5000)));

		ApplicationMetrics app5 = createApplicationMetrics2("httpIngest", "log2", "bar2", 1);
		app5.getMetrics().add(createMetric2("spring.integration.send", "output", 20.0, new Date(now + 2 * 5000)));

		aggregator.receive(mapper.writeValueAsString(app3));
		aggregator.receive(mapper.writeValueAsString(app4));
		aggregator.receive(mapper.writeValueAsString(app5));

		StreamMetrics streamMetrics = endpoint.fetchMetrics("").getBody().iterator().next();
		Metric<Double> aggregate = streamMetrics.getApplications().get(0).getAggregateMetrics().iterator().next();
		Assert.assertEquals(2.0, aggregate.getValue(), 0.0);
		Metric<Double> aggregate2 = streamMetrics.getApplications().get(1).getAggregateMetrics().iterator().next();
		Assert.assertEquals(30.0, aggregate2.getValue(), 0.0);
	}

	private ApplicationMetrics<Metric<Double>> createApplicationMetrics(String streamName, String applicationName, String appGuid, Integer index) {
		return createApplicationMetrics(streamName, applicationName, appGuid, index, new LinkedList<>());
	}

	private ApplicationMetrics<MicrometerMetric<Number>> createApplicationMetrics2(String streamName, String applicationName, String appGuid, Integer index) {
		return createApplicationMetrics2(streamName, applicationName, appGuid, index, new LinkedList<>());
	}

	private ApplicationMetrics<Metric<Double>> createApplicationMetrics(String streamName, String applicationName, String appGuid, Integer index,
			List<Metric<Double>> metrics) {

		ApplicationMetrics applicationMetrics = new ApplicationMetrics(
				streamName + "." + applicationName + "." + appGuid, new LinkedList<>());
		Map<String, Object> properties = new HashMap<>();
		properties.put(ApplicationMetrics.STREAM_NAME, streamName);
		properties.put(ApplicationMetrics.APPLICATION_NAME, applicationName);
		properties.put(ApplicationMetrics.APPLICATION_GUID, appGuid);
		properties.put(ApplicationMetrics.INSTANCE_INDEX, index.toString());
		properties.put(ApplicationMetrics.STREAM_METRICS_VERSION, ApplicationMetrics.METRICS_VERSION_1);
		applicationMetrics.setProperties(properties);
		applicationMetrics.setMetrics(metrics);
		return applicationMetrics;
	}

	private ApplicationMetrics<MicrometerMetric<Number>> createApplicationMetrics2(String streamName, String applicationName, String appGuid, Integer index,
			List<MicrometerMetric<Number>> metrics) {

		ApplicationMetrics applicationMetrics = new ApplicationMetrics(
				streamName + "." + applicationName + "." + appGuid, new LinkedList<>());
		Map<String, Object> properties = new HashMap<>();
		properties.put(ApplicationMetrics.STREAM_NAME, streamName);
		properties.put(ApplicationMetrics.APPLICATION_NAME, applicationName);
		properties.put(ApplicationMetrics.APPLICATION_GUID, appGuid);
		properties.put(ApplicationMetrics.INSTANCE_INDEX, index.toString());
		properties.put(ApplicationMetrics.STREAM_METRICS_VERSION, ApplicationMetrics.METRICS_VERSION_2);
		applicationMetrics.setProperties(properties);
		applicationMetrics.setMetrics(metrics);
		return applicationMetrics;
	}

	private MicrometerMetric<Number> createMetric2(String name, String channelName, Double value, Date date) {
		MicrometerMetric<Number> sample = new MicrometerMetric<>();
		sample.setId(new MicrometerMetric.Id());
		sample.getId().setTags(new ArrayList<>());
		sample.getId().setName(name);
		sample.setTimestamp(date);
		sample.setCount(value);

		sample.getId().getTags().add(tag("name", channelName));
		sample.getId().getTags().add(tag("type", "channel"));
		sample.getId().getTags().add(tag("result", "success"));

		return sample;
	}

	private MicrometerMetric.Tag tag(String name, String value) {
		MicrometerMetric.Tag tag = new MicrometerMetric.Tag();
		tag.setKey(name);
		tag.setValue(value);
		return tag;
	}
}
