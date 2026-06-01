package com.mira.build;

public record TaskConfig(String name, String cmd, String script, String description) {

    public TaskConfig {
        if (cmd == null && script == null) {
            throw new BuildException("Task '" + name + "': either 'cmd' or 'script' must be specified");
        }
        if (cmd != null && script != null) {
            throw new BuildException("Task '" + name + "': 'cmd' and 'script' are mutually exclusive");
        }
    }

    public boolean isCmd() {
        return cmd != null;
    }
}
