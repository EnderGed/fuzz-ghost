package com.smartseclab.fuzzghost;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;


public class MainActivity extends Activity {

    private int port;
    private GhostTask gt;
    private String appTag = getApplicationTag();
    private String address;
    private ArrayBlockingQueue<FuzzArgs> queue;

    /**
     * Return a string with application tag (for logging purposes).
     *
     * @return
     */
    public static String getApplicationTag() {
        return "FuzzGhost";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String defaultAddress = "10.0.2.2";
        int defaultPort = 7557;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent intent = getIntent();
        if (intent.hasExtra("address"))
            address = intent.getStringExtra("address");
        else {
            Log.d(appTag, "No address extra found, using default (" + defaultAddress + ").");
            address = defaultAddress;
        }
        if (intent.hasExtra("port"))
            port = intent.getIntExtra("port", defaultPort);
        else {
            Log.d(appTag, "No port extra found, using default (" + defaultPort + ").");
            port = defaultPort;
        }
        doConnect();
        talk();
    }

    /**
     * Create a blocking queue, run the client thread.
     */
    private void doConnect() {
        queue = new ArrayBlockingQueue<FuzzArgs>(10);
        gt = new GhostTask(port, address, this, queue);
        new Thread(gt).start();
    }

    /**
     * "Talk" to the executor: keep receiving methods info from the Blocking Queue and trying to execute them.
     */
    private void talk() {
        TestExecutor executor;
        FuzzArgs fuzzArgs = null;
        while (true) {
            try {
                Log.d(appTag, "Waiting for queue input.");
                fuzzArgs = queue.take();
                if (fuzzArgs.feierabend)
                    break;
                Log.d(appTag, "Queue released an element; performing test.");
                performTest(fuzzArgs);
                Log.d(appTag, "Finished.");
            } catch (NoSuchMethodException nsme) {
                Log.e(appTag, nsme.getMessage(), nsme);
                try {
                    gt.sendErrorMessage("No method " + fuzzArgs.methodName + " for specified args.");
                } catch (Exception e) {
                    Log.e(appTag, e.getMessage(), e);
                }
            } catch (Exception e) {
                Log.e(appTag, e.getMessage(), e);
                try {
                    gt.sendErrorMessage(e.getClass().getName());
                } catch (Exception e1) {
                    Log.e(appTag, e.getMessage(), e);
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
        Log.d(appTag, resultMessage);
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

}