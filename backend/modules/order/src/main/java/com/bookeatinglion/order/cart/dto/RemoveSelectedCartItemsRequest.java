package com.bookeatinglion.order.cart.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RemoveSelectedCartItemsRequest(@NotEmpty List<Long> cartItemIds) {}
