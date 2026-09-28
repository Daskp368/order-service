package com.example.orderservice.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String TRACE_ID_HEADER = "X-Trace-Id";

	private static final String TRACE_ID_MDC_KEY = "traceId";
	private static final Pattern VALID_TRACE_ID_PATTERN = Pattern.compile("[a-zA-Z0-9._-]{1,64}");

	private static final Logger LOGGER = LoggerFactory.getLogger(TraceIdFilter.class);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String traceId = resolveTraceId(request);
		String method = request.getMethod();
		String path = request.getRequestURI();
		long startedAt = System.nanoTime();

		response.setHeader(TRACE_ID_HEADER, traceId);

		try (MDC.MDCCloseable ignored = MDC.putCloseable(TRACE_ID_MDC_KEY, traceId)) {

			LOGGER.info("Получен HTTP-запрос: method={}, path={}", method, path);
			try {
				filterChain.doFilter(request, response);

				long durationMs = calculateDurationMs(startedAt);

				LOGGER.info("HTTP-запрос завершён: method={}, path={}, status={}, durationMs={}",
						method, path, response.getStatus(), durationMs);
			}
			catch (IOException | ServletException | RuntimeException exception) {
				long durationMs = calculateDurationMs(startedAt);

				LOGGER.error("Необработанная ошибка HTTP-запроса: method={}, path={}, durationMs={}",
						method, path, durationMs, exception);

				throw exception;
			}
		}
	}

	private String resolveTraceId(HttpServletRequest request) {
		String incomingTraceId = request.getHeader(TRACE_ID_HEADER);

		if (incomingTraceId != null && VALID_TRACE_ID_PATTERN.matcher(incomingTraceId).matches()) {
			return incomingTraceId;
		}
		return UUID.randomUUID().toString();
	}

	private long calculateDurationMs(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}
