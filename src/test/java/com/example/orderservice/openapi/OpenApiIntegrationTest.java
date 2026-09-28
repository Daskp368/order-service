package com.example.orderservice.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.orderservice.config.OpenApiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OpenApiIntegrationTest {

	private static final Map<String, List<String>> EXPECTED_OPERATIONS = Map.of(
			"/api/auth/register", List.of("post"),
			"/api/auth/login", List.of("post"),
			"/api/auth/me", List.of("get"),
			"/api/users", List.of("get"),
			"/api/users/{id}", List.of("delete"),
			"/api/orders", List.of("get", "post"),
			"/api/orders/all", List.of("get"),
			"/api/orders/{id}", List.of("put", "delete"));

	private static final List<String> PROTECTED_OPERATIONS = List.of(
			"/api/auth/me#get",
			"/api/users#get",
			"/api/users/{id}#delete",
			"/api/orders#get",
			"/api/orders#post",
			"/api/orders/all#get",
			"/api/orders/{id}#put",
			"/api/orders/{id}#delete");

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;

	@Autowired
	OpenApiIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
	}

	@Test
	void openApiDocumentIsPublicAndContainsMetadataAndBearerSecurityScheme() throws Exception {
		JsonNode document = getOpenApiDocument();

		assertThat(document.path("openapi").asText()).startsWith("3.");
		assertThat(document.path("info").path("title").asText()).isEqualTo("Order Service API");
		assertThat(document.path("info").path("version").asText()).isEqualTo("1.0");

		JsonNode securityScheme = document.path("components")
				.path("securitySchemes")
				.path(OpenApiConfig.SECURITY_SCHEME_NAME);
		assertThat(securityScheme.path("type").asText()).isEqualTo("http");
		assertThat(securityScheme.path("scheme").asText()).isEqualTo("bearer");
		assertThat(securityScheme.path("bearerFormat").asText()).isEqualTo("JWT");
	}

	@Test
	void openApiDocumentContainsEveryContractOperationWithCorrectSecurity() throws Exception {
		JsonNode paths = getOpenApiDocument().path("paths");

		assertThat(paths.size()).isEqualTo(EXPECTED_OPERATIONS.size());
		EXPECTED_OPERATIONS.forEach((path, methods) -> {
			assertThat(paths.has(path)).as("path %s", path).isTrue();
			methods.forEach(method -> assertThat(paths.path(path).has(method))
					.as("operation %s %s", method.toUpperCase(), path)
					.isTrue());
		});

		assertThat(paths.path("/api/auth/register").path("post").has("security")).isFalse();
		assertThat(paths.path("/api/auth/login").path("post").has("security")).isFalse();
		PROTECTED_OPERATIONS.forEach(operation -> assertBearerSecurity(paths, operation));
	}

	@Test
	void openApiDocumentDescribesDtoConstraintsPaginationAndErrors() throws Exception {
		JsonNode document = getOpenApiDocument();
		JsonNode schemas = document.path("components").path("schemas");

		JsonNode username = schemas.path("RegisterRequest").path("properties").path("username");
		assertThat(username.path("minLength").asInt()).isEqualTo(3);
		assertThat(username.path("maxLength").asInt()).isEqualTo(50);
		assertThat(username.path("pattern").asText()).isEqualTo("[a-zA-Z0-9_]+");

		JsonNode description = schemas.path("CreateOrderRequest").path("properties").path("description");
		assertThat(description.path("minLength").asInt()).isEqualTo(1);
		assertThat(description.path("maxLength").asInt()).isEqualTo(1000);

		JsonNode pagedResponse = findSchemaStartingWith(schemas, "PagedResponse");
		assertThat(pagedResponse.path("properties").fieldNames())
				.toIterable()
				.containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");

		JsonNode sizeParameter = findParameter(
				document.path("paths").path("/api/orders").path("get").path("parameters"), "size");
		assertThat(sizeParameter.path("schema").path("type").asText()).isEqualTo("integer");
		assertThat(sizeParameter.path("schema").path("format").asText()).isEqualTo("int32");
		assertThat(sizeParameter.path("schema").path("default").asInt()).isEqualTo(20);
		assertThat(sizeParameter.path("schema").path("minimum").asInt()).isEqualTo(1);
		assertThat(sizeParameter.path("schema").path("maximum").asInt()).isEqualTo(100);

		JsonNode badRequestSchema = document.path("paths")
				.path("/api/orders")
				.path("get")
				.path("responses")
				.path("400")
				.path("content")
				.path(MediaType.APPLICATION_JSON_VALUE)
				.path("schema");
		assertThat(badRequestSchema.path("$ref").asText()).endsWith("/ApiErrorResponse");
		assertThat(schemas.path("ApiErrorResponse").path("properties").has("fieldErrors")).isTrue();
	}

	@Test
	void swaggerUiIsPublicWhileBusinessEndpointsRemainProtected() throws Exception {
		MvcResult redirect = mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andReturn();

		assertThat(redirect.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/swagger-ui/index.html");
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
		mockMvc.perform(get("/v3/api-docs.yaml"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized());
	}

	private JsonNode getOpenApiDocument() throws Exception {
		MvcResult result = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private void assertBearerSecurity(JsonNode paths, String operation) {
		String[] parts = operation.split("#", 2);
		JsonNode security = paths.path(parts[0]).path(parts[1]).path("security");

		assertThat(security.isArray()).as("security for %s", operation).isTrue();
		assertThat(security).as("security for %s", operation)
				.anySatisfy(requirement -> assertThat(requirement.has(OpenApiConfig.SECURITY_SCHEME_NAME)).isTrue());
	}

	private JsonNode findSchemaStartingWith(JsonNode schemas, String prefix) {
		Iterator<Map.Entry<String, JsonNode>> fields = schemas.properties().iterator();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> field = fields.next();
			if (field.getKey().startsWith(prefix)) {
				return field.getValue();
			}
		}
		throw new AssertionError("Schema starting with " + prefix + " not found");
	}

	private JsonNode findParameter(JsonNode parameters, String name) {
		for (JsonNode parameter : parameters) {
			if (name.equals(parameter.path("name").asText())) {
				return parameter;
			}
		}
		throw new AssertionError("Parameter " + name + " not found");
	}
}
