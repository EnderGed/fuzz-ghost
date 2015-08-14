package com.smartseclab.fuzzghost;

/**
 * The arguments sent to the blocking queue used to call the Executor's function.
 */
public class FuzzArgs {
    public String className;
    public String methodName;
    public Class[] args;
    public Object[] argVals;
    public boolean feierabend;

    /**
     * A regular constructor.
     */
    public FuzzArgs(String className, String methodName, Class[] args, Object[] argVals){
        this.className = className;
        this.methodName = methodName;
        this.args = args;
        this.argVals = argVals;
        feierabend = false;
    }

    /**
     * The empty constructor set feierband to true: it indicates that the Ghost's job is finished and it may safely terminate.
     */
    public FuzzArgs(){
        feierabend = true;
    }
}
