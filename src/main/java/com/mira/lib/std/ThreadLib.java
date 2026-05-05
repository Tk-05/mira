package com.mira.lib.std;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.values.MutexValue;

public class ThreadLib implements Lib {

    @Override
    public void loadLib(Environment environment) {
        environment.define("newMutex",
                new NativeFunction(0, args -> new MutexValue()));
    }
}
