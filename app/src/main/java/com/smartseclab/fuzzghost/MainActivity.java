package com.smartseclab.fuzzghost;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.smartseclab.fuzzghost.R;

import java.util.concurrent.ArrayBlockingQueue;


public class MainActivity extends Activity {

    private int port;
    private GhostTask gt;
    public String appTag = getApplicationTag();
    public String address;
    public ArrayBlockingQueue<FuzzArgs> queue;

    public static String getApplicationTag(){
        return "FuzzGhost";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String defaultAddr = "10.0.2.2";
        int defaultPort = 7557;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent intent = getIntent();
        if (intent.hasExtra("address"))
            address = intent.getStringExtra("address");
        else {
            Log.d(appTag, "No addr extra found, using default (" + defaultAddr + ").");
            address = defaultAddr;
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

    public void doConnect() {
        queue = new ArrayBlockingQueue<FuzzArgs>(10);
        gt = new GhostTask(port, address, this, queue);
        new Thread(gt).start();
    }

    public void talk(){
        TestExecutor executor;
        FuzzArgs fuzzArgs = null;
        while (true) {
            try {
                Log.d(appTag, "Waiting for queue input.");
                fuzzArgs = queue.take();
                if(fuzzArgs.feierabend)
                    break;
                Log.d(appTag, "Queue released an element; performing tests.");
                int fails = 0;
                //executor = new TestExecutor(this, fuzzArgs.className);
                for (int j = 0; j < fuzzArgs.trials; ++j) {
                    executor = new TestExecutor(this, fuzzArgs.className);
                    if (!(executor.fuzzMethod(fuzzArgs.methodName, fuzzArgs.args)))
                        ++fails;
                }
                Log.d(appTag, "Tests completed with " + fails + " / " + fuzzArgs.trials + " errors.");
                gt.sendFailsMessage(fails);
                Log.d(appTag, "Finished.");
            }catch (NoSuchMethodException nsme) {
                    Log.e(appTag, "OMG", nsme);
                try{
                    gt.sendErrorMessage("No method " + fuzzArgs.methodName + " for specified args.");
                }catch(Exception e){
                    Log.e(appTag, "OMG", e);
                }
            } catch (Exception e) {
                Log.e(appTag, "OMG", e);
                try{
                    gt.sendErrorMessage(e.getClass().getName());
                }catch(Exception e1){
                    Log.e(appTag, "OMG", e);
                }
            }
        }
    }

    @Override
    protected  void onDestroy(){
        try {
            gt.closeClient();
        }catch(Exception e){}
        super.onDestroy();
    }

}