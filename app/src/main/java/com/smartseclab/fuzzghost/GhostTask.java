package com.smartseclab.fuzzghost;

import android.util.Log;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by smartseclab on 7/30/15.
 */
public class GhostTask implements Runnable {

    private int port;
    private String address;
    private String tag = MainActivity.getApplicationTag() + "Task";
    private MainActivity caller;
    private ServerSocket sSocket;
    private Socket cSocket;
    private TestExecutor executor;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private ArrayBlockingQueue<FuzzArgs> argsQueue;


    public GhostTask(int port, String address, MainActivity caller, ArrayBlockingQueue<FuzzArgs> queue) {
        this.port = port;
        this.caller = caller;
        this.argsQueue = queue;
        this.address = address;
    }

    public void run() {
        try {
            cSocket = new Socket(address, port);
            Log.d(tag, "Client started at port " + port + ".");
            handleSingleClient();
        } catch (Exception e) {
            Log.e(tag, "Crash Test OMG", e);
        }
        closeClient();
    }

    public void handleSingleClient() {
        try {
            ois = new ObjectInputStream(cSocket.getInputStream());
            oos = new ObjectOutputStream(cSocket.getOutputStream());
            String[] received = (String[]) (ois.readObject());
            if (received.length != 2 || !(received[0].toLowerCase().equals("hello"))) {
                Log.d(tag, "Invalid hello message (WTF Error). Exiting.");
                closeClient();
            }
            String className = received[1];
            Log.d(tag, "Tests are being prepared for class " + received[1]);
            while (true) {
                received = (String[]) (ois.readObject());
                Log.d(tag, "New message received: " + received[0].toLowerCase());
                if (received[0].toLowerCase().equals("goodbye")) {
                    Log.d(tag, "Execution completed gracefully (server goodbye).");
                    break;
                }
                if (received[0].toLowerCase().equals("do")) {
                    String methodName = received[2];
                    try {
                        int trials = Integer.parseInt(received[1]);
                        Class[] args = new Class[received.length - 3];
                        StringBuilder errorClasses = new StringBuilder("The following classes were not found:");
                        boolean wasClassError = false;
                        for (int i = 0; i < args.length; ++i)
                            try {
                                args[i] = TestExecutor.getClassByName(received[i + 3]);
                            } catch (ClassNotFoundException cnfe) {
                                Log.e(tag, "OMG", cnfe);
                                wasClassError = true;
                                errorClasses.append(" ");
                                errorClasses.append(received[i + 3]);
                            }
                        if (wasClassError) {
                            errorClasses.append(".");
                            Log.d(caller.appTag, "Error: classes not found: " + errorClasses.toString());
                            sendErrorMessage(errorClasses.toString());
                        } else {
                            argsQueue.put(new FuzzArgs(className, methodName, args, trials));
                        }
                    } catch (NumberFormatException nfe) {
                        Log.e(tag, "OMG", nfe);
                        sendErrorMessage("Failed to parse number of trials (must be integer).");
                    } catch (UnsupportedClassException uce) {
                        Log.e(tag, "OMG", uce);
                        sendErrorMessage("Invalid class name (currently supported are WifiService and AudioService).");
                    } catch (Exception e) {
                        Log.e(tag, "OMG", e);
                        Log.d(tag, "Execution interrupted by an exception.");
                        break;
                    }
                } else if (received[0].toLowerCase().equals("query")) {
                    String methodName = received[1];
                    try {
                        String[] methodArgs = TestExecutor.getMethodArgs(caller, className, methodName);
                        StringBuilder sb = new StringBuilder(methodName);
                        sb.append("\n");
                        for (int i = 0; i < methodArgs.length; ++i) {
                            sb.append("Invocation ");
                            sb.append(i + 1);
                            sb.append(": ");
                            sb.append(methodArgs[i]);
                            sb.append("\n");
                        }
                        sendStringMessage(sb.toString());
                    } catch (NoSuchMethodException nsme) {
                        sendErrorMessage("No method " + methodName + " for class " + className + ".");
                    } catch (Exception e) {
                        Log.e(tag, "OMG", e);
                        Log.d(tag, "Execution interrupted by an exception.");
                        break;
                    }
                } else if (received[0].toLowerCase().equals("query-class")) {
                    try {
                        String[] methodArgs = TestExecutor.getClassMethods(caller, className);
                        sendStringArrayMessage(methodArgs);
                    } catch (Exception e) {
                        Log.e(tag, "OMG", e);
                        Log.d(tag, "Execution interrupted by an exception.");
                        break;
                    }
                } else {
                    sendErrorMessage("Invalid command.");
                }
            }
        } catch (Exception e) {
            Log.e(tag, "Crash Test OMG", e);
        }
        closeClient();
        Log.d(tag, "The server has disconnected.");
    }

    public void sendErrorMessage(String message) throws Exception {
        try {
            String[] error = {"ERROR: ", message};
            oos.writeObject(error);
            oos.flush();
        } catch (Exception e) {
            throw e;
        }
    }

    public void sendFailsMessage(int fails) throws Exception {
        try {
            String[] message = {"Execution completed. Fails: ", fails + ""};
            oos.writeObject(message);
            oos.flush();
        } catch (Exception e) {
            throw e;
        }
    }

    public void sendStringMessage(String message) throws Exception {
        String[] arr = {message};
        try {
            oos.writeObject(arr);
            oos.flush();
        } catch (Exception e) {
            throw e;
        }
    }

    public void sendStringArrayMessage(String[] message) throws Exception {
        try {
            oos.writeObject(message);
            oos.flush();
        } catch (Exception e) {
            throw e;
        }
    }

    private void closeClient() {
        try {
            oos.close();
        } catch (Exception e1) {
        }
        try {
            ois.close();
        } catch (Exception e2) {
        }
        try {
            cSocket.close();
        } catch (Exception e3) {
        }
        try{
            argsQueue.put(new FuzzArgs());
        }catch(Exception e4){}
    }

}