package com.example.sevice;

import com.example.web.vo.CartItemVo;

import java.util.List;

public interface CartService {

    void addItem(String userId, CartItemVo item);

    List<CartItemVo> listItems(String userId);

    void updateNum(String userId, String fid, Integer num);

    void removeItem(String userId, String fid);

    void clearCart(String userId);
}