package com.cloudbees.jenkins.plugins.xpdev;

import hudson.model.Cause;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class WebHookCause extends Cause {

    @Override
    public String getShortDescription() {
        return "xp-dev WebHook";
    }
}
