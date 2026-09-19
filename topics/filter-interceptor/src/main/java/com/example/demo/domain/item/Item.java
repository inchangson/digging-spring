package com.example.demo.domain.item;

import lombok.Data;

/**
 * 서비스 상품 객체
 */
@Data
public class Item {
    private Long id;
    private String itemName;
    private Integer price;
    private Integer quantity;
}
