package com.bookeatinglion.order.delivery.dto;

import com.bookeatinglion.order.delivery.domain.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDeliveryStatusRequest(@NotNull DeliveryStatus status) {}
