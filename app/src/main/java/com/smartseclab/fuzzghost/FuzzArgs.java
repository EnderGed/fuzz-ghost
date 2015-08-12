package com.smartseclab.fuzzghost;

/**
 * Created by smartseclab on 8/3/15.
 */
public class FuzzArgs {
    public String className;
    public String methodName;
    public Class[] args;
    public Object[] argVals;
    public int trials;
    public boolean feierabend;

    public FuzzArgs(String className, String methodName, Class[] args, int trials){
        this.className = className;
        this.methodName = methodName;
        this.args = args;
        this.trials = trials;
        this.argVals = null;
        feierabend = false;
    }

    public FuzzArgs(String className, String methodName, Class[] args, Object[] argVals){
        this.className = className;
        this.methodName = methodName;
        this.args = args;
        this.argVals = argVals;
        feierabend = false;
    }

    public FuzzArgs(){
        feierabend = true;
    }
}
