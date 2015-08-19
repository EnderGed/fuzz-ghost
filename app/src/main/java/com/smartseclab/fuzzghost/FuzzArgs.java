package com.smartseclab.fuzzghost;

/**
 * The arguments sent to the blocking queue used to call the Executor.It contains class name,
 * method name, method arg types, method arg values and a boolean value indicating whether
 * the work is over (set true at the end of Client connection to enable the Ghost to finish its
 * execution properly). Everything is public for convenience reasons.
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
    public FuzzArgs(String className, String methodName, Class[] args, Object[] argVals) {
        this.className = className;
        this.methodName = methodName;
        this.args = args;
        this.argVals = argVals;
        feierabend = false;
    }

    /**
     * The empty constructor set feierband to true: it indicates that the Ghost's job is finished
     * and it may safely terminate.
     */
    public FuzzArgs() {
        feierabend = true;
    }
}
