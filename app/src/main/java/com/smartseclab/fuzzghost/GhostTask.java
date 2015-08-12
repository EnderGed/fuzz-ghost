package com.smartseclab.fuzzghost;

import android.util.Log;

import junit.framework.Test;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by smartseclab on 7/30/15.
 */
public class GhostTask implements Runnable {

    private int port;
    private String address, tag, invalid, exceptionInterrupted, classNotInitiated, envSet, unknownClassName;
    private MainActivity caller;
    private Socket cSocket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private ArrayBlockingQueue<FuzzArgs> argsQueue;
    private String className = "";


    public GhostTask(int port, String address, MainActivity caller, ArrayBlockingQueue<FuzzArgs> queue) {
        this.port = port;
        this.caller = caller;
        this.argsQueue = queue;
        this.address = address;
        exceptionInterrupted = caller.getString(R.string.exceptionInterrupted);
        classNotInitiated =  caller.getString(R.string.classNotInitiated);
        invalid = caller.getString(R.string.invalid);
        envSet =  caller.getString(R.string.envSet);
        unknownClassName =  caller.getString(R.string.unknownClassName);
        tag = MainActivity.getApplicationTag() + "Task";
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
            if (received.length<1 || !(received[0].toLowerCase().equals("hello"))) {
                Log.d(tag, "Invalid hello message (WTF Error). Exiting.");
                sendErrorMessage("Invalid hello message. WTF?");
                closeClient();
                return;
            }
            if(received.length>1) {
                if(!initiateClass(received[1]))
                    Log.d(tag, classNotInitiated);
            }
            else{
                Log.d(tag, classNotInitiated);
                sendStringMessage(classNotInitiated);
            }
            while (true) {
                Object receivedO = ois.readObject();
                if (receivedO instanceof String[]) {
                    received = (String[]) (receivedO);
                    Log.d(tag, "New message received: " + received[0].toLowerCase());
                    if (received[0].toLowerCase().equals("goodbye")) {
                        Log.d(tag, "Execution completed gracefully (server goodbye).");
                        break;
                    }
                    if (received[0].toLowerCase().equals("do")) {
                        if (!className.equals("")) {
                            if (received.length > 2) {
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
                                    Log.d(tag, exceptionInterrupted);
                                    break;
                                }
                            } else {
                                sendErrorMessage(invalid);
                            }
                        } else {
                            sendErrorMessage(classNotInitiated);
                        }
                    } else if (received[0].toLowerCase().equals("query")) {
                        if (!className.equals("")) {
                            if (received.length > 2) {
                                String name = received[1].toLowerCase();
                                if (name.equals("method")) {
                                    name = received[2];
                                    try {
                                        String[] methodArgs = TestExecutor.getMethodArgs(caller, className, name);
                                        StringBuilder sb = new StringBuilder(name);
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
                                        sendErrorMessage("No method " + name + " for class " + className + ".");
                                    } catch (Exception e) {
                                        Log.e(tag, "OMG", e);
                                        Log.d(tag, exceptionInterrupted);
                                        break;
                                    }
                                } else if (name.equals("class")) {
                                    try {
                                        String[] methodArgs = TestExecutor.getClassMethods(caller, className);
                                        sendStringArrayMessage(methodArgs);
                                    } catch (Exception e) {
                                        Log.e(tag, "OMG", e);
                                        Log.d(tag, exceptionInterrupted);
                                        break;
                                    }
                                } else {
                                    sendErrorMessage(invalid);
                                }
                            } else {
                                sendErrorMessage(invalid);
                            }
                        } else {
                            sendErrorMessage(classNotInitiated);
                        }
                    } else if (received[0].toLowerCase().equals("initiate")) {
                        if (received.length > 1) {
                            initiateClass(received[1]);
                        } else {
                            sendErrorMessage(invalid);
                        }
                    } else {
                        sendErrorMessage(invalid);
                    }
                }
                else{
                    if (!className.equals("")) {
                        Log.d(tag, "Received an object array.");
                        Object[] rec = (Object[]) receivedO;
                        String methodName = (String)rec[0];
                        int falseIndex = 1;
                        while(falseIndex<rec.length && !(rec[falseIndex] instanceof Boolean))
                            ++falseIndex;
                        if(falseIndex>=rec.length)
                            sendErrorMessage("WTF are these arguments?");
                        else {
                            Object[] argT = Arrays.copyOfRange(rec, 1, falseIndex);
                            Class[] argTypes = new Class[argT.length];
                            for(int i=0; i<argT.length; ++i)
                                argTypes[i] = (Class)argT[i];
                            Object[] args = Arrays.copyOfRange(rec, falseIndex+1, rec.length);
                            argsQueue.put(new FuzzArgs(className, methodName, argTypes, args));
                        }
                    }else{
                        sendErrorMessage(classNotInitiated);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(tag, "Crash Test OMG", e);
        }
        closeClient();
        Log.d(tag, "The vessel has disconnected.");
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

    private boolean initiateClass(String name) throws Exception{
        try {
            if (TestExecutor.knowsClass(name)) {
                className = name;
                Log.d(tag, envSet + className + ".");
                sendStringMessage(envSet + className + ".");
                return true;
            }
            else
                sendErrorMessage(unknownClassName);
            return false;
        }catch(Exception e){
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

    public void closeClient() {
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
        try {
            argsQueue.put(new FuzzArgs());
        } catch (Exception e4) {
        }
    }

}