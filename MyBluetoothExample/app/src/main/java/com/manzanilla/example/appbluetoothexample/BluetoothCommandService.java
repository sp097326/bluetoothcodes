/**
 *  code taken from com.luugiathuy.apps.remotebluetooth
 *  as seen on http://luugiathuy.com/2011/02/android-java-bluetooth/
 *  date: 24/07/2017
 *  adapted for deprecation an differences on API by
 *  @author: A.A.M.H. - @sp097326
 */

package com.manzanilla.example.appbluetoothexample;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;
import java.io.InputStream;
import java.io.OutputStream;


public class BluetoothCommandService {

    private static final String TAG = "BluetoothCommandService";
    private static final boolean D = true;

    //Unique UUID for this app?
    private static final UUID MY_UUID = UUID.fromString("e0cbf06c-cd8b-4647-bb8a-263b43f0f974"); //check your bt radio
    //from my radio code: e0cbf06c-cd8b-4647-bb8a-263b43f0f974
    //from original code: 04c6093b-0000-1000-8000-00805f9b34fb

    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    //constants
    public static final int STATE_NONE = 0;         //we're doing nothing
    public static final int STATE_LISTEN = 1;       //listening for incomming connections
    public static final int STATE_CONNECTING = 2;   //initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;    //connected to remote device;

    public static final int EXIT_CMD = -1;
    public static final int VOL_UP = 1;
    public static final int VOL_DOWN = 2;
    public static final int MOUSE_MOVE = 3;

    /**
     * Constructor. Prepares a new Bluetooth session
     * @param context   The UI Activity Context
     * @param handler   A Handler to send messages back to the UI Activity
     */
    public BluetoothCommandService(Context context, Handler handler) {
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mState = this.STATE_NONE;
        this.mHandler = handler;
    }

    private synchronized void setState(int state) {
        if (D) Log.d(TAG,"setState()" + mState + " -> " + state);
        this.mState = state;

        //give the new state to the hanlder so the UI can update
        mHandler.obtainMessage(RemoteBluetooth.MESSAGE_STATE_CHANGE,state,-1).sendToTarget();
    }

    public synchronized int getState() { return mState;}

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     * */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        //cancel if
        //thread attempting to make a connection
        if (mConnectThread != null) { mConnectThread.cancel(); mConnectThread = null; }
        //thread currently running a connection
        if(mConnectedThread != null) { mConnectedThread.cancel(); mConnectedThread = null; }

        setState(STATE_LISTEN);
    }

    /**
     * Start the ConnectThread to inititate a connection to a remote device
     * @param device The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to:" + device);

        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }
        if (mConnectedThread != null) { mConnectedThread.cancel(); mConnectedThread = null; }

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(this.STATE_CONNECTING);
    }

    /**
     * Start the CnnectedThread to begin managing a Bluetooth connection
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetootDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected");

        if (mConnectThread != null) { mConnectThread.cancel(); mConnectThread = null; }
        if (mConnectedThread != null) { mConnectedThread.cancel(); mConnectedThread = null; }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        Message msg = mHandler.obtainMessage(RemoteBluetooth.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(RemoteBluetooth.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     *  Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "Stop");
        if (mConnectThread != null) { mConnectThread.cancel(); mConnectThread = null; }
        if (mConnectedThread != null) { mConnectedThread.cancel(); mConnectedThread = null; }

        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in a unsynchronized manner
     * @param out
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        ConnectedThread r;
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        r.write(out);
    }

    /**
     * write to the connectedthread
     * @param out
     * @see ConnectedThread#write(int)
     */
    public void write(int out) {
        ConnectedThread r;
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        r.write(out);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionFailed() {
        setState(STATE_LISTEN);
        Message msg = mHandler.obtainMessage(RemoteBluetooth.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(RemoteBluetooth.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private void connectionLost() {
        setState(STATE_LISTEN);
        Message msg = mHandler.obtainMessage(RemoteBluetooth.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(RemoteBluetooth.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }


    /// private classes section
    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            this.mmDevice = device;
            BluetoothSocket tmpSocket = null;

            try {
                tmpSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException ioe) {
                Log.e(TAG, "create() failed", ioe);
            }
            mmSocket = tmpSocket;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            mAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException ioe1) {
                connectionFailed();
                try {
                    mmSocket.close();
                } catch (IOException ioe) {
                    Log.e(TAG, "unable to close() socket during connection failure", ioe);
                }
                BluetoothCommandService.this.start();
                return;
            }
            synchronized (BluetoothCommandService.this) {
                mConnectThread = null;
            }
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException ioe) {
                Log.e(TAG, "close() of connection socket failed",ioe);
            }
        }

    }


    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            this.mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try{
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException ioe) {
                Log.e(TAG, "temp sockets not created", ioe);
            }

            this.mmInStream = tmpIn;
            this.mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];

            //keep listening while connected
            while (true) {
                try {
                   int bytes = mmInStream.read(buffer);
                   mHandler.obtainMessage(RemoteBluetooth.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException ioe) {
                    Log.e(TAG, "disconnected", ioe);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected Outstream
         * @param buffer
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException ioe) {
                Log.e(TAG, "Excpetion during write", ioe);
            }
        }

        public void write(int out) {
            try {
                mmOutStream.write(out);
            } catch (IOException ioe) {
                Log.e(TAG, "Exception during write",ioe);
            }
        }

        public void cancel() {
            try {
                mmOutStream.write(EXIT_CMD);
                mmSocket.close();
            } catch (IOException ioe) {
                Log.e(TAG, "close() of connection failed", ioe);
            }
        }
    }

}
