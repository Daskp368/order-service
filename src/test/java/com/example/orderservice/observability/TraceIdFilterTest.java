package com.example.orderservice.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;

class TraceIdFilterTest {

	private final TraceIdFilter filter = new TraceIdFilter();

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void shouldUseIncomingTraceId() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
		request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "training-request-001");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> traceIdInsideChain = new AtomicReference<>();

		filter.doFilter(request, response,
				(servletRequest, servletResponse) -> traceIdInsideChain.set(MDC.get("traceId")));

		assertThat(traceIdInsideChain.get()).isEqualTo("training-request-001");
		assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("training-request-001");
		assertThat(MDC.get("traceId")).isNull();
	}

	@Test
	void shouldGenerateTraceIdWhenHeaderIsMissing() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> traceIdInsideChain = new AtomicReference<>();

		filter.doFilter(request, response,
				(servletRequest, servletResponse) -> traceIdInsideChain.set(MDC.get("traceId")));

		assertThat(traceIdInsideChain.get()).isNotBlank().matches("[a-f0-9-]{36}");
		assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(traceIdInsideChain.get());
		assertThat(MDC.get("traceId")).isNull();
	}

	@Test
	void shouldReplaceInvalidTraceId() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
		request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "invalid trace id");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> {
		});

		assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER))
				.isNotEqualTo("invalid trace id")
				.matches("[a-f0-9-]{36}");
	}

	@Test
	void shouldClearMdcWhenRequestFails() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
		MockHttpServletResponse response = new MockHttpServletResponse();

		assertThatThrownBy(() -> filter.doFilter(request, response,
				(servletRequest, servletResponse) -> {
					throw new ServletException("Test failure");
				}))
				.isInstanceOf(ServletException.class);

		assertThat(MDC.get("traceId")).isNull();
	}
}
