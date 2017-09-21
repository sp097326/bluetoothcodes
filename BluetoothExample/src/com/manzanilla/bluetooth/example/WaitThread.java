/**
 * Code retrieved from the website:
 * http://luugiathuy.com/2011/02/android-java-bluetooth/
 *
 */

package com.manzanilla.bluetooth.example;

import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

public class WaitThread implements Runnable {

    public WaitThread() {
    }

    @Override
    public void run() {
        waitForConnection();
    }

    /** Waiting for connection from devices **/
    private void waitForConnection() {
        LocalDevice local = null;

        StreamConnectionNotifier notifier;
        StreamConnection connection = null;

        //setup server and listen for connection
        try {
            local = LocalDevice.getLocalDevice();
            local.setDiscoverable(DiscoveryAgent.GIAC);

            UUID uuid = new UUID(80087355);
            String url = "btspp://localhost:" + uuid.toString() + ";name=RemoteBluetooth";
            notifier = (StreamConnectionNotifier) Connector.open(url);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        while (true) {
            try {
                System.out.println("waiting for connection....");
                connection = notifier.acceptAndOpen();
                Thread processThread = new Thread(new ProcessConnectionThread(connection));
                processThread.start();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }
}
