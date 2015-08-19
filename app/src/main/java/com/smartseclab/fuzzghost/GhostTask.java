package com.smartseclab.fuzzghost;

import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * The task (actually a thread) responsible for the TCP communication.
 */
public class GhostTask implements Runnable {

    private int port;
    private String address, tag, invalid, exceptionInterrupted, classNotInitiated, envSet, unknownClassName;
    private String className = "";
    private MainActivity caller;
    private Socket cSocket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private ArrayBlockingQueue<FuzzArgs> argsQueue;

    public GhostTask(int port, String address, MainActivity caller, ArrayBlockingQueue<FuzzArgs> queue) {
        this.port = port;
        this.caller = caller;
        this.argsQueue = queue;
        this.address = address;
        exceptionInterrupted = caller.getString(R.string.exceptionInterrupted);
        classNotInitiated = caller.getString(R.string.classNotInitiated);
        invalid = caller.getString(R.string.invalid);
        envSet = caller.getString(R.string.envSet);
        unknownClassName = caller.getString(R.string.unknownClassName);
        tag = MainActivity.getApplicationTag() + "Task";
    }

    /**
     * Run the Client.
     */
    public void run() {
        try {
            cSocket = new Socket(address, port);
            Log.d(tag, "Client started at port " + port + ".");
            clientLoop();
        } catch (Exception e) {
            Log.e(tag, e.getMessage(), e);
        }
        closeClient();
    }

    /**
     * Communicate with the Vessel. Receive and send messages.
     */
    private void clientLoop() {
        try {
            startCommunication();
            while (true) {
                Object receivedObject = ois.readObject();
                if (receivedObject instanceof String[]) {
                    String[] receivedString = (String[]) (receivedObject);
                    Log.d(tag, "Received a String array: " + receivedString[0].toLowerCase());
                    if (receivedString[0].toLowerCase().equals("goodbye")) {
                        Log.d(tag, "Execution completed gracefully (server goodbye).");
                        break;
                    } else if (receivedString[0].toLowerCase().equals("query")) {
                        if (className.length() != 0) {
                            String name = receivedString[1].toLowerCase();
                            if (name.equals("method")) {
                                if (receivedString.length > 2)
                                    queryMethod(receivedString[2]);
                                else
                                    sendErrorMessage(invalid);
                            } else if (name.equals("class")) {
                                if (receivedString.length > 1) {
                                    String[] methodArgs = TestExecutor.getClassMethods(caller, className);
                                    sendStringArrayMessage(methodArgs);
                                } else
                                    sendErrorMessage(invalid);
                            } else
                                sendErrorMessage(invalid);
                        } else
                            sendErrorMessage(classNotInitiated);
                    } else if (receivedString[0].toLowerCase().equals("initiate")) {
                        if (receivedString.length > 1)
                            initiateClass(receivedString[1]);
                        else
                            sendErrorMessage(invalid);
                    } else
                        sendErrorMessage(invalid);
                } else {
                    if (className.length() != 0) {
                        Log.d(tag, "Received an object array.");
                        addMethodToQueue((Object[]) receivedObject);
                    } else
                        sendErrorMessage(classNotInitiated);
                }
            }
        } catch (Exception e) {
            Log.e(tag, e.getMessage(), e);
            Log.d(tag, exceptionInterrupted);
        }
        closeClient();
        Log.d(tag, "The vessel has disconnected.");
    }

    /**
     * Try to connect to the Vessel. Receive and try to understand "Hello" message.
     * Initiate tested class (if any given) and send adequate response.
     * @throws IOException
     * @throws Exception
     */
    private void startCommunication() throws IOException, Exception {
        try {
            ois = new ObjectInputStream(cSocket.getInputStream());
            oos = new ObjectOutputStream(cSocket.getOutputStream());
            String[] hello = (String[]) (ois.readObject());
            if (hello.length < 1 || !(hello[0].toLowerCase().equals("hello")))
                throw new Exception();
            if (hello.length > 1) {
                if (!initiateClass(hello[1]))
                    Log.d(tag, classNotInitiated);
            } else {
                Log.d(tag, classNotInitiated);
                sendStringMessage(classNotInitiated);
            }
        } catch (IOException ioe) {
            Log.e(tag, ioe.getMessage(), ioe);
            closeClient();
            throw ioe;
        } catch (Exception e) {
            Log.d(tag, "Invalid hello message (WTF). Exiting.");
            sendErrorMessage("Invalid hello message. WTF?");
            closeClient();
            throw e;
        }
    }

    /**
     * Query a method to get its argument types.
     * @param methodName
     * @throws IOException
     */
    private void queryMethod(String methodName) throws IOException {
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
        }
    }

    /**
     * Upon receiving a method perform request, add its information to the Blocking Queue.
     * The "false" index is a special mark used as a border between method argument types
     * and values.
     * @param message
     * @throws IOException
     */
    private void addMethodToQueue(Object[] message) throws IOException {
        String methodName = (String) message[0];
        int falseIndex = 1;
        while (falseIndex < message.length && !(message[falseIndex] instanceof Boolean))
            ++falseIndex;
        if (falseIndex >= message.length)
            sendErrorMessage("Invalid message format.");
        else {
            Class[] argTypes = new Class[falseIndex - 1];
            for (int i = 1; i < falseIndex; ++i)
                argTypes[i - 1] = (Class) message[i];
            Object[] args = Arrays.copyOfRange(message, falseIndex + 1, message.length);
            if (argTypes.length != args.length)
                sendErrorMessage("The number of arg types and args is not the same.");
            else
                try {
                    argsQueue.put(new FuzzArgs(className, methodName, argTypes, args));
                } catch (InterruptedException ie) {
                    sendErrorMessage("Could not handle method execution this time: method queue interrupted.");
                }
        }
    }

    /**
     * Set a class which the tests will be performed upon.
     * @param name
     * @return
     * @throws IOException
     */
    private boolean initiateClass(String name) throws IOException {
        if (TestExecutor.knowsClass(name)) {
            className = name;
            Log.d(tag, envSet + className + ".");
            sendStringMessage(envSet + className + ".");
            return true;
        } else
            sendErrorMessage(unknownClassName);
        return false;
    }


    /**
     * Send a String message informing of an error during execution.
     * @param message
     * @throws IOException
     */
    public void sendErrorMessage(String message) throws IOException {
        String[] arr = {"ERROR: ", message};
        sendStringArrayMessage(arr);
    }

    /**
     * Send a String message.
     * @param message
     * @throws IOException
     */
    public void sendStringMessage(String message) throws IOException {
        String[] arr = {message};
        sendStringArrayMessage(arr);
    }

    /**
     * Send a String Array message.
     * @param message
     * @throws IOException
     */
    private void sendStringArrayMessage(String[] message) throws IOException {
        oos.writeObject(message);
        oos.flush();
    }

    /**
     * Close the Client connection. Ignore all exceptions. Insert the "feierband" info into
     * the Blocking Queue.
     */
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