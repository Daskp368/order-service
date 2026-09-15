package com.example.orderservice.dto.common;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Страница результатов с метаданными пагинации")
public class PagedResponse<T> {

	@Schema(description = "Элементы текущей страницы")
	private final List<T> content;
	@Schema(description = "Номер текущей страницы, начиная с нуля", example = "0", minimum = "0")
	private final int page;
	@Schema(description = "Запрошенный максимальный размер страницы", example = "20",
			minimum = "1", maximum = "100")
	private final int size;
	@Schema(description = "Общее количество подходящих элементов", example = "1", minimum = "0")
	private final long totalElements;
	@Schema(description = "Общее количество страниц", example = "1", minimum = "0")
	private final int totalPages;

	public PagedResponse(List<T> content, int page, int size, long totalElements, int totalPages) {
		this.content = List.copyOf(content);
		this.page = page;
		this.size = size;
		this.totalElements = totalElements;
		this.totalPages = totalPages;
	}

	public List<T> getContent() {
		return content;
	}

	public int getPage() {
		return page;
	}

	public int getSize() {
		return size;
	}

	public long getTotalElements() {
		return totalElements;
	}

	public int getTotalPages() {
		return totalPages;
	}
}
