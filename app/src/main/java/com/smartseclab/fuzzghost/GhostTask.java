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
    private String address, TAG;
    private String className = "";
    private MainActivity caller;
    private Socket cSocket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private ArrayBlockingQueue<FuzzArgs> argsQueue;
    private static final String exceptionInterrupted = "Execution interrupted by an exception.";
    private static final String classNotInitiated = "Tested class has not been set yet.";
    private static final String invalid = "Invalid command.";
    private static final String envSet = "Test environment initiated for class.";
    private static final String unknownClassName = "Unknown class name.";

    public GhostTask(int port, String address, ArrayBlockingQueue<FuzzArgs> queue) {
        this.port = port;
        this.argsQueue = queue;
        this.address = address;

        TAG = MainActivity.getApplicationTag() + "Task";
    }

    /**
     * Run the Client.
     */
    public void run() {
        try {
            cSocket = new Socket(address, port);
            Log.d(TAG, "Client started at port " + port + ".");
            clientLoop();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        closeClient();
    }

    /**
     * Communicate with the Vessel. Receive and send messages.
     */
    private void clientLoop() {
        try {
            initializeCommunication();
            while (true) {
                Object receivedObject = ois.readObject();
                if (receivedObject instanceof String[]) {
                    String[] receivedString = (String[]) (receivedObject);
                    Log.d(TAG, "Received a String array: " + receivedString[0].toLowerCase());
                    if (receivedString[0].toLowerCase().equals("goodbye")) {
                        Log.d(TAG, "Execution completed gracefully (server goodbye).");
                        MainActivity.stopLoop();
                        break;
                    } else if (receivedString[0].toLowerCase().equals("query")) {
                        if (className.length() != 0) {
                            String name = receivedString[1].toLowerCase();
                            switch (name) {
                                case "method":
                                    if (receivedString.length > 2) {
                                        queryMethod(receivedString[2]);
                                    } else {
                                        sendErrorMessage(invalid);
                                    }
                                    break;
                                case "class":
                                    if (receivedString.length > 1) {
                                        String[] methodArgs = TestExecutor.getClassMethods(caller, className);
                                        sendStringArrayMessage(methodArgs);
                                    } else {
                                        sendErrorMessage(invalid);
                                    }
                                    break;
                                default:
                                    sendErrorMessage(invalid);
                                    break;
                            }
                        } else {
                            sendErrorMessage(classNotInitiated);
                        }
                    } else if (receivedString[0].toLowerCase().equals("initiate")) {
                        if (receivedString.length > 1) {
                            initiateClass(receivedString[1]);
                        } else {
                            sendErrorMessage(invalid);
                        }
                    } else {
                        sendErrorMessage(invalid);
                    }
                } else {
                    if (className.length() != 0) {
                        Log.d(TAG, "Received an object array.");
                        addMethodToQueue((Object[]) receivedObject);
                    } else {
                        sendErrorMessage(classNotInitiated);
                    }
                }
            }
        } catch(Exception e){
            e.printStackTrace();

        }
//        catch (Exception e) {
//            Log.e(TAG, e.getMessage(), e);
//            Log.d(TAG, exceptionInterrupted);
//        }
        closeClient();
        Log.d(TAG, "The vessel has disconnected.");
    }

    /**
     * Try to connect to the Vessel. Receive and try to understand "Hello" message.
     * Initiate tested class (if any given) and send adequate response.
     */
    private void initializeCommunication() throws IOException{
        try {
            ois = new ObjectInputStream(cSocket.getInputStream());
            oos = new ObjectOutputStream(cSocket.getOutputStream());
            String[] hello = (String[]) (ois.readObject());
            if (hello.length < 1 || !(hello[0].toLowerCase().equals("hello"))) {
                throw new IOException("Invalid handshake with server.");
            }
            if (hello.length > 1) {
                if (!initiateClass(hello[1]))
                    Log.d(TAG, classNotInitiated);
            } else {
                Log.d(TAG, classNotInitiated);
                sendStringMessage(classNotInitiated);
            }
        }catch(ClassNotFoundException e){
            e.printStackTrace();
            throw new IOException("Invalid handshake object.");
        }
    }

    /**
     * Query a method to get its argument types.
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
     * @throws IOException
     */
    private void addMethodToQueue(Object[] message) throws IOException {
        String methodName = (String) message[0];
        int falseIndex = 1;
        while (falseIndex < message.length && !(message[falseIndex] instanceof Boolean)) {
            ++falseIndex;
        }
        if (falseIndex >= message.length) {
            sendErrorMessage("Invalid message format.");
        }
        else {
            Class[] argTypes = new Class[falseIndex - 1];
            for (int i = 1; i < falseIndex; ++i)
                argTypes[i - 1] = (Class) message[i];
            Object[] args = Arrays.copyOfRange(message, falseIndex + 1, message.length);
            if (argTypes.length != args.length)
                sendErrorMessage("The number of arg types and args is not the same.");
            else {
                try {
                    argsQueue.put(new FuzzArgs(className, methodName, argTypes, args));
                } catch (InterruptedException ie) {
                    sendErrorMessage("Could not handle method execution this time: method queue interrupted.");
                }
            }
        }
    }

    /**
     * Set a class which the tests will be performed upon.
     * @throws IOException
     */
    private boolean initiateClass(String name) throws IOException {
        if (TestExecutor.knowsClass(name)) {
            className = name;
            Log.d(TAG, envSet + className + ".");
            sendStringMessage(envSet + className + ".");
            return true;
        } else {
            sendErrorMessage(unknownClassName);
        }
        return false;
    }


    /**
     * Send a String message informing of an error during execution.
     * @throws IOException
     */
    public void sendErrorMessage(String message) throws IOException {
        String[] arr = {"ERROR: ", message};
        sendStringArrayMessage(arr);
    }

    /**
     * Send a String message.
     * @throws IOException
     */
    public void sendStringMessage(String message) throws IOException {
        String[] arr = {message};
        sendStringArrayMessage(arr);
    }

    /**
     * Send a String Array message.
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
            Log.e(TAG, "Error while closing ObjectOutputStream");
            e1.printStackTrace();
        }
        try {
            ois.close();
        } catch (Exception e2) {
            Log.e(TAG, "Error while closing ObjectInputStream");
            e2.printStackTrace();
        }
        try {
            cSocket.close();
        } catch (Exception e3) {
            Log.e(TAG, "Error while closing Socket");
            e3.printStackTrace();
        }
        try {
            argsQueue.put(new FuzzArgs());
        } catch (Exception e4) {
            Log.e(TAG, "Error while closing ObjectOutputStream");
            e4.printStackTrace();
        }
    }

}