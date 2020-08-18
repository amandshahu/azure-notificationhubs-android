package com.microsoft.windowsazure.messaging.notificationhubs;

import android.content.Intent;

import com.amazon.device.messaging.ADMMessageHandlerBase;

public class ADMReceiver extends ADMMessageHandlerBase {

    private final NotificationHub mHub;

    protected ADMReceiver() {
        this(ADMReceiver.class.toString(), NotificationHub.getInstance());
    }

    protected ADMReceiver(String className, NotificationHub hub) {
        super(className);
        mHub = hub;
    }

    @Override
    protected void onMessage(Intent intent) {
    }

    @Override
    protected void onRegistrationError(String s) {

    }

    @Override
    protected void onRegistered(String s) {

    }

    @Override
    protected void onUnregistered(String s) {

    }
}
