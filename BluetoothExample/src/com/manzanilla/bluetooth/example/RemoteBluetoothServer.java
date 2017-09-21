package com.manzanilla.bluetooth.example;

import com.sun.deploy.util.Waiter;

public class RemoteBluetoothServer {
    public static void main(String[] args) {
        Thread waitThread = new Thread(new WaitThread()); //supposed to be a waitThread
        waitThread.start();
    }
}
