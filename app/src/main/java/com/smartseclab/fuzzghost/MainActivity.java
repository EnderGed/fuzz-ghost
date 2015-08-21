package com.smartseclab.fuzzghost;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;


public class MainActivity extends Activity {

    private static int port = 7557;
    private static String address = "10.0.2.2";
    private static GhostTask gt;
    private static String TAG = "FuzzGhost";
    private static final int QueueMAXSIZE = 10;
    private static boolean runLoop = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent intent = getIntent();
        getIPAndPort(intent);
        Log.i(TAG, "Port in use: " + port);
        executeTests();
    }

    private void executeTests() {
    /*
    //TODO change it into normal queue, and settle stop condition
     */
        ArrayBlockingQueue<FuzzArgs> queue = new ArrayBlockingQueue<FuzzArgs>(QueueMAXSIZE);
        doConnect(queue);
        runLoop = true;
        talk(queue);
    }

    /**
     * Set IP address and port for connection if given in intent
     */
    private void getIPAndPort(Intent intent) {
        if (intent.hasExtra("address"))
            address = intent.getStringExtra("address");
        else {
            Log.d(TAG, "No address extra found.");
        }
        Log.i(TAG, "IP in use: " + address);
        if (intent.hasExtra("port"))
            port = intent.getIntExtra("port", 7557);
        else {
            Log.d(TAG, "No port extra found.");
        }
        Log.i(TAG, "Port in use: " + port);
    }

    /**
     * Create a blocking queue, run the client thread.
     */
    private void doConnect(ArrayBlockingQueue<FuzzArgs> queue) {
        gt = new GhostTask(port, address, queue, this);
        new Thread(gt).start();
    }

    /**
     * "Talk" to the executor: keep receiving methods info from the Blocking Queue and trying to execute them.
     */
    private void talk(ArrayBlockingQueue<FuzzArgs> queue) {
        FuzzArgs fuzzArgs = null;
        while (runLoop) {
            try {
                Log.d(TAG, "Waiting for queue input.");
                fuzzArgs = queue.take();
                if (fuzzArgs.feierabend)
                    break;
                Log.d(TAG, "Queue released an element; performing test.");
                performTest(fuzzArgs);
                Log.d(TAG, "Finished.");
            } catch (NoSuchMethodException nsme) {
                Log.e(TAG, nsme.getMessage(), nsme);
                try {
                    gt.sendErrorMessage("No method " + fuzzArgs.methodName + " for specified args.");
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                try {
                    gt.sendErrorMessage(e.getClass().getName());
                } catch (Exception e1) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Perform a single test with the given method info. Send its results to the Vessel.
     *
     * @param fuzzArgs
     * @throws Exception
     */
    private void performTest(FuzzArgs fuzzArgs) throws Exception {
        Object testResult = new TestExecutor(this, fuzzArgs.className).runMethod(fuzzArgs.methodName, fuzzArgs.args, fuzzArgs.argVals);
        String resultMessage = "Test completed with result: " + testResult.toString();
        Log.d(TAG, resultMessage);
        gt.sendStringMessage(resultMessage);
    }

    @Override
    protected void onDestroy() {
        try {
            gt.closeClient();
        } catch (Exception e) {
        }
        super.onDestroy();
    }


    public static String getApplicationTag() {
        return TAG;
    }

    public static void stopLoop(){
        runLoop = false;
    }
}