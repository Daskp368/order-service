package com.example.orderservice.advice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.orderservice.exception.ResourceNotFoundException;

class ApiExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
				.standaloneSetup(new ErrorTestController())
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void returnsNotFoundErrorBody() throws Exception {
		mockMvc.perform(get("/test/errors/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.timestamp").isString())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("Учебный ресурс не найден"))
				.andExpect(jsonPath("$.path").value("/test/errors/not-found"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void hidesInternalExceptionDetails() throws Exception {
		mockMvc.perform(get("/test/errors/internal"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.timestamp").isString())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.error").value("Internal Server Error"))
				.andExpect(jsonPath("$.message").value("Внутренняя ошибка сервера"))
				.andExpect(jsonPath("$.path").value("/test/errors/internal"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("internal-sensitive-details"))));
	}

	@Test
	void preservesStatusOfKnownSpringMvcError() throws Exception {
		mockMvc.perform(post("/test/errors/not-found"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.status").value(405))
				.andExpect(jsonPath("$.error").value("Method Not Allowed"))
				.andExpect(jsonPath("$.path").value("/test/errors/not-found"));
	}

	@RestController
	@RequestMapping("/test/errors")
	private static class ErrorTestController {

		@GetMapping("/not-found")
		void notFound() {
			throw new ResourceNotFoundException("Учебный ресурс не найден");
		}

		@GetMapping("/internal")
		void internal() {
			throw new IllegalStateException("internal-sensitive-details");
		}
	}
}
