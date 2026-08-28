package com.ecommerce.project.service.order;

import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.OrderItem;
import com.ecommerce.project.payload.AddressDTO;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Turns order entities into their DTOs / response envelopes. Pulled out of
 * {@code OrderServiceImpl} so that class no longer carries {@link ModelMapper}
 * and the two-phase pagination hydration lives behind one seam.
 */
@Component
@RequiredArgsConstructor
public class OrderDtoAssembler {

    private final OrderRepository orderRepository;
    private final ModelMapper modelMapper;

    /**
     * Phase 2 of two-phase pagination: hydrates one page of order IDs into full
     * order graphs, preserving the ordering established by the ID query.
     */
    public OrderResponse buildOrderResponse(Page<Long> idPage) {
        List<Long> ids = idPage.getContent();

        List<OrderDTO> orderDTOS;
        if (ids.isEmpty()) {
            orderDTOS = List.of();
        } else {
            Map<Long, Order> byId = orderRepository.findByIdInWithDetails(ids).stream()
                    .collect(Collectors.toMap(Order::getId, order -> order));
            orderDTOS = ids.stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .map(order -> modelMapper.map(order, OrderDTO.class))
                    .toList();
        }

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setContent(orderDTOS);
        orderResponse.setPageNumber(idPage.getNumber());
        orderResponse.setPageSize(idPage.getSize());
        orderResponse.setTotalElements(idPage.getTotalElements());
        orderResponse.setTotalPages(idPage.getTotalPages());
        orderResponse.setLastPage(idPage.isLast());
        return orderResponse;
    }

    /** DTO for an order that was just placed (customer or guest checkout). */
    public OrderDTO forPlacedOrder(Order order, List<OrderItem> orderItems, Long addressId, BigDecimal totalAmount) {
        OrderDTO orderDTO = modelMapper.map(order, OrderDTO.class);
        orderDTO.setTotalAmount(totalAmount);
        orderDTO.setDiscountAmount(order.getDiscountAmount());
        orderDTO.setShippingCost(order.getShippingCost());
        orderDTO.setAddressId(addressId);
        orderDTO.setAppliedCoupons(order.getAppliedCoupons() != null
                ? List.of(order.getAppliedCoupons().split(","))
                : List.of());
        orderDTO.setItems(orderItems.stream()
                .map(item -> modelMapper.map(item, OrderItemDTO.class))
                .toList());
        return orderDTO;
    }

    /** DTO for a single persisted order, with its items and address id. */
    public OrderDTO forDetailView(Order order) {
        OrderDTO orderDTO = modelMapper.map(order, OrderDTO.class);
        orderDTO.setAddressId(order.getAddress().getAddressId());
        orderDTO.setItems(order.getOrderItems().stream()
                .map(item -> modelMapper.map(item, OrderItemDTO.class))
                .toList());
        return orderDTO;
    }

    /** Plain DTO for a status-update response. */
    public OrderDTO forStatusUpdate(Order order) {
        return modelMapper.map(order, OrderDTO.class);
    }

    public Address toAddress(AddressDTO addressDTO) {
        return modelMapper.map(addressDTO, Address.class);
    }
}
