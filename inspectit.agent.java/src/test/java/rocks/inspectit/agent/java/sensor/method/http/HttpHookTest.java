package rocks.inspectit.agent.java.sensor.method.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.lang.management.ThreadMXBean;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.collections.MapUtils;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import rocks.inspectit.agent.java.AbstractLogSupport;
import rocks.inspectit.agent.java.config.impl.RegisteredSensorConfig;
import rocks.inspectit.agent.java.core.ICoreService;
import rocks.inspectit.agent.java.core.IPlatformManager;
import rocks.inspectit.agent.java.util.Timer;
import rocks.inspectit.shared.all.communication.MethodSensorData;
import rocks.inspectit.shared.all.communication.data.HttpTimerData;

/**
 * Tests the {@link HttpHook} class.
 *
 */
@SuppressWarnings("PMD")
public class HttpHookTest extends AbstractLogSupport {

	@Mock
	private Timer timer;

	@Mock
	private IPlatformManager platformManager;

	@Mock
	private ICoreService coreService;

	@Mock
	private RegisteredSensorConfig registeredSensorConfig;

	@Mock
	private ThreadMXBean threadMXBean;

	@Mock
	private Object result;

	@Mock
	private HttpServlet servlet;

	@Mock
	private HttpServletRequest httpServletRequest;

	@Mock
	private HttpServletResponse httpServletResponse;

	@Mock
	private ServletRequest servletRequest;

	@Mock
	private ServletRequest servletResponse;

	@Mock
	private HttpSession session;

	private HttpHook httpHook;

	private final long platformId = 1L;
	private final long methodId = 1L;
	private final long sensorTypeId = 3L;

	@BeforeMethod
	public void initTestClass() {
		Map<String, String> settings = new HashMap<String, String>();
		settings.put("sessioncapture", "false");
		when(threadMXBean.isThreadCpuTimeEnabled()).thenReturn(true);
		when(threadMXBean.isThreadCpuTimeSupported()).thenReturn(true);

		Map<String, Object> map = new HashMap<String, Object>();
		MapUtils.putAll(map, new String[][] { { "sessioncapture", "true" } });
		httpHook = new HttpHook(timer, platformManager, map, threadMXBean);
	}

	@Test
	public void oneRecordThatIsHttpWithoutReadingData() {
		Double firstTimerValue = 1000.453d;
		Double secondTimerValue = 1323.675d;

		Long firstCpuTimerValue = 5000L;
		Long secondCpuTimerValue = 6872L;

		HttpTimerData data = new HttpTimerData(null, platformId, sensorTypeId, methodId);

		when(timer.getCurrentTime()).thenReturn(firstTimerValue).thenReturn(secondTimerValue);
		when(threadMXBean.getCurrentThreadCpuTime()).thenReturn(firstCpuTimerValue).thenReturn(secondCpuTimerValue);
		when(platformManager.getPlatformId()).thenReturn(platformId);

		Object[] parameters = new Object[] { httpServletRequest, httpServletResponse };

		httpHook.beforeBody(methodId, sensorTypeId, servlet, parameters, registeredSensorConfig);

		httpHook.firstAfterBody(methodId, sensorTypeId, servlet, parameters, result, false, registeredSensorConfig);

		httpHook.secondAfterBody(coreService, methodId, sensorTypeId, servlet, parameters, result, false, registeredSensorConfig);

		verify(coreService).addMethodSensorData(eq(sensorTypeId), eq(methodId), eq(String.valueOf(firstTimerValue)), argThat(new HttpTimerDataVerifier(data)));
		verifyZeroInteractions(result);
	}

	@Test
	public void oneRecordThatIsHttpCharting() {
		Double firstTimerValue = 1000.453d;
		Double secondTimerValue = 1323.675d;

		Long firstCpuTimerValue = 5000L;
		Long secondCpuTimerValue = 6872L;

		HttpTimerData data = new HttpTimerData(null, platformId, sensorTypeId, methodId);
		data.setCharting(true);

		when(timer.getCurrentTime()).thenReturn(firstTimerValue).thenReturn(secondTimerValue);
		when(threadMXBean.getCurrentThreadCpuTime()).thenReturn(firstCpuTimerValue).thenReturn(secondCpuTimerValue);
		when(platformManager.getPlatformId()).thenReturn(platformId);
		when(registeredSensorConfig.getSettings()).thenReturn(Collections.<String, Object> singletonMap("charting", Boolean.TRUE));

		Object[] parameters = new Object[] { httpServletRequest, httpServletResponse };

		httpHook.beforeBody(methodId, sensorTypeId, servlet, parameters, registeredSensorConfig);

		httpHook.firstAfterBody(methodId, sensorTypeId, servlet, parameters, result, false, registeredSensorConfig);

		httpHook.secondAfterBody(coreService, methodId, sensorTypeId, servlet, parameters, result, false, registeredSensorConfig);

		verify(coreService).addMethodSensorData(eq(sensorTypeId), eq(methodId), eq(String.valueOf(firstTimerValue)), argThat(new HttpTimerDataVerifier(data)));
		verifyZeroInteractions(result);
	}

	@Test
	public void oneRecordThatIsHttpReadingDataNoCropping() {
		final String uri = "URI";
		final String method = "GET";

		final String param1 = "p1";
		final String param2 = "p2";
		final String param1VReal = "value1";
		final String param2VReal1 = "value5";
		final String param2VReal2 = "value6";
		final String[] param1V = new String[] { param1VReal };
		final String[] param2V = new String[] { param2VReal1, param2VReal2 };
		final Map<String, String[]> parameterMap = new HashMap<String, String[]>();
		MapUtils.putAll(parameterMap, new Object[][] { { param1, param1V }, { param2, param2V } });

		final String att1 = "a1";
		final String att2 = "a2";
		final String att1Value = "aValue1";
		final String att2Value = "aValue2";
		final Vector<String> attributesList = new Vector<String>();
		Collections.addAll(attributesList, att1, att2);
		final Enumeration<String> attributes = attributesList.elements();

		final String h1 = "h1";
		final String h2 = "h2";
		final String h1Value = "hValue1";
		final String h2Value = "hValue2";
		final Vector<String> headersList = new Vector<String>();
		Collections.addAll(headersList, h1, h2);
		final Enumeration<String> headers = headersList.elements();

		final String sa1 = "sa1";
		final String sa2 = "sa2";
		final String sa1Value = "saValue1";
		final String sa2Value = "saValue2";
		final Vector<String> sessionAttributesList = new Vector<String>();
		Collections.addAll(sessionAttributesList, sa1, sa2);
		final Enumeration<String> sessionAttributes = sessionAttributesList.elements();

		Double firstTimerValue = 1000.453d;
		Double secondTimerValue = 1323.675d;

		Long firstCpuTimerValue = 5000L;
		Long secondCpuTimerValue = 6872L;

		HttpTimerData tmp = new HttpTimerData(null, platformId, sensorTypeId, methodId);
		tmp.getHttpInfo().setRequestMethod(method);
		tmp.getHttpInfo().setUri(uri);
		Map<String, String> attributeMap = new HashMap<String, String>();
		MapUtils.putAll(attributeMap, new Object[][] { { att1, att1Value }, { att2, att2Value } });
		tmp.setAttributes(attributeMap);

		tmp.setParameters(parameterMap);

		Map<String, String> headerMap = new HashMap<String, String>();
		MapUtils.putAll(headerMap, new Object[][] { { h1, h1Value }, { h2, h2Value } });
		tmp.setHeaders(headerMap);

		Map<String, String> sessionAtrMap = new HashMap<String, String>();
		MapUtils.putAll(sessionAtrMap, new Object[][] { { sa1, sa1Value }, { sa2, sa2Value } });
		tmp.setSessionAttributes(sessionAtrMap);

		int responseStatus = 404;
		tmp.setHttpResponseStatus(responseStatus);

		MethodSensorData data = tmp;

		when(timer.getCurrentTime()).thenReturn(firstTimerValue).thenReturn(secondTimerValue);
		when(threadMXBean.getCurrentThreadCpuTime()).thenReturn(firstCpuTimerValue).thenReturn(secondCpuTimerValue);
		when(platformManager.getPlatformId()).thenReturn(platformId);

		when(httpServletRequest.getMethod()).thenReturn(method);
		when(httpServletRequest.getRequestURI()).thenReturn(uri);
		when(httpServletRequest.getParameterMap()).thenReturn(parameterMap);
		when(httpServletRequest.getAttributeNames()).thenReturn(attributes);
		when(httpServletRequest.getAttribute(att1)).thenReturn(att1Value);
		when(httpServletRequest.getAttribute(att2)).thenReturn(att2Value);
		when(httpServletRequest.getHeaderNames()).thenReturn(headers);
		when(httpServletRequest.getHeader(h1)).thenReturn(h1Value);
		when(httpServletRequest.getHeader(h2)).thenReturn(h2Value);

		when(httpServletResponse.getStatus()).thenReturn(responseStatus);

		when(session.getAttributeNames()).thenReturn(sessionAttributes);
		when(session.getAttribute(sa1)).thenReturn(sa1Value);
		when(session.getAttribute(sa2)).thenReturn(sa2Value);
		when(httpServletRequest.getSession(false)).thenReturn(session);

		// Object servlet = (Object) new MyTestServlet();
		Object[] parameters = new Object[] { httpServletRequest, httpServletResponse };

		httpHook.beforeBody(methodId, sensorTypeId, servlet, parameters, registeredSensorConfig);

		httpHook.firstAfterBody(methodId, sensorTypeId, servlet, parameters, result, false, registeredSensorConfig);

		httpHook.secondAfterBody(coreService, methodId, sensorTypeId, servlet, parameters, result, false, registeredSensorConfig);

		verify(coreService).addMethodSensorData(eq(sensorTypeId), eq(methodId), eq(String.valueOf(firstTimerValue)), argThat(new HttpTimerDataVerifier((HttpTimerData) data)));
		verifyZeroInteractions(result);
	}

	@Test
	public void oneRecordThatIsNotHttp() {
		Double firstTimerValue = 1000.453d;
		Double secondTimerValue = 1323.675d;

		Long firstCpuTimerValue = 5000L;
		Long secondCpuTimerValue = 6872L;

		when(timer.getCurrentTime()).thenReturn(firstTimerValue).thenReturn(secondTimerValue);

		when(threadMXBean.getCurrentThreadCpuTime()).thenReturn(firstCpuTimerValue).thenReturn(secondCpuTimerValue);
		when(platformManager.getPlatformId()).thenReturn(platformId);

		Object[] parameters = new Object[] { servletRequest, servletResponse };

		httpHook.beforeBody(methodId, sensorTypeId, servlet, parameters, registeredSensorConfig);

		httpHook.firstAfterBody(methodId, sensorTypeId, servlet, parameters, result, false, registeredSensorConfig);

		httpHook.secondAfterBody(coreService, methodId, sensorTypeId, servlet, parameters, result, false, registeredSensorConfig);

		// Data must not be pushed!
		verifyNoMoreInteractions(coreService);
		verifyZeroInteractions(result);
	}

	@Test
	public void twoInvocationsAfterEachOther() {
		// Idea: Is it also working for two invocations after each other. Is the marker reset
		// correctly?

		// First invocation:
		// a) has no http
		// b) has http

		// Seconf invocation:
		// a) has http
		// b) has no http

		// initialize the ids
		long platformId = 1L;
		long sensorTypeId = 3L;

		long methodId11 = 1L;
		long methodId12 = 2L;
		long methodId21 = 3L;
		long methodId22 = 4L;

		Double timerS11 = 1000d;
		Double timerS12 = 1500d;
		Double timerE12 = 2000d;
		Double timerE11 = 2500d;

		Double timerS21 = 2000d;
		Double timerS22 = 2500d;
		Double timerE22 = 3000d;
		Double timerE21 = 3500d;

		Long cpuS11 = 11000L;
		Long cpuS12 = 21500L;
		Long cpuE12 = 34500L;
		Long cpuE11 = 45000L;

		Long cpuS21 = 52000L;
		Long cpuS22 = 62500L;
		Long cpuE22 = 73500L;
		Long cpuE21 = 84000L;

		// The second one should have the results!
		MethodSensorData data1 = new HttpTimerData(null, platformId, sensorTypeId, methodId12);
		MethodSensorData data2 = new HttpTimerData(null, platformId, sensorTypeId, methodId21);

		when(timer.getCurrentTime()).thenReturn(timerS11).thenReturn(timerS12).thenReturn(timerE12).thenReturn(timerE11).thenReturn(timerS21).thenReturn(timerS22).thenReturn(timerE22)
		.thenReturn(timerE21);
		when(threadMXBean.getCurrentThreadCpuTime()).thenReturn(cpuS11).thenReturn(cpuS12).thenReturn(cpuE12).thenReturn(cpuE11).thenReturn(cpuS21).thenReturn(cpuS22).thenReturn(cpuE22)
		.thenReturn(cpuE21);
		when(platformManager.getPlatformId()).thenReturn(platformId);

		Object[] parametersNoHttp = new Object[] { servletRequest, servletResponse };
		Object[] parametersHttp = new Object[] { httpServletRequest, httpServletResponse };

		httpHook.beforeBody(methodId11, sensorTypeId, servlet, parametersNoHttp, registeredSensorConfig);
		httpHook.beforeBody(methodId12, sensorTypeId, servlet, parametersHttp, registeredSensorConfig);

		httpHook.firstAfterBody(methodId12, sensorTypeId, servlet, parametersHttp, result, false, registeredSensorConfig);
		httpHook.secondAfterBody(coreService, methodId12, sensorTypeId, servlet, parametersHttp, result, false, registeredSensorConfig);

		httpHook.firstAfterBody(methodId11, sensorTypeId, servlet, parametersNoHttp, result, false, registeredSensorConfig);
		httpHook.secondAfterBody(coreService, methodId11, sensorTypeId, servlet, parametersNoHttp, result, false, registeredSensorConfig);

		verify(coreService).addMethodSensorData(eq(sensorTypeId), eq(methodId12), eq(String.valueOf(timerS11)), argThat(new HttpTimerDataVerifier((HttpTimerData) data1)));

		httpHook.beforeBody(methodId21, sensorTypeId, servlet, parametersHttp, registeredSensorConfig);
		httpHook.beforeBody(methodId22, sensorTypeId, servlet, parametersNoHttp, registeredSensorConfig);

		httpHook.firstAfterBody(methodId22, sensorTypeId, servlet, parametersNoHttp, result, false, registeredSensorConfig);
		httpHook.secondAfterBody(coreService, methodId22, sensorTypeId, servlet, parametersNoHttp, result, false, registeredSensorConfig);

		httpHook.firstAfterBody(methodId21, sensorTypeId, servlet, parametersHttp, result, false, registeredSensorConfig);
		httpHook.secondAfterBody(coreService, methodId21, sensorTypeId, servlet, parametersHttp, result, false, registeredSensorConfig);

		verify(coreService).addMethodSensorData(eq(sensorTypeId), eq(methodId21), eq(String.valueOf(timerE12)), argThat(new HttpTimerDataVerifier((HttpTimerData) data2)));

		// ensure that there are no exceptions (like "NoSuchElement" which means that before or
		// after did not push a timer object)

		// No other data must not be pushed!
		verifyNoMoreInteractions(coreService);

		verifyZeroInteractions(result);

	}

	@Test
	public void multipleRecordsWithHttp() {
		// Idea:
		// 1.element -> not http -> no data
		// 2.element -> http -> measurement
		// 3.element -> not http -> no data
		// 4.element -> http -> no data (already have a measurement)

		// initialize the ids
		long platformId = 1L;
		long sensorTypeId = 3L;

		long methodId1 = 1L;
		long methodId2 = 2L;
		long methodId3 = 3L;
		long methodId4 = 4L;

		Double timerS1 = 1000d;
		Double timerS2 = 1500d;
		Double timerS3 = 2000d;
		Double timerS4 = 2500d;
		Double timerE4 = 3500d;
		Double timerE3 = 4000d;
		Double timerE2 = 4500d;
		Double timerE1 = 5000d;

		Long cpuS1 = 11000L;
		Long cpuS2 = 21500L;
		Long cpuS3 = 32000L;
		Long cpuS4 = 42500L;
		Long cpuE4 = 53500L;
		Long cpuE3 = 64000L;
		Long cpuE2 = 74500L;
		Long cpuE1 = 85000L;

		// The second one should have the results!
		MethodSensorData data = new HttpTimerData(null, platformId, sensorTypeId, methodId2);

		when(timer.getCurrentTime()).thenReturn(timerS1).thenReturn(timerS2).thenReturn(timerS3).thenReturn(timerS4).thenReturn(timerE4).thenReturn(timerE3).thenReturn(timerE2).thenReturn(timerE1);
		when(threadMXBean.getCurrentThreadCpuTime()).thenReturn(cpuS1).thenReturn(cpuS2).thenReturn(cpuS3).thenReturn(cpuS4).thenReturn(cpuE4).thenReturn(cpuE3).thenReturn(cpuE2).thenReturn(cpuE1);
		when(platformManager.getPlatformId()).thenReturn(platformId);

		Object[] parameters1 = new Object[] { servletRequest, servletResponse };
		Object[] parameters2 = new Object[] { httpServletRequest, httpServletResponse };
		Object[] parameters3 = new Object[] { "Ich bin ein String und keine http information" };
		Object[] parameters4 = new Object[] { httpServletRequest, httpServletResponse };

		httpHook.beforeBody(methodId1, sensorTypeId, servlet, parameters1, registeredSensorConfig);
		httpHook.beforeBody(methodId2, sensorTypeId, servlet, parameters2, registeredSensorConfig);
		httpHook.beforeBody(methodId3, sensorTypeId, servlet, parameters3, registeredSensorConfig);
		httpHook.beforeBody(methodId4, sensorTypeId, servlet, parameters4, registeredSensorConfig);

		httpHook.firstAfterBody(methodId4, sensorTypeId, servlet, parameters4, result, false, registeredSensorConfig);
		httpHook.secondAfterBody(coreService, methodId4, sensorTypeId, servlet, parameters4, result, false, registeredSensorConfig);

		httpHook.firstAfterBody(methodId3, sensorTypeId, servlet, parameters3, result, false, registeredSensorConfig);
		httpHook.secondAfterBody(coreService, methodId3, sensorTypeId, servlet, parameters3, result, false, registeredSensorConfig);

		httpHook.firstAfterBody(methodId2, sensorTypeId, servlet, parameters2, result, false, registeredSensorConfig);
		httpHook.secondAfterBody(coreService, methodId2, sensorTypeId, servlet, parameters2, result, false, registeredSensorConfig);

		httpHook.firstAfterBody(methodId1, sensorTypeId, servlet, parameters1, result, false, registeredSensorConfig);
		httpHook.secondAfterBody(coreService, methodId1, sensorTypeId, servlet, parameters1, result, false, registeredSensorConfig);

		verify(coreService).addMethodSensorData(eq(sensorTypeId), eq(methodId2), eq(String.valueOf(timerS1)), argThat(new HttpTimerDataVerifier((HttpTimerData) data)));

		// No other data must not be pushed!
		verifyNoMoreInteractions(coreService);

		verifyZeroInteractions(result);
	}

	/**
	 * Inner class used to verify the contents of PlainTimerData objects.
	 */
	private static class HttpTimerDataVerifier extends ArgumentMatcher<HttpTimerData> {
		private final HttpTimerData data;

		public HttpTimerDataVerifier(HttpTimerData data) {
			this.data = data;
		}

		@Override
		public boolean matches(Object object) {
			if (!HttpTimerData.class.isInstance(object)) {
				return false;
			}
			HttpTimerData other = (HttpTimerData) object;

			assertThat(data.getHttpInfo().getUri(), is(equalTo(other.getHttpInfo().getUri())));
			assertThat(data.getHttpInfo().getRequestMethod(), is(equalTo(other.getHttpInfo().getRequestMethod())));
			assertThat(data.getAttributes(), is(equalTo(other.getAttributes())));
			assertThat(data.getHeaders(), is(equalTo(other.getHeaders())));
			assertThat(data.getSessionAttributes(), is(equalTo(other.getSessionAttributes())));
			assertThat(data.getParameters(), is(equalTo(other.getParameters())));
			assertThat(data.isCharting(), is(equalTo(other.isCharting())));
			assertThat(data.getHttpResponseStatus(), is(equalTo(other.getHttpResponseStatus())));

			return true;
		}
	}
}
