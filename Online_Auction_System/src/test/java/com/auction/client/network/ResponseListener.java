package com.auction.client.network;

import com.auction.common.dto.Response;

public interface ResponseListener {

    void onResponse(Response response);

    default void onDisconnected() {
    }
}